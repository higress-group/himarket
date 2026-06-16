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

import com.alibaba.himarket.core.exception.BusinessException;
import com.alibaba.himarket.core.exception.ErrorCode;
import com.alibaba.himarket.core.utils.K8sClientUtils;
import com.alibaba.himarket.entity.SandboxInstance;
import com.alibaba.himarket.support.common.Strings;
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
 * Deployment strategy for AGENT_RUNTIME sandboxes.
 *
 * <p>Loads CRD YAML templates from classpath resources under {@code crd-templates/}, replaces
 * placeholders, and applies the rendered resources to the sandbox cluster. Users may customize the
 * templates as long as the placeholders are preserved.
 *
 * <p>Template placeholders: RESOURCE_NAME, NAMESPACE, CLUSTER_ID, SHOW_NAME, PROTOCOL,
 * MCP_SERVERS_JSON, ACCESSES_YAML, and ENV_YAML for stdio templates.
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

    /**
     * Maximum Endpoint polling wait time, in milliseconds.
     */
    private static final long POLL_TIMEOUT_MS = 60_000;

    private static final long POLL_INTERVAL_MS = 3_000;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final K8sClientUtils k8sClientUtils;

    public AgentRuntimeDeployStrategy(K8sClientUtils k8sClientUtils) {
        this.k8sClientUtils = k8sClientUtils;
    }

    /**
     * Scheduler for non-blocking polling. Daemon threads do not block JVM shutdown.
     */
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
        if (Strings.isBlank(sandbox.getKubeConfig())) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST,
                    String.format(
                            "Sandbox instance has no KubeConfig: %s", sandbox.getSandboxId()));
        }

        String ns = Strings.blankToDefault(namespace, "default");
        String resourceName = buildResourceName(mcpName, userId);
        String accessName = "himarket-" + userId;
        boolean isStdio = "stdio".equalsIgnoreCase(metaProtocolType);
        boolean isApiKeyAuth =
                "bearer".equalsIgnoreCase(authType) || "apikey".equalsIgnoreCase(authType);
        String secretName = null;

        // Build mcpServers JSON and strip env fields from it.
        String[] mcpResult = buildMcpServersJson(mcpName, connectionConfig);
        String mcpServersJson = mcpResult[0];
        String configEnvJson = mcpResult[1];

        // For non-stdio, route user params to headers/query/env based on extraParams.position.
        // For stdio, all user params are treated as env values.
        String envParamsJson = userParams;
        if (!isStdio && Strings.isNotBlank(extraParamsDef) && Strings.isNotBlank(userParams)) {
            try {
                Map<String, String> headerParams = new LinkedHashMap<>();
                Map<String, String> queryParams = new LinkedHashMap<>();
                Map<String, String> envParams = new LinkedHashMap<>();

                List<?> defs = OBJECT_MAPPER.readValue(extraParamsDef, List.class);
                @SuppressWarnings("unchecked")
                Map<String, String> userValues = OBJECT_MAPPER.readValue(userParams, Map.class);

                for (Object defObj : defs) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> def = (Map<String, Object>) defObj;
                    String paramName = (String) def.get("name");
                    String position = (String) def.getOrDefault("position", "env");
                    String value = userValues.get(paramName);
                    if (Strings.isBlank(value)) {
                        continue;
                    }

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

                if (!headerParams.isEmpty() || !queryParams.isEmpty()) {
                    mcpServersJson =
                            injectParamsIntoMcpServersJson(
                                    mcpServersJson, headerParams, queryParams);
                }

                envParamsJson =
                        envParams.isEmpty() ? null : OBJECT_MAPPER.writeValueAsString(envParams);
            } catch (Exception e) {
                log.warn(
                        "Failed to route params by position, falling back to env-only params,"
                                + " errorMessage={}",
                        e.getMessage(),
                        e);
                envParamsJson = userParams;
            }
        }

        // Merge env values from connectionConfig and user-submitted env params.
        String mergedEnvJson = mergeEnvJson(configEnvJson, envParamsJson);
        String envYaml = "";
        if (Strings.isNotBlank(mergedEnvJson)) {
            envYaml = buildEnvYaml(mergedEnvJson);
        }

        // Only include placeholders that are used by the templates.
        Map<String, String> vars = new LinkedHashMap<>();
        vars.put("RESOURCE_NAME", resourceName);
        vars.put("NAMESPACE", ns);
        vars.put("CLUSTER_ID", extractClusterId(sandbox.getClusterAttribute()));
        vars.put("SHOW_NAME", resourceName);
        vars.put(
                "PROTOCOL",
                "http".equalsIgnoreCase(metaProtocolType) ? "streamableHttp" : metaProtocolType);
        vars.put("MCP_SERVERS_JSON", mcpServersJson);
        // Generate a Secret name when authType is "apikey" and apiKey is present.
        if ("apikey".equalsIgnoreCase(authType) && Strings.isNotBlank(apiKey)) {
            secretName = buildSecretName(mcpName);
        }
        vars.put("ACCESSES_YAML", buildAccessesYaml(isApiKeyAuth, accessName, secretName));
        vars.put("ENV_YAML", envYaml);

        // Read CPU, memory, and other resource placeholders from MCP resourceSpec.
        Map<String, String> resourceVars = extractResourceVars(resourceSpec);
        vars.putAll(resourceVars);

        String templateFile =
                isStdio
                        ? "crd-templates/toolserver-stdio.yaml"
                        : "crd-templates/toolserver-sse.yaml";

        String renderedYaml = renderTemplate(templateFile, vars);

        // Use Jackson YAML to avoid SnakeYAML 2.x compatibility issues when deserializing CRDs.
        GenericKubernetesResource crd;
        try {
            crd =
                    new com.fasterxml.jackson.databind.ObjectMapper(
                                    new com.fasterxml.jackson.dataformat.yaml.YAMLFactory())
                            .readValue(renderedYaml, GenericKubernetesResource.class);
        } catch (Exception e) {
            throw new BusinessException(
                    ErrorCode.INTERNAL_ERROR,
                    e,
                    String.format("Failed to deserialize CRD YAML: %s", e.getMessage()));
        }

        if (crd.getMetadata().getLabels() == null) {
            crd.getMetadata().setLabels(new LinkedHashMap<>());
        }
        crd.getMetadata().getLabels().put("app.kubernetes.io/managed-by", "himarket");
        crd.getMetadata().getLabels().put("himarket.io/mcp-server-id", mcpServerId);
        crd.getMetadata().getLabels().put("himarket.io/user-id", userId);
        if (secretName != null) {
            crd.getMetadata().getLabels().put("himarket.io/ref-secret", secretName);
        }

        // Create Secret first, then CRD. Roll back the Secret if CRD creation fails.
        KubernetesClient client = k8sClientUtils.getClient(sandbox.getKubeConfig());

        if ("apikey".equalsIgnoreCase(authType) && Strings.isNotBlank(apiKey)) {
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
            log.info("Agent runtime Secret created, namespace={}, secretName={}", ns, secretName);
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
                    log.info(
                            "Rolled back agent runtime Secret after CRD creation failure,"
                                    + " secretName={}",
                            secretName);
                } catch (Exception rollbackEx) {
                    log.warn(
                            "Failed to roll back agent runtime Secret, secretName={},"
                                    + " errorMessage={}",
                            secretName,
                            rollbackEx.getMessage(),
                            rollbackEx);
                }
            }
            throw e;
        }

        log.info(
                "Agent runtime CRD applied, namespace={}, resourceName={}, template={}",
                ns,
                resourceName,
                templateFile);

        String endpointName = resourceName + "-primary";
        String endpointUrl = pollEndpointUrl(client, ns, endpointName);

        // When SSL verification is disabled, downgrade HTTPS to HTTP
        if (!sslVerify && endpointUrl != null && endpointUrl.startsWith("https://")) {
            endpointUrl = endpointUrl.replaceFirst("https://", "http://");
            log.info(
                    "Agent runtime endpoint protocol downgraded, namespace={}, resourceName={},"
                            + " sslVerify={}",
                    ns,
                    resourceName,
                    sslVerify);
        }

        log.info(
                "Agent runtime endpoint resolved, namespace={}, resourceName={}, endpoint={}",
                ns,
                resourceName,
                endpointUrl);
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
        if (Strings.isBlank(sandbox.getKubeConfig())) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST,
                    String.format(
                            "Sandbox instance has no KubeConfig: %s", sandbox.getSandboxId()));
        }

        String ns = Strings.blankToDefault(namespace, "default");
        KubernetesClient client = k8sClientUtils.getClient(sandbox.getKubeConfig());

        if (Strings.isBlank(resourceName)) {
            resourceName = buildResourceName(mcpName, userId);
        }

        if (Strings.isNotBlank(secretName)) {
            try {
                client.secrets().inNamespace(ns).withName(secretName).delete();
                log.info(
                        "Agent runtime Secret deleted, namespace={}, secretName={}",
                        ns,
                        secretName);
            } catch (Exception e) {
                log.warn(
                        "Failed to delete agent runtime Secret, namespace={}, secretName={},"
                                + " errorMessage={}",
                        ns,
                        secretName,
                        e.getMessage(),
                        e);
            }
        }

        String endpointName = resourceName + "-primary";

        try {
            client.genericKubernetesResources(CRD_CONTEXT)
                    .inNamespace(ns)
                    .withName(resourceName)
                    .delete();
            log.info(
                    "Agent runtime ToolServer CRD deleted, namespace={}, resourceName={}",
                    ns,
                    resourceName);
        } catch (Exception e) {
            log.warn(
                    "Failed to delete agent runtime ToolServer CRD, namespace={}, resourceName={},"
                            + " errorMessage={}",
                    ns,
                    resourceName,
                    e.getMessage(),
                    e);
            return;
        }

        waitEndpointDeleted(client, ns, endpointName);
    }

    /**
     * Waits for the Endpoint CRD to be asynchronously cleaned up by the sandbox.
     *
     * <p>CompletableFuture and ScheduledExecutorService avoid blocking Tomcat request threads. A
     * timeout only emits a warning and does not block a later rebuild.
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
                            future.complete(null);
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
                                        "Agent runtime endpoint cleaned, namespace={},"
                                                + " endpointName={}",
                                        namespace,
                                        endpointName);
                                future.complete(null);
                                return;
                            }
                        } catch (Exception e) {
                            log.info(
                                    "Agent runtime endpoint treated as cleaned after query failure,"
                                            + " namespace={}, endpointName={}",
                                    namespace,
                                    endpointName);
                            future.complete(null);
                            return;
                        }
                        log.debug(
                                "Agent runtime endpoint is not cleaned yet, endpointName={}",
                                endpointName);
                        pollScheduler.schedule(this, POLL_INTERVAL_MS, TimeUnit.MILLISECONDS);
                    }
                };

        pollScheduler.schedule(pollTask, 0, TimeUnit.MILLISECONDS);

        try {
            future.get(POLL_TIMEOUT_MS + 5_000, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            log.warn(
                    "Agent runtime endpoint cleanup wait failed or timed out, timeoutSeconds={},"
                            + " endpointName={}",
                    POLL_TIMEOUT_MS / 1000,
                    endpointName);
        }
    }

    // Private helpers.

    /**
     * Loads a template from classpath and replaces placeholders.
     */
    private String renderTemplate(String templatePath, Map<String, String> variables) {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(templatePath)) {
            if (is == null) {
                throw new BusinessException(
                        ErrorCode.INVALID_REQUEST,
                        String.format("CRD template file does not exist: %s", templatePath));
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
                    ErrorCode.INVALID_REQUEST,
                    e,
                    String.format("Failed to read CRD template: %s", e.getMessage()));
        }
    }

    /**
     * Parses connectionConfig JSON, builds mcpServers JSON, and removes env values.
     *
     * <p>env values are passed through CRD spec.env and should not remain in mcpServers JSON.
     *
     * @return String[2]: [0]=mcpServersJson, [1]=extracted env JSON, possibly null
     * @throws BusinessException when connectionConfig is blank or cannot be parsed
     */
    private String[] buildMcpServersJson(String mcpName, String connectionConfig) {
        if (Strings.isBlank(connectionConfig)) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST, "MCP connectionConfig is empty and cannot deploy");
        }

        String serverName = Strings.blankToDefault(mcpName, "mcp-server");

        try {
            McpConnectionConfig config = McpConnectionConfig.parse(connectionConfig);

            // Format 3: { mcpServerConfig: { rawConfig: {...} } }. Parse rawConfig recursively.
            if (config.isWrappedFormat()) {
                return buildMcpServersJson(mcpName, config.getRawConfigJson());
            }

            // Format 1 or 2: extract env and build mcpServers JSON without env.
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
                    ErrorCode.INVALID_REQUEST,
                    "Unrecognized connectionConfig format. Check the MCP config");
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST,
                    e,
                    String.format("Failed to parse connectionConfig: %s", e.getMessage()));
        }
    }

    /**
     * Injects header and query parameters into mcpServers JSON.
     *
     * <p>Header parameters are written to each server's {@code headers} field. Query parameters are
     * appended to each server's {@code url} query string.
     */
    @SuppressWarnings("unchecked")
    private String injectParamsIntoMcpServersJson(
            String mcpServersJson,
            Map<String, String> headerParams,
            Map<String, String> queryParams) {
        try {
            Map<String, Object> root = OBJECT_MAPPER.readValue(mcpServersJson, Map.class);
            Map<String, Object> servers = (Map<String, Object>) root.get("mcpServers");
            if (servers == null) {
                return mcpServersJson;
            }

            for (Map.Entry<String, Object> entry : servers.entrySet()) {
                Map<String, Object> server = (Map<String, Object>) entry.getValue();

                if (!headerParams.isEmpty()) {
                    Map<String, String> headers =
                            server.containsKey("headers")
                                    ? new LinkedHashMap<>(
                                            (Map<String, String>) server.get("headers"))
                                    : new LinkedHashMap<>();
                    headers.putAll(headerParams);
                    server.put("headers", headers);
                }

                // Existing query parameters keep their order; user parameters replace same-name
                // keys and append new keys.
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
                        merged.putAll(queryParams);

                        StringBuilder sb = new StringBuilder(baseUrl);
                        sb.append("?");
                        boolean first = true;
                        for (Map.Entry<String, String> qp : merged.entrySet()) {
                            if (!first) {
                                sb.append("&");
                            }
                            sb.append(java.net.URLEncoder.encode(qp.getKey(), "UTF-8"))
                                    .append("=")
                                    .append(java.net.URLEncoder.encode(qp.getValue(), "UTF-8"));
                            first = false;
                        }
                        server.put("url", sb.toString());
                    } catch (Exception urlEx) {
                        log.warn(
                                "Failed to parse URL query parameters, falling back to append"
                                        + " mode, errorMessage={}",
                                urlEx.getMessage(),
                                urlEx);
                        StringBuilder sb = new StringBuilder(url);
                        sb.append(url.contains("?") ? "&" : "?");
                        boolean first = true;
                        for (Map.Entry<String, String> qp : queryParams.entrySet()) {
                            if (!first) {
                                sb.append("&");
                            }
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
            log.warn(
                    "Failed to inject header/query parameters into mcpServersJson, errorMessage={}",
                    e.getMessage(),
                    e);
            return mcpServersJson;
        }
    }

    /**
     * Builds the CRD accesses YAML fragment from the authentication mode.
     *
     * <p>When isApiKeyAuth is true, for bearer or apikey, the fragment includes authentication,
     * name, port, and type. Otherwise it includes only port and type.
     */
    private String buildAccessesYaml(boolean isApiKeyAuth, String accessName, String secretName) {
        List<Map<String, Object>> accesses = new ArrayList<>();
        Map<String, Object> access = new LinkedHashMap<>();

        if (isApiKeyAuth) {
            String resolvedSecretName =
                    Strings.isNotBlank(secretName) ? secretName : accessName + "-secret";
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

        // Add indentation to match the CRD template nesting level.
        StringBuilder sb = new StringBuilder();
        for (String line : raw.split("\n")) {
            sb.append("        ").append(line).append("\n");
        }
        return sb.toString().stripTrailing();
    }

    /**
     * Merges env extracted from connectionConfig with user-submitted params.
     *
     * <p>userParams has higher priority and overrides same-name keys.
     *
     * @return merged JSON string, or null
     */
    @SuppressWarnings("unchecked")
    private String mergeEnvJson(String configEnvJson, String userParams) {
        Map<String, Object> merged = new LinkedHashMap<>();
        if (Strings.isNotBlank(configEnvJson)) {
            try {
                merged.putAll(OBJECT_MAPPER.readValue(configEnvJson, Map.class));
            } catch (Exception e) {
                log.warn("Failed to parse configEnvJson, errorMessage={}", e.getMessage(), e);
            }
        }
        if (Strings.isNotBlank(userParams)) {
            try {
                merged.putAll(OBJECT_MAPPER.readValue(userParams, Map.class));
            } catch (Exception e) {
                log.warn("Failed to parse userParams, errorMessage={}", e.getMessage(), e);
            }
        }
        if (merged.isEmpty()) {
            return null;
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(merged);
        } catch (Exception e) {
            log.warn("Failed to serialize mergedEnv, errorMessage={}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Builds a CRD spec.env YAML fragment from env JSON.
     *
     * <p>envJson format: {"KEY": "value", ...}
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
            log.warn(
                    "Failed to parse envJson while building env YAML, errorMessage={}",
                    e.getMessage(),
                    e);
            return "";
        }
    }

    /**
     * Extracts CPU, memory, and other resource values from resourceSpec JSON.
     *
     * <p>Missing fields use defaults and the resulting values replace CRD template placeholders.
     */
    @SuppressWarnings("unchecked")
    private Map<String, String> extractResourceVars(String resourceSpecJson) {
        if (Strings.isBlank(resourceSpecJson)) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST,
                    "Resource spec is required. Set CPU, memory, and related resources in the MCP"
                            + " sandbox deployment config");
        }

        Map<String, Object> spec;
        try {
            spec = OBJECT_MAPPER.readValue(resourceSpecJson, Map.class);
        } catch (Exception e) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST,
                    e,
                    String.format("Resource spec JSON format is invalid: %s", e.getMessage()));
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
        if (Strings.isNotBlank(image)) {
            vars.put("IMAGE", image);
        }
        return vars;
    }

    private String getOrDefault(Map<String, Object> map, String key, String defaultValue) {
        Object val = map.get(key);
        return (val != null && Strings.isNotBlank(val.toString())) ? val.toString() : defaultValue;
    }

    /**
     * Extracts clusterId from clusterAttribute JSON.
     */
    @SuppressWarnings("unchecked")
    private String extractClusterId(String clusterAttribute) {
        if (Strings.isBlank(clusterAttribute)) {
            return "";
        }
        try {
            Map<String, Object> map = OBJECT_MAPPER.readValue(clusterAttribute, Map.class);
            Object clusterId = map.get("clusterId");
            return clusterId != null ? clusterId.toString() : "";
        } catch (Exception e) {
            log.warn("Failed to parse clusterAttribute, errorMessage={}", e.getMessage(), e);
            return "";
        }
    }

    /**
     * Builds a K8s Secret name.
     *
     * <p>Format: himarket-{sanitized-mcp-name}-{first-8-uuid-chars}-secret
     */
    public static String buildSecretName(String mcpName) {
        String name = Strings.blankToDefault(mcpName, "mcp-server");
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
     * Builds a K8s resource name from mcpName and the last 8 characters of userId.
     *
     * <p>This public static method is used by McpSandboxDeployListener when recording resourceName
     * after deployment.
     */
    public static String buildResourceNameStatic(String mcpName, String userId) {
        String name = Strings.blankToDefault(mcpName, "mcp-server");
        String userSuffix =
                (userId != null && userId.length() >= 8)
                        ? userId.substring(userId.length() - 8)
                        : Strings.blankToDefault(userId, "unknown");
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
     * Polls the Endpoint CRD for status.url.
     *
     * <p>CompletableFuture and ScheduledExecutorService avoid blocking Tomcat request threads. The
     * Endpoint name is {toolserver-name}-primary.
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
                                            String.format(
                                                    "Timed out waiting for Endpoint readiness"
                                                            + " after %s seconds: %s",
                                                    POLL_TIMEOUT_MS / 1000, endpointName)));
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
                                    "Agent runtime endpoint polling failed before creation,"
                                            + " errorMessage={}",
                                    e.getMessage());
                        }
                        pollScheduler.schedule(this, POLL_INTERVAL_MS, TimeUnit.MILLISECONDS);
                    }
                };

        pollScheduler.schedule(pollTask, 0, TimeUnit.MILLISECONDS);

        try {
            return future.get(POLL_TIMEOUT_MS + 5_000, TimeUnit.MILLISECONDS);
        } catch (java.util.concurrent.ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof BusinessException) {
                throw (BusinessException) cause;
            }
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST,
                    cause,
                    String.format("Endpoint polling failed: %s", cause.getMessage()));
        } catch (java.util.concurrent.TimeoutException e) {
            future.cancel(true);
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST,
                    String.format(
                            "Timed out waiting for Endpoint readiness after %s seconds: %s",
                            POLL_TIMEOUT_MS / 1000, endpointName));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST, "Endpoint polling was interrupted");
        }
    }

    /**
     * Tries to get the URL from the Endpoint CRD, returning null when unavailable.
     */
    @SuppressWarnings("unchecked")
    private String tryGetEndpointUrl(
            KubernetesClient client, String namespace, String endpointName) {
        GenericKubernetesResource endpoint =
                client.genericKubernetesResources(ENDPOINT_CONTEXT)
                        .inNamespace(namespace)
                        .withName(endpointName)
                        .get();

        if (endpoint == null) {
            return null;
        }

        Map<String, Object> status =
                (Map<String, Object>) endpoint.getAdditionalProperties().get("status");
        if (status == null) {
            return null;
        }

        // Prefer top-level status.url.
        String url = status.get("url") != null ? status.get("url").toString() : null;
        if (Strings.isNotBlank(url)) {
            return url;
        }

        // Fallback to the internet address in status.addresses.
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
