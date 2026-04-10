/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.alibaba.himarket.service.mcp;

import cn.hutool.core.util.StrUtil;
import com.alibaba.himarket.core.exception.BusinessException;
import com.alibaba.himarket.core.exception.ErrorCode;
import com.alibaba.himarket.core.utils.K8sClientUtils;
import com.alibaba.himarket.entity.SandboxInstance;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.base.CustomResourceDefinitionContext;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.representer.Representer;

/**
 * AGENT_RUNTIME 类型沙箱部署策略。
 *
 * <p>从 classpath 加载 CRD YAML 模板（resources/crd-templates/），
 * 替换占位符后下发到沙箱集群。用户可自定义模板，只需保留占位符即可。
 *
 * <p>模板占位符：
 * RESOURCE_NAME, NAMESPACE, CLUSTER_ID, SHOW_NAME, PROTOCOL,
 * MCP_SERVERS_JSON, ACCESSES_YAML, ENV_YAML（仅 stdio 模板）
 */
@Component
@Slf4j
public class AgentRuntimeDeployStrategy implements McpSandboxDeployStrategy {

    private static final CustomResourceDefinitionContext CRD_CONTEXT =
            new CustomResourceDefinitionContext.Builder()
                    .withGroup("agentruntime.alibabacloud.com")
                    .withVersion("v1alpha1")
                    .withPlural("toolservers")
                    .withScope("Namespaced")
                    .build();

    private static final CustomResourceDefinitionContext ENDPOINT_CONTEXT =
            new CustomResourceDefinitionContext.Builder()
                    .withGroup("agentruntime.alibabacloud.com")
                    .withVersion("v1alpha1")
                    .withPlural("endpoints")
                    .withScope("Namespaced")
                    .build();

    /** 轮询 Endpoint 的最大等待时间和间隔（毫秒） */
    private static final long POLL_TIMEOUT_MS = 60_000;

    private static final long POLL_INTERVAL_MS = 3_000;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /** 用于非阻塞轮询的调度线程池（守护线程，不阻塞 JVM 关闭） */
    private final ScheduledExecutorService pollScheduler =
            Executors.newScheduledThreadPool(
                    2,
                    r -> {
                        Thread t = new Thread(r, "sandbox-poll");
                        t.setDaemon(true);
                        return t;
                    });

    @jakarta.annotation.PreDestroy
    void shutdown() {
        pollScheduler.shutdownNow();
    }

    @org.springframework.beans.factory.annotation.Value("${sandbox.ssl-verify:true}")
    private boolean sslVerify;

    @Override
    public String supportedSandboxType() {
        return "AGENT_RUNTIME";
    }

    @Override
    public String deploy(
            SandboxInstance sandbox,
            String mcpServerId,
            String mcpName,
            String userId,
            String transportType,
            String metaProtocolType,
            String connectionConfig,
            String apiKey,
            String authType,
            String userParams,
            String extraParamsDef,
            String namespace,
            String resourceSpec) {
        if (StrUtil.isBlank(sandbox.getKubeConfig())) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST, "沙箱实例未配置 KubeConfig: " + sandbox.getSandboxId());
        }

        String ns = StrUtil.blankToDefault(namespace, "default");
        String resourceName = buildResourceName(mcpName, userId);
        String accessName = "himarket-" + userId;
        boolean isStdio = "stdio".equalsIgnoreCase(metaProtocolType);
        boolean isApiKeyAuth =
                "bearer".equalsIgnoreCase(authType) || "apikey".equalsIgnoreCase(authType);
        String secretName = null;

        // 构建 mcpServers JSON，同时从中剥离 env 字段
        String[] mcpResult = buildMcpServersJson(mcpName, connectionConfig);
        String mcpServersJson = mcpResult[0];
        String configEnvJson = mcpResult[1];

        // 非 stdio：根据 extraParams 定义的 position 将用户参数分流到 headers/query/env
        // stdio：所有用户参数都作为 env 处理
        String envParamsJson = userParams; // 默认全部当 env
        if (!isStdio && StrUtil.isNotBlank(extraParamsDef) && StrUtil.isNotBlank(userParams)) {
            try {
                Map<String, String> headerParams = new LinkedHashMap<>();
                Map<String, String> queryParams = new LinkedHashMap<>();
                Map<String, String> envParams = new LinkedHashMap<>();

                // 解析参数定义（含 position）
                List<?> defs = OBJECT_MAPPER.readValue(extraParamsDef, List.class);
                // 解析用户提交的参数值
                @SuppressWarnings("unchecked")
                Map<String, String> userValues = OBJECT_MAPPER.readValue(userParams, Map.class);

                for (Object defObj : defs) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> def = (Map<String, Object>) defObj;
                    String paramName = (String) def.get("name");
                    String position = (String) def.getOrDefault("position", "env");
                    String value = userValues.get(paramName);
                    if (StrUtil.isBlank(value)) continue;

                    switch (position.toLowerCase()) {
                        case "header":
                            headerParams.put(paramName, value);
                            break;
                        case "query":
                            queryParams.put(paramName, value);
                            break;
                        default:
                            envParams.put(paramName, value);
                            break;
                    }
                }

                // 将 header 和 query 参数注入到 mcpServers JSON
                if (!headerParams.isEmpty() || !queryParams.isEmpty()) {
                    mcpServersJson =
                            injectParamsIntoMcpServersJson(
                                    mcpServersJson, headerParams, queryParams);
                }

                // env 参数继续走原来的逻辑
                envParamsJson =
                        envParams.isEmpty() ? null : OBJECT_MAPPER.writeValueAsString(envParams);
            } catch (Exception e) {
                log.warn("按 position 分流参数失败，回退为全部当 env: {}", e.getMessage());
                envParamsJson = userParams;
            }
        }

        // 合并 env：connectionConfig 中的 env + env 类型的用户参数
        String mergedEnvJson = mergeEnvJson(configEnvJson, envParamsJson);
        String envYaml = "";
        if (StrUtil.isNotBlank(mergedEnvJson)) {
            envYaml = buildEnvYaml(mergedEnvJson);
        }

        // 只放模板里实际用到的占位符
        Map<String, String> vars = new LinkedHashMap<>();
        vars.put("RESOURCE_NAME", resourceName);
        vars.put("NAMESPACE", ns);
        vars.put("CLUSTER_ID", extractClusterId(sandbox.getClusterAttribute()));
        vars.put("SHOW_NAME", resourceName);
        vars.put(
                "PROTOCOL",
                "http".equalsIgnoreCase(metaProtocolType) ? "streamableHttp" : metaProtocolType);
        vars.put("MCP_SERVERS_JSON", mcpServersJson);
        // 当 authType 为 "apikey" 且 apiKey 非空时，生成 Secret 名称
        if ("apikey".equalsIgnoreCase(authType) && StrUtil.isNotBlank(apiKey)) {
            secretName = buildSecretName(mcpName);
        }
        vars.put("ACCESSES_YAML", buildAccessesYaml(isApiKeyAuth, accessName, secretName));
        vars.put("ENV_YAML", envYaml);

        // 从 MCP 配置的资源规格读取 CPU/内存等
        Map<String, String> resourceVars = extractResourceVars(resourceSpec);
        vars.putAll(resourceVars);

        // 选择模板
        String templateFile =
                isStdio
                        ? "crd-templates/toolserver-stdio.yaml"
                        : "crd-templates/toolserver-sse.yaml";

        // 加载模板 + 替换占位符
        String renderedYaml = renderTemplate(templateFile, vars);

        // 反序列化为 GenericKubernetesResource（使用 Jackson YAML 避免 SnakeYAML 2.x 兼容问题）
        GenericKubernetesResource crd;
        try {
            crd =
                    new com.fasterxml.jackson.databind.ObjectMapper(
                                    new com.fasterxml.jackson.dataformat.yaml.YAMLFactory())
                            .readValue(renderedYaml, GenericKubernetesResource.class);
        } catch (Exception e) {
            throw new BusinessException(
                    ErrorCode.INTERNAL_ERROR, "CRD YAML 反序列化失败: " + e.getMessage());
        }

        // 追加 labels
        if (crd.getMetadata().getLabels() == null) {
            crd.getMetadata().setLabels(new LinkedHashMap<>());
        }
        crd.getMetadata().getLabels().put("app.kubernetes.io/managed-by", "himarket");
        crd.getMetadata().getLabels().put("himarket.io/mcp-server-id", mcpServerId);
        crd.getMetadata().getLabels().put("himarket.io/user-id", userId);
        if (secretName != null) {
            crd.getMetadata().getLabels().put("himarket.io/ref-secret", secretName);
        }

        // 下发 CRD（先创建 Secret，再创建 CRD，CRD 失败时回滚 Secret）
        KubernetesClient client = K8sClientUtils.getClient(sandbox.getKubeConfig());

        // 当 authType 为 "apikey" 且 apiKey 非空时，先创建 K8s Secret
        if ("apikey".equalsIgnoreCase(authType) && StrUtil.isNotBlank(apiKey)) {
            Secret k8sSecret =
                    new SecretBuilder()
                            .withNewMetadata()
                            .withName(secretName)
                            .withNamespace(ns)
                            .addToLabels("app.kubernetes.io/managed-by", "himarket")
                            .addToLabels("himarket.io/user-id", userId)
                            .addToLabels("himarket.io/mcp-name", mcpName)
                            .addToLabels("himarket.io/mcp-server-id", mcpServerId)
                            .addToLabels("himarket.io/ref-toolserver", resourceName)
                            .endMetadata()
                            .withType("Opaque")
                            .addToStringData("API_KEY", apiKey)
                            .build();
            client.secrets().inNamespace(ns).resource(k8sSecret).createOrReplace();
            log.info("[AgentRuntimeDeploy] K8s Secret 创建成功: namespace={}, name={}", ns, secretName);
        }

        try {
            client.genericKubernetesResources(CRD_CONTEXT)
                    .inNamespace(ns)
                    .resource(crd)
                    .createOrReplace();
        } catch (Exception e) {
            if (secretName != null) {
                try {
                    client.secrets().inNamespace(ns).withName(secretName).delete();
                    log.info("[AgentRuntimeDeploy] CRD 创建失败，已回滚删除 Secret: {}", secretName);
                } catch (Exception rollbackEx) {
                    log.warn("[AgentRuntimeDeploy] 回滚删除 Secret 失败: {}", rollbackEx.getMessage());
                }
            }
            throw e;
        }

        log.info(
                "[AgentRuntimeDeploy] CRD 下发成功: namespace={}, name={}, template={}",
                ns,
                resourceName,
                templateFile);

        // 轮询 Endpoint CRD 获取真实 endpoint URL
        String endpointName = resourceName + "-primary";
        String endpointUrl = pollEndpointUrl(client, ns, endpointName);

        // When SSL verification is disabled, downgrade HTTPS to HTTP
        if (!sslVerify && endpointUrl != null && endpointUrl.startsWith("https://")) {
            endpointUrl = endpointUrl.replaceFirst("https://", "http://");
            log.info("[AgentRuntimeDeploy] SSL verification disabled, using HTTP: {}", endpointUrl);
        }

        log.info("[AgentRuntimeDeploy] Endpoint URL 获取成功: {}", endpointUrl);
        if (secretName != null) {
            return endpointUrl + "|SECRET:" + secretName;
        }
        return endpointUrl;
    }

    @Override
    public void undeploy(SandboxInstance sandbox, String mcpName, String userId, String namespace) {
        undeploy(sandbox, mcpName, userId, namespace, null);
    }

    @Override
    public void undeploy(
            SandboxInstance sandbox,
            String mcpName,
            String userId,
            String namespace,
            String resourceName) {
        undeploy(sandbox, mcpName, userId, namespace, resourceName, null);
    }

    @Override
    public void undeploy(
            SandboxInstance sandbox,
            String mcpName,
            String userId,
            String namespace,
            String resourceName,
            String secretName) {
        if (StrUtil.isBlank(sandbox.getKubeConfig())) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST, "沙箱实例未配置 KubeConfig: " + sandbox.getSandboxId());
        }

        String ns = StrUtil.blankToDefault(namespace, "default");
        KubernetesClient client = K8sClientUtils.getClient(sandbox.getKubeConfig());

        if (StrUtil.isBlank(resourceName)) {
            resourceName = buildResourceName(mcpName, userId);
        }

        // 先删除 K8s Secret（如果有）
        if (StrUtil.isNotBlank(secretName)) {
            try {
                client.secrets().inNamespace(ns).withName(secretName).delete();
                log.info(
                        "[AgentRuntimeDeploy] K8s Secret 删除成功: namespace={}, name={}",
                        ns,
                        secretName);
            } catch (Exception e) {
                log.warn(
                        "[AgentRuntimeDeploy] K8s Secret 删除失败（可能已不存在）: namespace={}, name={},"
                                + " error={}",
                        ns,
                        secretName,
                        e.getMessage());
            }
        }

        String endpointName = resourceName + "-primary";

        // 删除 ToolServer CRD
        try {
            client.genericKubernetesResources(CRD_CONTEXT)
                    .inNamespace(ns)
                    .withName(resourceName)
                    .delete();
            log.info(
                    "[AgentRuntimeDeploy] ToolServer CRD 删除成功: namespace={}, name={}",
                    ns,
                    resourceName);
        } catch (Exception e) {
            log.warn(
                    "[AgentRuntimeDeploy] ToolServer CRD 删除失败（可能已不存在）: namespace={}, name={},"
                            + " error={}",
                    ns,
                    resourceName,
                    e.getMessage());
            return;
        }

        waitEndpointDeleted(client, ns, endpointName);
    }

    /**
     * 轮询等待 Endpoint CRD 被沙箱异步清理。
     * 使用 CompletableFuture + ScheduledExecutorService 避免阻塞 Tomcat 请求线程。
     * 如果超时仍未删除，仅打印警告不抛异常（不阻塞后续重建）。
     */
    private void waitEndpointDeleted(
            KubernetesClient client, String namespace, String endpointName) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        long deadline = System.currentTimeMillis() + POLL_TIMEOUT_MS;

        Runnable pollTask =
                new Runnable() {
                    @Override
                    public void run() {
                        if (System.currentTimeMillis() > deadline) {
                            future.complete(null); // 超时不抛异常
                            return;
                        }
                        try {
                            GenericKubernetesResource endpoint =
                                    client.genericKubernetesResources(ENDPOINT_CONTEXT)
                                            .inNamespace(namespace)
                                            .withName(endpointName)
                                            .get();
                            if (endpoint == null) {
                                log.info(
                                        "[AgentRuntimeDeploy] Endpoint 已清理: namespace={}, name={}",
                                        namespace,
                                        endpointName);
                                future.complete(null);
                                return;
                            }
                        } catch (Exception e) {
                            log.info(
                                    "[AgentRuntimeDeploy] Endpoint 已清理（查询异常）: namespace={},"
                                            + " name={}",
                                    namespace,
                                    endpointName);
                            future.complete(null);
                            return;
                        }
                        log.debug("[AgentRuntimeDeploy] Endpoint 尚未清理，继续等待: {}", endpointName);
                        pollScheduler.schedule(this, POLL_INTERVAL_MS, TimeUnit.MILLISECONDS);
                    }
                };

        pollScheduler.schedule(pollTask, 0, TimeUnit.MILLISECONDS);

        try {
            future.get(POLL_TIMEOUT_MS + 5_000, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            log.warn(
                    "[AgentRuntimeDeploy] 等待 Endpoint 清理超时或异常（{}秒），继续执行: {}",
                    POLL_TIMEOUT_MS / 1000,
                    endpointName);
        }
    }

    // ==================== 私有方法 ====================

    /**
     * 从 classpath 加载模板并替换占位符。
     */
    private String renderTemplate(String templatePath, Map<String, String> variables) {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(templatePath)) {
            if (is == null) {
                throw new BusinessException(
                        ErrorCode.INVALID_REQUEST, "CRD 模板文件不存在: " + templatePath);
            }
            String template = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            for (Map.Entry<String, String> entry : variables.entrySet()) {
                template = template.replace("${" + entry.getKey() + "}", entry.getValue());
            }
            return template;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST, "读取 CRD 模板失败: " + e.getMessage());
        }
    }

    /**
     * 解析 connectionConfig JSON，构建 mcpServers JSON 并剥离 env。
     * env 应通过 CRD spec.env 传递，不应留在 mcpServers JSON 中。
     *
     * @return String[2]: [0]=mcpServersJson, [1]=提取的 env JSON（可能为 null）
     * @throws BusinessException connectionConfig 为空或无法解析时抛出
     */
    private String[] buildMcpServersJson(String mcpName, String connectionConfig) {
        if (StrUtil.isBlank(connectionConfig)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "MCP connectionConfig 为空，无法部署");
        }

        String serverName = StrUtil.blankToDefault(mcpName, "mcp-server");

        try {
            McpConnectionConfig config = McpConnectionConfig.parse(connectionConfig);

            // 格式3: { mcpServerConfig: { rawConfig: {...} } } → 递归解析 rawConfig
            if (config.isWrappedFormat()) {
                return buildMcpServersJson(mcpName, config.getRawConfigJson());
            }

            // 格式1 或 格式2: 提取 env，构建不含 env 的 mcpServers JSON
            if (config.isMcpServersFormat() || config.isSingleServerFormat()) {
                Map<String, String> extractedEnv = config.extractAllEnv();
                String mcpJson = config.toMcpServersJsonWithoutEnv(serverName);
                String envJson =
                        extractedEnv.isEmpty()
                                ? null
                                : OBJECT_MAPPER.writeValueAsString(extractedEnv);
                return new String[] {mcpJson, envJson};
            }

            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST, "无法识别 connectionConfig 格式，请检查 MCP 配置");
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST, "解析 connectionConfig 失败: " + e.getMessage());
        }
    }

    /**
     * 将 header 和 query 参数注入到 mcpServers JSON 中。
     * header 参数 → 每个 server 的 "headers" 字段
     * query 参数 → 追加到每个 server 的 "url" 的 query string
     */
    @SuppressWarnings("unchecked")
    private String injectParamsIntoMcpServersJson(
            String mcpServersJson,
            Map<String, String> headerParams,
            Map<String, String> queryParams) {
        try {
            Map<String, Object> root = OBJECT_MAPPER.readValue(mcpServersJson, Map.class);
            Map<String, Object> servers = (Map<String, Object>) root.get("mcpServers");
            if (servers == null) return mcpServersJson;

            for (Map.Entry<String, Object> entry : servers.entrySet()) {
                Map<String, Object> server = (Map<String, Object>) entry.getValue();

                // 注入 headers
                if (!headerParams.isEmpty()) {
                    Map<String, String> headers =
                            server.containsKey("headers")
                                    ? new LinkedHashMap<>(
                                            (Map<String, String>) server.get("headers"))
                                    : new LinkedHashMap<>();
                    headers.putAll(headerParams);
                    server.put("headers", headers);
                }

                // 注入 query 参数到 url（同名参数替换，新参数追加）
                if (!queryParams.isEmpty() && server.containsKey("url")) {
                    String url = server.get("url").toString();
                    try {
                        java.net.URI uri = java.net.URI.create(url);
                        String baseUrl =
                                new java.net.URI(
                                                uri.getScheme(),
                                                uri.getAuthority(),
                                                uri.getPath(),
                                                null,
                                                null)
                                        .toString();

                        // 解析已有的 query 参数（保持顺序）
                        LinkedHashMap<String, String> merged = new LinkedHashMap<>();
                        if (uri.getRawQuery() != null) {
                            for (String pair : uri.getRawQuery().split("&")) {
                                int eq = pair.indexOf('=');
                                String k =
                                        eq >= 0
                                                ? java.net.URLDecoder.decode(
                                                        pair.substring(0, eq), "UTF-8")
                                                : java.net.URLDecoder.decode(pair, "UTF-8");
                                String v =
                                        eq >= 0
                                                ? java.net.URLDecoder.decode(
                                                        pair.substring(eq + 1), "UTF-8")
                                                : "";
                                merged.put(k, v);
                            }
                        }
                        // 用户参数覆盖同名 key
                        merged.putAll(queryParams);

                        // 重新拼接
                        StringBuilder sb = new StringBuilder(baseUrl);
                        sb.append("?");
                        boolean first = true;
                        for (Map.Entry<String, String> qp : merged.entrySet()) {
                            if (!first) sb.append("&");
                            sb.append(java.net.URLEncoder.encode(qp.getKey(), "UTF-8"))
                                    .append("=")
                                    .append(java.net.URLEncoder.encode(qp.getValue(), "UTF-8"));
                            first = false;
                        }
                        server.put("url", sb.toString());
                    } catch (Exception urlEx) {
                        log.warn("解析 URL query 参数失败，回退为追加模式: {}", urlEx.getMessage());
                        StringBuilder sb = new StringBuilder(url);
                        sb.append(url.contains("?") ? "&" : "?");
                        boolean first = true;
                        for (Map.Entry<String, String> qp : queryParams.entrySet()) {
                            if (!first) sb.append("&");
                            sb.append(java.net.URLEncoder.encode(qp.getKey(), "UTF-8"))
                                    .append("=")
                                    .append(java.net.URLEncoder.encode(qp.getValue(), "UTF-8"));
                            first = false;
                        }
                        server.put("url", sb.toString());
                    }
                }
            }

            return OBJECT_MAPPER.writeValueAsString(root);
        } catch (Exception e) {
            log.warn("注入 header/query 参数到 mcpServersJson 失败: {}", e.getMessage());
            return mcpServersJson;
        }
    }

    /**
     * 根据鉴权方式生成 CRD accesses YAML 片段。
     * isApiKeyAuth 为 true（bearer 或 apikey）：包含 authentication + name + port + type。
     * 否则：只有 port + type，不含 authentication 和 name。
     */
    private String buildAccessesYaml(boolean isApiKeyAuth, String accessName, String secretName) {
        List<Map<String, Object>> accesses = new ArrayList<>();
        Map<String, Object> access = new LinkedHashMap<>();

        if (isApiKeyAuth) {
            String resolvedSecretName =
                    StrUtil.isNotBlank(secretName) ? secretName : accessName + "-secret";
            Map<String, Object> source = new LinkedHashMap<>();
            source.put("key", "API_KEY");
            source.put("name", resolvedSecretName);
            source.put("optional", true);

            Map<String, Object> apiKey = new LinkedHashMap<>();
            apiKey.put("headerName", "Authorization");
            apiKey.put("source", source);

            Map<String, Object> authentication = new LinkedHashMap<>();
            authentication.put("apiKey", apiKey);

            access.put("authentication", authentication);
            access.put("name", accessName);
        }

        access.put("port", 80);
        access.put("type", "http");
        accesses.add(access);

        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setIndent(2);
        Yaml yaml = new Yaml(new Representer(options), options);
        String raw = yaml.dump(accesses);

        // 添加缩进前缀以匹配 CRD 模板层级
        StringBuilder sb = new StringBuilder();
        for (String line : raw.split("\n")) {
            sb.append("        ").append(line).append("\n");
        }
        return sb.toString().stripTrailing();
    }

    /**
     * 合并 connectionConfig 中提取的 env 和用户提交的 params。
     * userParams 优先级更高（覆盖同名 key）。
     *
     * @return 合并后的 JSON 字符串，或 null
     */
    @SuppressWarnings("unchecked")
    private String mergeEnvJson(String configEnvJson, String userParams) {
        Map<String, Object> merged = new LinkedHashMap<>();
        if (StrUtil.isNotBlank(configEnvJson)) {
            try {
                merged.putAll(OBJECT_MAPPER.readValue(configEnvJson, Map.class));
            } catch (Exception e) {
                log.warn("解析 configEnvJson 失败: {}", e.getMessage());
            }
        }
        if (StrUtil.isNotBlank(userParams)) {
            try {
                merged.putAll(OBJECT_MAPPER.readValue(userParams, Map.class));
            } catch (Exception e) {
                log.warn("解析 userParams 失败: {}", e.getMessage());
            }
        }
        if (merged.isEmpty()) return null;
        try {
            return OBJECT_MAPPER.writeValueAsString(merged);
        } catch (Exception e) {
            log.warn("序列化 mergedEnv 失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 从 env JSON 构建 CRD spec.env YAML 片段。
     * envJson 格式：{"KEY": "value", ...}
     */
    @SuppressWarnings("unchecked")
    private String buildEnvYaml(String envJson) {
        try {
            Map<String, Object> params = OBJECT_MAPPER.readValue(envJson, Map.class);
            if (params.isEmpty()) {
                return "";
            }
            List<Map<String, String>> envList = new ArrayList<>();
            for (Map.Entry<String, Object> e : params.entrySet()) {
                Map<String, String> entry = new LinkedHashMap<>();
                entry.put("name", e.getKey());
                entry.put("value", e.getValue() != null ? e.getValue().toString() : "");
                envList.add(entry);
            }
            Map<String, Object> envMap = new LinkedHashMap<>();
            envMap.put("env", envList);

            DumperOptions options = new DumperOptions();
            options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
            options.setIndent(2);
            Yaml yaml = new Yaml(new Representer(options), options);
            String raw = yaml.dump(envMap);
            StringBuilder sb = new StringBuilder();
            for (String line : raw.split("\n")) {
                sb.append("      ").append(line).append("\n");
            }
            return sb.toString();
        } catch (Exception e) {
            log.warn("解析 envJson 构建 env 失败: {}", e.getMessage());
            return "";
        }
    }

    /**
     * 从资源规格 JSON 中提取 CPU/内存等配置，用于 CRD 模板占位符替换。
     * 未配置的字段使用默认值。
     */
    @SuppressWarnings("unchecked")
    private Map<String, String> extractResourceVars(String resourceSpecJson) {
        if (StrUtil.isBlank(resourceSpecJson)) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST, "未配置资源规格，请在 MCP 沙箱部署配置中设置 CPU/内存等资源");
        }

        Map<String, Object> spec;
        try {
            spec = OBJECT_MAPPER.readValue(resourceSpecJson, Map.class);
        } catch (Exception e) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST, "资源规格 JSON 格式异常: " + e.getMessage());
        }

        String cpuRequest = getOrDefault(spec, "cpuRequest", "250m");
        String cpuLimit = getOrDefault(spec, "cpuLimit", "1");
        String memoryRequest = getOrDefault(spec, "memoryRequest", "256Mi");
        String memoryLimit = getOrDefault(spec, "memoryLimit", "512Mi");
        String ephemeralStorage = getOrDefault(spec, "ephemeralStorage", "1Gi");
        String image = getOrDefault(spec, "image", "");

        Map<String, String> vars = new LinkedHashMap<>();
        vars.put("CPU_REQUEST", cpuRequest);
        vars.put("CPU_LIMIT", cpuLimit);
        vars.put("MEMORY_REQUEST", memoryRequest);
        vars.put("MEMORY_LIMIT", memoryLimit);
        vars.put("EPHEMERAL_STORAGE", ephemeralStorage);
        if (StrUtil.isNotBlank(image)) {
            vars.put("IMAGE", image);
        }
        return vars;
    }

    private String getOrDefault(Map<String, Object> map, String key, String defaultValue) {
        Object val = map.get(key);
        return (val != null && StrUtil.isNotBlank(val.toString())) ? val.toString() : defaultValue;
    }

    /**
     * 从 clusterAttribute JSON 中提取 clusterId。
     */
    @SuppressWarnings("unchecked")
    private String extractClusterId(String clusterAttribute) {
        if (StrUtil.isBlank(clusterAttribute)) {
            return "";
        }
        try {
            Map<String, Object> map = OBJECT_MAPPER.readValue(clusterAttribute, Map.class);
            Object clusterId = map.get("clusterId");
            return clusterId != null ? clusterId.toString() : "";
        } catch (Exception e) {
            log.warn("解析 clusterAttribute 失败: {}", e.getMessage());
            return "";
        }
    }

    /**
     * 生成 K8s Secret 名称。
     * 格式：himarket-{sanitized-mcp-name}-{uuid前8位}-secret
     */
    public static String buildSecretName(String mcpName) {
        String name = StrUtil.blankToDefault(mcpName, "mcp-server");
        String sanitized =
                name.toLowerCase()
                        .replaceAll("[^a-z0-9-]", "-")
                        .replaceAll("-+", "-")
                        .replaceAll("^-|-$", "");
        String uuid8 = java.util.UUID.randomUUID().toString().substring(0, 8);
        String secretName = "himarket-" + sanitized + "-" + uuid8 + "-secret";
        return secretName.length() > 253 ? secretName.substring(0, 253) : secretName;
    }

    /**
     * 构建 K8s 资源名称：mcpName + userId 后 8 位。
     * 公开静态方法，供 McpSandboxDeployListener 在部署后记录 resourceName。
     */
    public static String buildResourceNameStatic(String mcpName, String userId) {
        String name = StrUtil.blankToDefault(mcpName, "mcp-server");
        String userSuffix =
                (userId != null && userId.length() >= 8)
                        ? userId.substring(userId.length() - 8)
                        : StrUtil.blankToDefault(userId, "unknown");
        String raw = name + "-" + userSuffix;
        String sanitized =
                raw.toLowerCase()
                        .replaceAll("[^a-z0-9-]", "-")
                        .replaceAll("-+", "-")
                        .replaceAll("^-|-$", "");
        return sanitized.length() > 253 ? sanitized.substring(0, 253) : sanitized;
    }

    private String buildResourceName(String mcpName, String userId) {
        return buildResourceNameStatic(mcpName, userId);
    }

    /**
     * 轮询 Endpoint CRD（kind: Endpoint）获取 status.url。
     * 使用 CompletableFuture + ScheduledExecutorService 避免阻塞 Tomcat 请求线程。
     * Endpoint 名称为 {toolserver名称}-primary。
     */
    private String pollEndpointUrl(KubernetesClient client, String namespace, String endpointName) {
        CompletableFuture<String> future = new CompletableFuture<>();
        long deadline = System.currentTimeMillis() + POLL_TIMEOUT_MS;

        Runnable pollTask =
                new Runnable() {
                    @Override
                    public void run() {
                        if (System.currentTimeMillis() > deadline) {
                            future.completeExceptionally(
                                    new BusinessException(
                                            ErrorCode.INVALID_REQUEST,
                                            "等待 Endpoint 就绪超时（"
                                                    + (POLL_TIMEOUT_MS / 1000)
                                                    + "秒）: "
                                                    + endpointName));
                            return;
                        }
                        try {
                            String url = tryGetEndpointUrl(client, namespace, endpointName);
                            if (url != null) {
                                future.complete(url);
                                return;
                            }
                        } catch (Exception e) {
                            log.debug(
                                    "[AgentRuntimeDeploy] 轮询 Endpoint 异常（可能尚未创建）: {}",
                                    e.getMessage());
                        }
                        // 未获取到，继续调度下一次轮询
                        pollScheduler.schedule(this, POLL_INTERVAL_MS, TimeUnit.MILLISECONDS);
                    }
                };

        // 启动首次轮询
        pollScheduler.schedule(pollTask, 0, TimeUnit.MILLISECONDS);

        try {
            return future.get(POLL_TIMEOUT_MS + 5_000, TimeUnit.MILLISECONDS);
        } catch (java.util.concurrent.ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof BusinessException) {
                throw (BusinessException) cause;
            }
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST, "轮询 Endpoint 失败: " + cause.getMessage());
        } catch (java.util.concurrent.TimeoutException e) {
            future.cancel(true);
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST,
                    "等待 Endpoint 就绪超时（" + (POLL_TIMEOUT_MS / 1000) + "秒）: " + endpointName);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "轮询 Endpoint 被中断");
        }
    }

    /**
     * 尝试从 Endpoint CRD 中获取 URL，获取不到返回 null。
     */
    @SuppressWarnings("unchecked")
    private String tryGetEndpointUrl(
            KubernetesClient client, String namespace, String endpointName) {
        GenericKubernetesResource endpoint =
                client.genericKubernetesResources(ENDPOINT_CONTEXT)
                        .inNamespace(namespace)
                        .withName(endpointName)
                        .get();

        if (endpoint == null) return null;

        Map<String, Object> status =
                (Map<String, Object>) endpoint.getAdditionalProperties().get("status");
        if (status == null) return null;

        // 优先取顶层 status.url
        String url = status.get("url") != null ? status.get("url").toString() : null;
        if (StrUtil.isNotBlank(url)) return url;

        // fallback: 从 status.addresses 中取 internet 类型
        Object addressesObj = status.get("addresses");
        if (addressesObj instanceof java.util.List) {
            for (Object addrObj : (java.util.List<?>) addressesObj) {
                if (addrObj instanceof Map) {
                    Map<String, Object> addr = (Map<String, Object>) addrObj;
                    if ("internet".equals(addr.get("type")) && addr.get("url") != null) {
                        return addr.get("url").toString();
                    }
                }
            }
        }
        return null;
    }
}
