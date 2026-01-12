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

package com.alibaba.himarket.service.gateway;

import cn.hutool.json.JSONUtil;
import com.alibaba.himarket.core.exception.BusinessException;
import com.alibaba.himarket.core.exception.ErrorCode;
import com.alibaba.himarket.dto.params.gateway.QueryAdpAIGatewayParam;
import com.alibaba.himarket.dto.result.agent.AgentAPIResult;
import com.alibaba.himarket.dto.result.common.DomainResult;
import com.alibaba.himarket.dto.result.common.PageResult;
import com.alibaba.himarket.dto.result.gateway.AdpGatewayInstanceResult;
import com.alibaba.himarket.dto.result.gateway.GatewayResult;
import com.alibaba.himarket.dto.result.httpapi.APIResult;
import com.alibaba.himarket.dto.result.httpapi.HttpRouteResult;
import com.alibaba.himarket.dto.result.httpapi.ServiceResult;
import com.alibaba.himarket.dto.result.mcp.AdpMcpServerListResult;
import com.alibaba.himarket.dto.result.mcp.GatewayMCPServerResult;
import com.alibaba.himarket.dto.result.mcp.MCPConfigResult;
import com.alibaba.himarket.dto.result.model.AIGWModelAPIResult;
import com.alibaba.himarket.dto.result.model.GatewayModelAPIResult;
import com.alibaba.himarket.dto.result.model.ModelConfigResult;
import com.alibaba.himarket.entity.Consumer;
import com.alibaba.himarket.entity.ConsumerCredential;
import com.alibaba.himarket.entity.Gateway;
import com.alibaba.himarket.service.gateway.client.AdpAIGatewayClient;
import com.alibaba.himarket.support.consumer.AdpAIAuthConfig;
import com.alibaba.himarket.support.consumer.ConsumerAuthConfig;
import com.alibaba.himarket.support.enums.GatewayType;
import com.alibaba.himarket.support.gateway.AdpAIGatewayConfig;
import com.alibaba.himarket.support.gateway.GatewayConfig;
import com.alibaba.himarket.support.product.APIGRefConfig;
import com.aliyun.sdk.service.apig20240327.models.HttpApiApiInfo;
import java.net.URI;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

/** ADP AI网关操作器 */
@Service
@Slf4j
public class AdpAIGatewayOperator extends GatewayOperator {

    @Override
    public PageResult<APIResult> fetchHTTPAPIs(Gateway gateway, int page, int size) {
        return null;
    }

    @Override
    public PageResult<APIResult> fetchRESTAPIs(Gateway gateway, int page, int size) {
        return null;
    }

    @Override
    public PageResult<? extends GatewayMCPServerResult> fetchMcpServers(
            Gateway gateway, int page, int size) {
        AdpAIGatewayConfig config = gateway.getAdpAIGatewayConfig();
        if (config == null) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "ADP AI Gateway 配置缺失");
        }

        AdpAIGatewayClient client = new AdpAIGatewayClient(config);
        try {
            String url = client.getFullUrl("/mcpServer/listMcpServers");
            // 修复：添加必需的 gwInstanceId 参数
            String requestBody =
                    String.format(
                            "{\"current\": %d, \"size\": %d, \"gwInstanceId\": \"%s\"}",
                            page, size, gateway.getGatewayId());
            HttpEntity<String> requestEntity = client.createRequestEntity(requestBody);

            ResponseEntity<AdpMcpServerListResult> response =
                    client.getRestTemplate()
                            .exchange(
                                    url,
                                    HttpMethod.POST,
                                    requestEntity,
                                    AdpMcpServerListResult.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                AdpMcpServerListResult result = response.getBody();
                if (result.getCode() != null
                        && result.getCode() == 200
                        && result.getData() != null) {
                    List<GatewayMCPServerResult> items = new ArrayList<>();
                    if (result.getData().getRecords() != null) {
                        items.addAll(result.getData().getRecords());
                    }
                    int total =
                            result.getData().getTotal() != null ? result.getData().getTotal() : 0;
                    return PageResult.of(items, page, size, total);
                }
                String msg = result.getMessage() != null ? result.getMessage() : result.getMsg();
                throw new BusinessException(ErrorCode.GATEWAY_ERROR, msg);
            }
            throw new BusinessException(
                    ErrorCode.GATEWAY_ERROR, "调用 ADP /mcpServer/listMcpServers 失败");
        } catch (Exception e) {
            log.error("Error fetching ADP MCP servers", e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, e.getMessage());
        } finally {
            client.close();
        }
    }

    @Override
    public PageResult<AgentAPIResult> fetchAgentAPIs(Gateway gateway, int page, int size) {
        return null;
    }

    @Override
    public PageResult<? extends GatewayModelAPIResult> fetchModelAPIs(
            Gateway gateway, int page, int size) {
        AdpAIGatewayConfig config = gateway.getAdpAIGatewayConfig();
        if (config == null) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "ADP AI Gateway 配置缺失");
        }

        AdpAIGatewayClient client = new AdpAIGatewayClient(config);
        try {
            String url = client.getFullUrl("/modelapi/listModelApis");
            // 构建请求体
            String requestBody =
                    String.format(
                            "{\"size\": %d, \"currentPage\": %d, \"gwInstanceId\": \"%s\"}",
                            size, page, gateway.getGatewayId());
            HttpEntity<String> requestEntity = client.createRequestEntity(requestBody);

            // 发起 HTTP 请求
            ResponseEntity<AdpAiServiceListResult> response =
                    client.getRestTemplate()
                            .exchange(
                                    url,
                                    HttpMethod.POST,
                                    requestEntity,
                                    AdpAiServiceListResult.class);

            // 处理响应
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                AdpAiServiceListResult result = response.getBody();
                if (result.getCode() != null
                        && result.getCode() == 200
                        && result.getData() != null) {

                    List<GatewayModelAPIResult> items = new ArrayList<>();
                    if (result.getData().getRecords() != null) {
                        items =
                                result.getData().getRecords().stream()
                                        .map(this::convertToModelAPIResult)
                                        .collect(Collectors.toList());
                    }

                    int total =
                            result.getData().getTotal() != null ? result.getData().getTotal() : 0;
                    return PageResult.of(items, page, size, total);
                }
                String msg = result.getMessage() != null ? result.getMessage() : result.getMsg();
                throw new BusinessException(ErrorCode.GATEWAY_ERROR, msg);
            }
            throw new BusinessException(
                    ErrorCode.GATEWAY_ERROR, "调用 ADP /modelapi/listModelApis 失败");
        } catch (Exception e) {
            log.error("Error fetching ADP model APIs", e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, e.getMessage());
        } finally {
            client.close();
        }
    }

    @Override
    public String fetchAPIConfig(Gateway gateway, Object config) {
        return "";
    }

    @Override
    public String fetchMcpConfig(Gateway gateway, Object conf) {
        AdpAIGatewayConfig config = gateway.getAdpAIGatewayConfig();
        if (config == null) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "ADP AI Gateway 配置缺失");
        }

        // 从 conf 参数中获取 APIGRefConfig
        APIGRefConfig apigRefConfig = (APIGRefConfig) conf;
        if (apigRefConfig == null || apigRefConfig.getMcpServerName() == null) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "MCP Server 名称缺失");
        }

        AdpAIGatewayClient client = new AdpAIGatewayClient(config);
        try {
            String url = client.getFullUrl("/mcpServer/getMcpServer");

            // 构建请求体，包含 gwInstanceId 和 mcpServerName
            String requestBody =
                    String.format(
                            "{\"gwInstanceId\": \"%s\", \"mcpServerName\": \"%s\"}",
                            gateway.getGatewayId(), apigRefConfig.getMcpServerName());

            HttpEntity<String> requestEntity = client.createRequestEntity(requestBody);

            ResponseEntity<AdpMcpServerDetailResult> response =
                    client.getRestTemplate()
                            .exchange(
                                    url,
                                    HttpMethod.POST,
                                    requestEntity,
                                    AdpMcpServerDetailResult.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                AdpMcpServerDetailResult result = response.getBody();
                if (result.getCode() != null
                        && result.getCode() == 200
                        && result.getData() != null) {
                    return convertToMCPConfig(result.getData(), config);
                }
                String msg = result.getMessage() != null ? result.getMessage() : result.getMsg();
                throw new BusinessException(ErrorCode.GATEWAY_ERROR, msg);
            }
            throw new BusinessException(
                    ErrorCode.GATEWAY_ERROR, "调用 ADP /mcpServer/getMcpServer 失败");
        } catch (Exception e) {
            log.error(
                    "Error fetching ADP MCP config for server: {}",
                    apigRefConfig.getMcpServerName(),
                    e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, e.getMessage());
        } finally {
            client.close();
        }
    }

    @Override
    public String fetchAgentConfig(Gateway gateway, Object conf) {
        return "";
    }

    @Override
    public String fetchModelConfig(Gateway gateway, Object conf) {
        AdpAIGatewayConfig config = gateway.getAdpAIGatewayConfig();
        if (config == null) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "ADP AI Gateway 配置缺失");
        }

        // 入参 conf 通常为 APIGRefConfig，包含选中的 apiId
        APIGRefConfig refConfig = (APIGRefConfig) conf;
        if (refConfig == null || refConfig.getModelApiId() == null) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "Model API ID 缺失");
        }

        AdpAIGatewayClient client = new AdpAIGatewayClient(config);
        try {
            String url = client.getFullUrl("/modelapi/getModelApi");

            // 构建请求体
            String requestBody =
                    String.format(
                            "{\"gwInstanceId\": \"%s\", \"id\": \"%s\"}",
                            gateway.getGatewayId(), refConfig.getModelApiId());

            HttpEntity<String> requestEntity = client.createRequestEntity(requestBody);

            ResponseEntity<AdpAiServiceDetailResult> response =
                    client.getRestTemplate()
                            .exchange(
                                    url,
                                    HttpMethod.POST,
                                    requestEntity,
                                    AdpAiServiceDetailResult.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                AdpAiServiceDetailResult result = response.getBody();
                if (result.getCode() != null
                        && result.getCode() == 200
                        && result.getData() != null) {
                    return convertToModelConfigJson(result.getData(), gateway, config);
                }
                String msg = result.getMessage() != null ? result.getMessage() : result.getMsg();
                throw new BusinessException(ErrorCode.GATEWAY_ERROR, msg);
            }
            throw new BusinessException(ErrorCode.GATEWAY_ERROR, "调用 ADP /modelapi/getModelApi 失败");

        } catch (Exception e) {
            log.error("Error fetching ADP model config", e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, e.getMessage());
        } finally {
            client.close();
        }
    }

    @Override
    public String fetchMcpToolsForConfig(Gateway gateway, Object conf) {
        return null;
    }

    /** 将 ADP MCP Server 详情转换为 MCPConfigResult 格式 */
    private String convertToMCPConfig(
            AdpMcpServerDetailResult.AdpMcpServerDetail data, AdpAIGatewayConfig config) {
        MCPConfigResult mcpConfig = new MCPConfigResult();
        mcpConfig.setMcpServerName(data.getName());

        // 设置 MCP Server 配置
        MCPConfigResult.MCPServerConfig serverConfig = new MCPConfigResult.MCPServerConfig();
        serverConfig.setPath("/mcp-servers/" + data.getName());

        // 获取网关实例访问信息并设置域名信息
        List<DomainResult> domains = getGatewayAccessDomains(data.getGwInstanceId(), config);
        if (domains != null && !domains.isEmpty()) {
            serverConfig.setDomains(domains);
        } else {
            // 如果无法获取网关访问信息，则使用原有的services信息作为备选
            if (data.getServices() != null && !data.getServices().isEmpty()) {
                List<DomainResult> fallbackDomains =
                        data.getServices().stream()
                                .map(
                                        domain ->
                                                DomainResult.builder()
                                                        .domain(
                                                                domain.getName()
                                                                        + ":"
                                                                        + domain.getPort())
                                                        .protocol("http")
                                                        .build())
                                .collect(Collectors.toList());
                serverConfig.setDomains(fallbackDomains);
            }
        }

        mcpConfig.setMcpServerConfig(serverConfig);

        // 设置工具配置
        mcpConfig.setTools(data.getRawConfigurations());

        // 设置元数据
        MCPConfigResult.McpMetadata meta = new MCPConfigResult.McpMetadata();
        meta.setSource(GatewayType.ADP_AI_GATEWAY.name());
        meta.setCreateFromType(data.getType());
        mcpConfig.setMeta(meta);

        return JSONUtil.toJsonStr(mcpConfig);
    }

    /** 获取网关实例的访问信息并构建域名列表 */
    private List<DomainResult> getGatewayAccessDomains(
            String gwInstanceId, AdpAIGatewayConfig config) {
        AdpAIGatewayClient client = new AdpAIGatewayClient(config);
        try {
            String url = client.getFullUrl("/gatewayInstance/getInstanceInfo");
            String requestBody = String.format("{\"gwInstanceId\": \"%s\"}", gwInstanceId);
            HttpEntity<String> requestEntity = client.createRequestEntity(requestBody);

            // 注意：getInstanceInfo 返回的 data 是单个实例对象（无 records 字段），直接从 data.accessMode 读取
            ResponseEntity<String> response =
                    client.getRestTemplate()
                            .exchange(url, HttpMethod.POST, requestEntity, String.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                cn.hutool.json.JSONObject root = JSONUtil.parseObj(response.getBody());
                Integer code = root.getInt("code");
                if (code != null && code == 200 && root.containsKey("data")) {
                    cn.hutool.json.JSONObject dataObj = root.getJSONObject("data");
                    if (dataObj != null && dataObj.containsKey("accessMode")) {
                        cn.hutool.json.JSONArray arr = dataObj.getJSONArray("accessMode");
                        List<AdpGatewayInstanceResult.AccessMode> accessModes =
                                JSONUtil.toList(arr, AdpGatewayInstanceResult.AccessMode.class);
                        return buildDomainsFromAccessModes(accessModes);
                    }
                    log.warn("Gateway instance has no accessMode, instanceId={}", gwInstanceId);
                    return null;
                }
                String message = root.getStr("message", root.getStr("msg"));
                log.warn("Failed to get gateway instance access info: {}", message);
                return null;
            }
            log.warn("Failed to call gateway instance access API");
            return null;
        } catch (Exception e) {
            log.error("Error fetching gateway access info for instance: {}", gwInstanceId, e);
            return null;
        } finally {
            client.close();
        }
    }

    /** 根据网关实例访问信息构建域名列表 */
    private List<DomainResult> buildDomainsFromAccessInfo(
            AdpGatewayInstanceResult.AdpGatewayInstanceData data) {
        // 兼容 listInstances 调用：取第一条记录的 accessMode
        if (data != null && data.getRecords() != null && !data.getRecords().isEmpty()) {
            AdpGatewayInstanceResult.AdpGatewayInstance instance = data.getRecords().get(0);
            if (instance.getAccessMode() != null) {
                return buildDomainsFromAccessModes(instance.getAccessMode());
            }
        }
        return new ArrayList<>();
    }

    private List<DomainResult> buildDomainsFromAccessModes(
            List<AdpGatewayInstanceResult.AccessMode> accessModes) {
        List<DomainResult> domains = new ArrayList<>();
        if (accessModes == null || accessModes.isEmpty()) {
            return domains;
        }
        AdpGatewayInstanceResult.AccessMode accessMode = accessModes.get(0);

        // 1) LoadBalancer: externalIps:80
        if ("LoadBalancer".equalsIgnoreCase(accessMode.getAccessModeType())) {
            if (accessMode.getExternalIps() != null && !accessMode.getExternalIps().isEmpty()) {
                for (String externalIp : accessMode.getExternalIps()) {
                    if (externalIp == null || externalIp.isEmpty()) {
                        continue;
                    }
                    DomainResult domain =
                            DomainResult.builder()
                                    .domain(externalIp + ":80")
                                    .protocol("http")
                                    .build();
                    domains.add(domain);
                }
            }
        }

        // 2) NodePort: ips + ports → ip:nodePort
        if (domains.isEmpty() && "NodePort".equalsIgnoreCase(accessMode.getAccessModeType())) {
            List<String> ips = accessMode.getIps();
            List<String> ports = accessMode.getPorts();
            if (ips != null && !ips.isEmpty() && ports != null && !ports.isEmpty()) {
                for (String ip : ips) {
                    if (ip == null || ip.isEmpty()) {
                        continue;
                    }
                    for (String portMapping : ports) {
                        if (portMapping == null || portMapping.isEmpty()) {
                            continue;
                        }
                        String[] parts = portMapping.split(":");
                        if (parts.length >= 2) {
                            String nodePort = parts[1].split("/")[0];
                            DomainResult domain =
                                    DomainResult.builder()
                                            .domain(ip + ":" + nodePort)
                                            .protocol("http")
                                            .build();
                            domains.add(domain);
                        }
                    }
                }
            }
        }

        // 3) fallback: only externalIps → :80
        if (domains.isEmpty()
                && accessMode.getExternalIps() != null
                && !accessMode.getExternalIps().isEmpty()) {
            for (String externalIp : accessMode.getExternalIps()) {
                if (externalIp == null || externalIp.isEmpty()) {
                    continue;
                }
                DomainResult domain =
                        DomainResult.builder().domain(externalIp + ":80").protocol("http").build();
                domains.add(domain);
            }
        }

        return domains;
    }

    /** 将 ADP 模型对象转换为系统通用模型对象 */
    private GatewayModelAPIResult convertToModelAPIResult(
            AdpAiServiceListResult.AdpAiServiceItem item) {
        // 使用 AIGWModelAPIResult
        return AIGWModelAPIResult.builder()
                .modelApiId(item.getId())
                .modelApiName(item.getApiName())
                .build();
    }

    /** 将 ADP 模型详情转换为 ModelConfigResult JSON 字符串 */
    private String convertToModelConfigJson(
            AdpAiServiceDetailResult.AdpAiServiceDetail data,
            Gateway gateway,
            AdpAIGatewayConfig config) {

        // 设置访问域名/地址
        // 优先使用 getModelApi 返回的 domainNameList
        List<DomainResult> domains = null;
        if (data.getDomainNameList() != null && !data.getDomainNameList().isEmpty()) {
            domains =
                    data.getDomainNameList().stream()
                            .map(
                                    domain ->
                                            DomainResult.builder()
                                                    .domain(domain)
                                                    .protocol("http") // 默认使用http协议
                                                    .build())
                            .collect(Collectors.toList());
        } else {
            // 降级方案：使用网关入口信息
            domains = getGatewayAccessDomains(gateway.getGatewayId(), config);
        }

        // 构建ModelAPIConfig
        ModelConfigResult.ModelAPIConfig apiConfig =
                ModelConfigResult.ModelAPIConfig.builder()
                        .aiProtocols(
                                Collections.singletonList(
                                        mapProtocol(data.getProtocol()))) // 使用协议信息
                        .modelCategory(data.getSceneType()) // 使用场景类型作为模型类别
                        .routes(buildRoutesFromAdpService(data, domains)) // 从ADP服务数据构建routes
                        .services(buildServicesFromAdpService(data)) // 从ADP服务数据构建services
                        .build();

        ModelConfigResult modelConfig = new ModelConfigResult();
        modelConfig.setModelAPIConfig(apiConfig);

        return JSONUtil.toJsonStr(modelConfig);
    }

    /** 从ADP服务数据构建路由列表 */
    private List<HttpRouteResult> buildRoutesFromAdpService(
            AdpAiServiceDetailResult.AdpAiServiceDetail data, List<DomainResult> domains) {
        if (data.getPathList() == null || data.getPathList().isEmpty()) {
            return Collections.emptyList();
        }

        List<HttpRouteResult> routes = new ArrayList<>();
        for (String path : data.getPathList()) {
            HttpRouteResult route = new HttpRouteResult();

            // 设置域名
            route.setDomains(domains);

            // 设置匹配规则，路径前面加上basePath
            String fullPath = data.getBasePath() != null ? data.getBasePath() + path : path;
            HttpRouteResult.RouteMatchResult matchResult =
                    HttpRouteResult.RouteMatchResult.builder()
                            .methods(Collections.singletonList("POST")) // 默认使用POST方法
                            .path(
                                    HttpRouteResult.RouteMatchPath.builder()
                                            .value(fullPath)
                                            .type("Exact") // 使用完全匹配
                                            .build())
                            .build();
            route.setMatch(matchResult);

            // 设置描述
            route.setDescription(data.getDescription());

            // 设置为非内置路由
            route.setBuiltin(false);

            routes.add(route);
        }

        return routes;
    }

    /** 从ADP服务数据构建服务列表 */
    private List<ServiceResult> buildServicesFromAdpService(
            AdpAiServiceDetailResult.AdpAiServiceDetail data) {
        if (data.getDomainNameList() == null || data.getDomainNameList().isEmpty()) {
            return Collections.emptyList();
        }

        List<ServiceResult> services = new ArrayList<>();
        for (String domainName : data.getDomainNameList()) {
            // 解析域名和端口
            String[] parts = domainName.split(":");
            String host = parts[0];
            Integer port = parts.length > 1 ? Integer.parseInt(parts[1]) : 80;

            ServiceResult service = new ServiceResult();
            service.setName(host);
            service.setPort(port);
            service.setProtocol(data.getProtocol() != null ? data.getProtocol() : "http");
            service.setWeight(100); // 默认权重

            services.add(service);
        }

        return services;
    }

    @Override
    public String createConsumer(
            Consumer consumer, ConsumerCredential credential, GatewayConfig config) {
        AdpAIGatewayConfig adpConfig = config.getAdpAIGatewayConfig();
        if (adpConfig == null) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "ADP AI Gateway配置缺失");
        }

        AdpAIGatewayClient client = new AdpAIGatewayClient(adpConfig);
        try {
            // 构建请求参数
            cn.hutool.json.JSONObject requestData = JSONUtil.createObj();
            requestData.set("authType", 5);
            requestData.set("apiKeyLocationType", "BEARER");

            // 从凭证中获取key
            if (credential.getApiKeyConfig() != null
                    && credential.getApiKeyConfig().getCredentials() != null
                    && !credential.getApiKeyConfig().getCredentials().isEmpty()) {
                String key = credential.getApiKeyConfig().getCredentials().get(0).getApiKey();
                requestData.set("key", key);
            }

            requestData.set("appName", consumer.getName());

            // 从 GatewayConfig 中获取 Gateway 实体，与 fetchMcpConfig 方法保持一致
            Gateway gateway = config.getGateway();
            if (gateway == null || gateway.getGatewayId() == null) {
                throw new BusinessException(ErrorCode.INVALID_PARAMETER, "网关实例ID缺失");
            }
            requestData.set("gwInstanceId", gateway.getGatewayId());

            String url = client.getFullUrl("/application/createApp");
            String requestBody = requestData.toString();
            HttpEntity<String> requestEntity = client.createRequestEntity(requestBody);

            log.info("Creating consumer in ADP gateway: url={}, requestBody={}", url, requestBody);

            ResponseEntity<String> response =
                    client.getRestTemplate()
                            .exchange(url, HttpMethod.POST, requestEntity, String.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                log.info("ADP gateway response: {}", response.getBody());
                // 对于ADP AI网关，返回的data就是appName，可以直接用于后续的MCP授权
                return extractConsumerIdFromResponse(response.getBody(), consumer.getName());
            }
            throw new BusinessException(
                    ErrorCode.GATEWAY_ERROR, "Failed to create consumer in ADP gateway");
        } catch (BusinessException e) {
            log.error("Business error creating consumer in ADP gateway", e);
            throw e;
        } catch (Exception e) {
            log.error("Error creating consumer in ADP gateway", e);
            throw new BusinessException(
                    ErrorCode.INTERNAL_ERROR,
                    "Error creating consumer in ADP gateway: " + e.getMessage());
        } finally {
            client.close();
        }
    }

    /**
     * 从响应中提取消费者ID 对于ADP AI网关，/application/createApp接口返回的data就是appName（应用名称）
     * 我们直接返回appName，这样在授权时可以直接使用
     */
    private String extractConsumerIdFromResponse(String responseBody, String defaultConsumerId) {
        try {
            cn.hutool.json.JSONObject responseJson = JSONUtil.parseObj(responseBody);
            // ADP AI网关的/application/createApp接口，成功时返回格式: {"code": 200, "data": "appName"}
            if (responseJson.getInt("code", 0) == 200 && responseJson.containsKey("data")) {
                Object dataObj = responseJson.get("data");
                if (dataObj != null) {
                    // data字段就是appName，直接返回
                    if (dataObj instanceof String) {
                        return (String) dataObj;
                    }
                    // 如果data是对象类型，则按原逻辑处理（兼容性考虑）
                    if (dataObj instanceof cn.hutool.json.JSONObject) {
                        cn.hutool.json.JSONObject data = (cn.hutool.json.JSONObject) dataObj;
                        if (data.containsKey("applicationId")) {
                            return data.getStr("applicationId");
                        }
                        // 如果没有applicationId字段，将整个data对象转为字符串
                        return data.toString();
                    }
                    // 其他类型直接转为字符串
                    return dataObj.toString();
                }
            }
            // 如果无法解析，使用应用名称作为fallback
            return defaultConsumerId; // 这里传入的是consumer.getName()
        } catch (Exception e) {
            log.warn("Failed to parse response body, using consumer name as fallback", e);
            return defaultConsumerId;
        }
    }

    @Override
    public void updateConsumer(
            String consumerId, ConsumerCredential credential, GatewayConfig config) {
        AdpAIGatewayConfig adpConfig = config.getAdpAIGatewayConfig();
        if (adpConfig == null) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "ADP AI Gateway配置缺失");
        }

        Gateway gateway = config.getGateway();
        if (gateway == null || gateway.getGatewayId() == null) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "网关实例ID缺失");
        }

        AdpAIGatewayClient client = new AdpAIGatewayClient(adpConfig);
        try {

            // 从凭据中提取API Key
            String apiKey = null;
            if (credential != null
                    && credential.getApiKeyConfig() != null
                    && credential.getApiKeyConfig().getCredentials() != null
                    && !credential.getApiKeyConfig().getCredentials().isEmpty()) {
                apiKey = credential.getApiKeyConfig().getCredentials().get(0).getApiKey();
            }

            String url = client.getFullUrl("/application/modifyApp");

            // 明确各参数含义，避免重复使用consumerId带来的混淆
            String appId = consumerId; // 应用ID，用于标识要更新的应用
            String appName = consumerId; // 应用名称，通常与appId保持一致
            String description = "Consumer managed by Portal"; // 语义化的描述信息
            Integer authType = 5; // 认证类型：API_KEY
            String authTypeName = "API_KEY"; // 认证类型名称
            Boolean enable = true; // 启用状态

            // 构建请求体
            cn.hutool.json.JSONObject requestData = JSONUtil.createObj();
            requestData.set("appId", appId);
            requestData.set("appName", appName);
            requestData.set("authType", authType);
            requestData.set("apiKeyLocationType", "BEARER");
            requestData.set("authTypeName", authTypeName);
            requestData.set("description", description);
            requestData.set("enable", enable);
            if (apiKey != null) {
                requestData.set("key", apiKey);
            }
            requestData.set("groups", Collections.singletonList("true"));
            requestData.set("gwInstanceId", gateway.getGatewayId());

            String requestBody = requestData.toString();
            HttpEntity<String> requestEntity = client.createRequestEntity(requestBody);

            log.info("Updating consumer in ADP gateway: url={}, requestBody={}", url, requestBody);

            ResponseEntity<String> response =
                    client.getRestTemplate()
                            .exchange(url, HttpMethod.POST, requestEntity, String.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                cn.hutool.json.JSONObject responseJson = JSONUtil.parseObj(response.getBody());
                Integer code = responseJson.getInt("code", 0);
                if (code != null && code == 200) {
                    log.info(
                            "Successfully updated consumer {} in ADP gateway instance {}",
                            consumerId,
                            gateway.getGatewayId());
                    return;
                }
                String message =
                        responseJson.getStr("message", responseJson.getStr("msg", "Unknown error"));
                throw new BusinessException(ErrorCode.GATEWAY_ERROR, "更新ADP网关消费者失败: " + message);
            }
            throw new BusinessException(
                    ErrorCode.GATEWAY_ERROR, "调用 ADP /application/modifyApp 失败");
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error(
                    "Error updating consumer {} in ADP gateway instance {}",
                    consumerId,
                    gateway != null ? gateway.getGatewayId() : "unknown",
                    e);
            throw new BusinessException(
                    ErrorCode.INTERNAL_ERROR, "更新ADP网关消费者异常: " + e.getMessage());
        } finally {
            client.close();
        }
    }

    @Override
    public void deleteConsumer(String consumerId, GatewayConfig config) {
        AdpAIGatewayConfig adpConfig = config.getAdpAIGatewayConfig();
        if (adpConfig == null) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "ADP AI Gateway配置缺失");
        }

        Gateway gateway = config.getGateway();
        if (gateway == null || gateway.getGatewayId() == null) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "网关实例ID缺失");
        }

        AdpAIGatewayClient client = new AdpAIGatewayClient(adpConfig);
        try {

            String url = client.getFullUrl("/application/deleteApp");
            String requestBody =
                    String.format(
                            "{\"appId\": \"%s\", \"gwInstanceId\": \"%s\"}",
                            consumerId, gateway.getGatewayId());
            HttpEntity<String> requestEntity = client.createRequestEntity(requestBody);

            log.info("Deleting consumer in ADP gateway: url={}, requestBody={}", url, requestBody);

            ResponseEntity<String> response =
                    client.getRestTemplate()
                            .exchange(url, HttpMethod.POST, requestEntity, String.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                cn.hutool.json.JSONObject responseJson = JSONUtil.parseObj(response.getBody());
                Integer code = responseJson.getInt("code", 0);
                if (code != null && code == 200) {
                    log.info(
                            "Successfully deleted consumer {} from ADP gateway instance {}",
                            consumerId,
                            gateway.getGatewayId());
                    return;
                }
                String message =
                        responseJson.getStr("message", responseJson.getStr("msg", "Unknown error"));
                throw new BusinessException(ErrorCode.GATEWAY_ERROR, "删除ADP网关消费者失败: " + message);
            }
            throw new BusinessException(
                    ErrorCode.GATEWAY_ERROR, "调用 ADP /application/deleteApp 失败");
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error(
                    "Error deleting consumer {} from ADP gateway instance {}",
                    consumerId,
                    gateway != null ? gateway.getGatewayId() : "unknown",
                    e);
            throw new BusinessException(
                    ErrorCode.INTERNAL_ERROR, "删除ADP网关消费者异常: " + e.getMessage());
        } finally {
            client.close();
        }
    }

    @Override
    public boolean isConsumerExists(String consumerId, GatewayConfig config) {
        AdpAIGatewayConfig adpConfig = config.getAdpAIGatewayConfig();
        if (adpConfig == null) {
            log.warn("ADP AI Gateway配置缺失，无法检查消费者存在性");
            return false;
        }

        AdpAIGatewayClient client = new AdpAIGatewayClient(adpConfig);
        try {
            // 从 GatewayConfig 中获取 Gateway 实体
            Gateway gateway = config.getGateway();
            if (gateway == null || gateway.getGatewayId() == null) {
                log.warn("网关实例ID缺失，无法检查消费者存在性");
                return false;
            }

            String url = client.getFullUrl("/application/getApp");
            String requestBody =
                    String.format(
                            "{\"%s\": \"%s\", \"%s\": \"%s\"}",
                            "gwInstanceId", gateway.getGatewayId(), "appId", consumerId);
            HttpEntity<String> requestEntity = client.createRequestEntity(requestBody);

            ResponseEntity<String> response =
                    client.getRestTemplate()
                            .exchange(url, HttpMethod.POST, requestEntity, String.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                cn.hutool.json.JSONObject responseJson = JSONUtil.parseObj(response.getBody());
                Integer code = responseJson.getInt("code", 0);
                // 如果返回200且有data，说明消费者存在
                return code == 200
                        && responseJson.containsKey("data")
                        && responseJson.get("data") != null;
            }
            return false;
        } catch (Exception e) {
            log.warn("检查ADP网关消费者存在性失败: consumerId={}", consumerId, e);
            return false;
        } finally {
            client.close();
        }
    }

    @Override
    public ConsumerAuthConfig authorizeConsumer(
            Gateway gateway, String consumerId, Object refConfig) {
        AdpAIGatewayConfig adpConfig = gateway.getAdpAIGatewayConfig();
        if (adpConfig == null) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "ADP AI Gateway配置缺失");
        }

        APIGRefConfig apigRefConfig = (APIGRefConfig) refConfig;
        if (apigRefConfig == null) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "APIGRefConfig配置缺失");
        }

        ConsumerAuthConfig result = null;

        // 如果是MCP Server配置
        if (apigRefConfig.getMcpServerName() != null) {
            result = authorizeMcpServerConsumer(gateway, consumerId, apigRefConfig);
        }

        // 如果是Model API配置
        if (apigRefConfig.getModelApiId() != null) {
            authorizeModelApiConsumer(gateway, consumerId, apigRefConfig);
            // Model API授权不需要返回特定的auth config
        }

        // 如果result为null，说明是Model API的情况，返回通用配置
        if (result == null) {
            result =
                    ConsumerAuthConfig.builder()
                            .adpAIAuthConfig(
                                    AdpAIAuthConfig.builder()
                                            .modelApiId(apigRefConfig.getModelApiId())
                                            .consumerId(consumerId)
                                            .gwInstanceId(gateway.getGatewayId())
                                            .build())
                            .build();
        }

        return result;
    }

    /**
     * 授权消费者访问MCP Server
     */
    private ConsumerAuthConfig authorizeMcpServerConsumer(
            Gateway gateway, String consumerId, APIGRefConfig apigRefConfig) {
        AdpAIGatewayConfig adpConfig = gateway.getAdpAIGatewayConfig();
        AdpAIGatewayClient client = new AdpAIGatewayClient(adpConfig);
        try {
            // 构建授权请求参数
            // 由于createConsumer返回的就是appName，所以consumerId就是应用名称
            cn.hutool.json.JSONObject requestData = JSONUtil.createObj();
            requestData.set("mcpServerName", apigRefConfig.getMcpServerName());
            requestData.set(
                    "consumers", Collections.singletonList(consumerId)); // consumerId就是appName
            requestData.set("gwInstanceId", gateway.getGatewayId());

            String url = client.getFullUrl("/mcpServer/addMcpServerConsumers");
            String requestBody = requestData.toString();
            HttpEntity<String> requestEntity = client.createRequestEntity(requestBody);

            log.info(
                    "Authorizing consumer to MCP server: url={}, requestBody={}", url, requestBody);

            ResponseEntity<String> response =
                    client.getRestTemplate()
                            .exchange(url, HttpMethod.POST, requestEntity, String.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                cn.hutool.json.JSONObject responseJson = JSONUtil.parseObj(response.getBody());
                Integer code = responseJson.getInt("code", 0);

                if (code == 200) {
                    log.info(
                            "Successfully authorized consumer {} to MCP server {}",
                            consumerId,
                            apigRefConfig.getMcpServerName());

                    // 构建授权配置返回结果
                    AdpAIAuthConfig authConfig =
                            AdpAIAuthConfig.builder()
                                    .mcpServerName(apigRefConfig.getMcpServerName())
                                    .consumerId(consumerId)
                                    .gwInstanceId(gateway.getGatewayId())
                                    .build();

                    return ConsumerAuthConfig.builder().adpAIAuthConfig(authConfig).build();
                } else {
                    String message =
                            responseJson.getStr(
                                    "message", responseJson.getStr("msg", "Unknown error"));
                    throw new BusinessException(
                            ErrorCode.GATEWAY_ERROR,
                            "Failed to authorize consumer to MCP server: " + message);
                }
            }
            throw new BusinessException(
                    ErrorCode.GATEWAY_ERROR, "Failed to authorize consumer to MCP server");
        } catch (BusinessException e) {
            log.error("Business error authorizing consumer to MCP server", e);
            throw e;
        } catch (Exception e) {
            log.error(
                    "Error authorizing consumer {} to MCP server {}",
                    consumerId,
                    apigRefConfig.getMcpServerName(),
                    e);
            throw new BusinessException(
                    ErrorCode.INTERNAL_ERROR,
                    "Error authorizing consumer to MCP server: " + e.getMessage());
        } finally {
            client.close();
        }
    }

    /**
     * 授权消费者访问Model API
     */
    private void authorizeModelApiConsumer(
            Gateway gateway, String consumerId, APIGRefConfig apigRefConfig) {
        AdpAIGatewayConfig adpConfig = gateway.getAdpAIGatewayConfig();
        AdpAIGatewayClient client = new AdpAIGatewayClient(adpConfig);
        try {
            // 构建Model API授权请求参数
            cn.hutool.json.JSONObject requestData = JSONUtil.createObj();
            requestData.set("gwInstanceId", gateway.getGatewayId());
            requestData.set("modelApiId", apigRefConfig.getModelApiId());
            requestData.set("consumerIds", Collections.singletonList(consumerId));

            String url = client.getFullUrl("/modelapi/batchGrantModelApi");
            String requestBody = requestData.toString();
            HttpEntity<String> requestEntity = client.createRequestEntity(requestBody);

            log.info("Authorizing consumer to Model API: url={}, requestBody={}", url, requestBody);

            ResponseEntity<String> response =
                    client.getRestTemplate()
                            .exchange(url, HttpMethod.POST, requestEntity, String.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                cn.hutool.json.JSONObject responseJson = JSONUtil.parseObj(response.getBody());
                Integer code = responseJson.getInt("code", 0);

                if (code == 200) {
                    log.info(
                            "Successfully authorized consumer {} to Model API {}",
                            consumerId,
                            apigRefConfig.getModelApiId());
                    return;
                } else {
                    String message =
                            responseJson.getStr(
                                    "message", responseJson.getStr("msg", "Unknown error"));
                    throw new BusinessException(
                            ErrorCode.GATEWAY_ERROR,
                            "Failed to authorize consumer to Model API: " + message);
                }
            }
            throw new BusinessException(
                    ErrorCode.GATEWAY_ERROR, "Failed to authorize consumer to Model API");
        } catch (BusinessException e) {
            log.error("Business error authorizing consumer to Model API", e);
            throw e;
        } catch (Exception e) {
            log.error(
                    "Error authorizing consumer {} to Model API {}",
                    consumerId,
                    apigRefConfig.getModelApiId(),
                    e);
            throw new BusinessException(
                    ErrorCode.INTERNAL_ERROR,
                    "Error authorizing consumer to Model API: " + e.getMessage());
        } finally {
            client.close();
        }
    }

    @Override
    public void revokeConsumerAuthorization(
            Gateway gateway, String consumerId, ConsumerAuthConfig authConfig) {
        AdpAIAuthConfig adpAIAuthConfig = authConfig.getAdpAIAuthConfig();
        if (adpAIAuthConfig == null) {
            log.warn("ADP AI 授权配置为空，无法撤销授权");
            return;
        }

        AdpAIGatewayConfig adpConfig = gateway.getAdpAIGatewayConfig();
        if (adpConfig == null) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "ADP AI Gateway配置缺失");
        }

        // 如果是MCP Server授权，使用原有逻辑
        if (adpAIAuthConfig.getMcpServerName() != null) {
            revokeMcpServerConsumerAuthorization(gateway, consumerId, adpAIAuthConfig);
        }

        // 如果是Model API授权，使用新逻辑
        if (adpAIAuthConfig.getModelApiId() != null) {
            revokeModelApiConsumerAuthorization(gateway, consumerId, adpAIAuthConfig);
        }
    }

    /**
     * 撤销MCP Server的消费者授权
     */
    private void revokeMcpServerConsumerAuthorization(
            Gateway gateway, String consumerId, AdpAIAuthConfig adpAIAuthConfig) {
        AdpAIGatewayConfig adpConfig = gateway.getAdpAIGatewayConfig();
        AdpAIGatewayClient client = new AdpAIGatewayClient(adpConfig);
        try {
            // 构建撤销授权请求参数
            // 由于createConsumer返回的就是appName，所以consumerId就是应用名称
            cn.hutool.json.JSONObject requestData = JSONUtil.createObj();
            requestData.set("mcpServerName", adpAIAuthConfig.getMcpServerName());
            requestData.set(
                    "consumers", Collections.singletonList(consumerId)); // consumerId就是appName
            requestData.set("gwInstanceId", gateway.getGatewayId());

            String url = client.getFullUrl("/mcpServer/deleteMcpServerConsumers");
            String requestBody = requestData.toString();
            HttpEntity<String> requestEntity = client.createRequestEntity(requestBody);

            log.info(
                    "Revoking consumer authorization from MCP server: url={}, requestBody={}",
                    url,
                    requestBody);

            ResponseEntity<String> response =
                    client.getRestTemplate()
                            .exchange(url, HttpMethod.POST, requestEntity, String.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                cn.hutool.json.JSONObject responseJson = JSONUtil.parseObj(response.getBody());
                Integer code = responseJson.getInt("code", 0);

                if (code == 200) {
                    log.info(
                            "Successfully revoked consumer {} authorization from MCP server {}",
                            consumerId,
                            adpAIAuthConfig.getMcpServerName());
                    return;
                }

                // 获取错误信息
                String message =
                        responseJson.getStr("message", responseJson.getStr("msg", "Unknown error"));

                // 如果是资源不存在（已被删除），只记录警告，不抛异常
                if (message != null
                        && (message.contains("not found")
                                || message.contains("不存在")
                                || message.contains("NotFound")
                                || code == 404)) {
                    log.warn(
                            "Consumer authorization already removed or not found: consumerId={},"
                                    + " mcpServer={}, message={}",
                            consumerId,
                            adpAIAuthConfig.getMcpServerName(),
                            message);
                    return;
                }

                // 其他错误抛出异常
                String errorMsg =
                        "Failed to revoke consumer authorization from MCP server: " + message;
                log.error(errorMsg);
                throw new BusinessException(ErrorCode.GATEWAY_ERROR, errorMsg);
            }

            throw new BusinessException(
                    ErrorCode.GATEWAY_ERROR,
                    "Failed to revoke consumer authorization, HTTP status: "
                            + response.getStatusCode());
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error(
                    "Error revoking consumer {} authorization from MCP server {}",
                    consumerId,
                    adpAIAuthConfig.getMcpServerName(),
                    e);
            throw new BusinessException(
                    ErrorCode.INTERNAL_ERROR,
                    "Error revoking consumer authorization: " + e.getMessage());
        } finally {
            client.close();
        }
    }

    /**
     * 撤销Model API的消费者授权
     */
    private void revokeModelApiConsumerAuthorization(
            Gateway gateway, String consumerId, AdpAIAuthConfig adpAIAuthConfig) {
        AdpAIGatewayConfig adpConfig = gateway.getAdpAIGatewayConfig();
        AdpAIGatewayClient client = new AdpAIGatewayClient(adpConfig);
        try {
            // 首先需要获取Model API的授权信息，以确定authId
            String authId =
                    getAuthIdForModelApi(
                            gateway, adpAIAuthConfig.getModelApiId(), consumerId, adpConfig);

            if (authId == null) {
                log.warn(
                        "No authId found for consumer {} and model API {}, skipping revocation",
                        consumerId,
                        adpAIAuthConfig.getModelApiId());
                return;
            }

            // 构建撤销Model API授权请求参数
            cn.hutool.json.JSONObject requestData = JSONUtil.createObj();
            requestData.set("gwInstanceId", gateway.getGatewayId());
            requestData.set("authId", authId);

            String url = client.getFullUrl("/modelapi/revokeModelApiGrant");
            String requestBody = requestData.toString();
            HttpEntity<String> requestEntity = client.createRequestEntity(requestBody);

            log.info(
                    "Revoking consumer authorization from Model API: url={}, requestBody={}",
                    url,
                    requestBody);

            ResponseEntity<String> response =
                    client.getRestTemplate()
                            .exchange(url, HttpMethod.POST, requestEntity, String.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                cn.hutool.json.JSONObject responseJson = JSONUtil.parseObj(response.getBody());
                Integer code = responseJson.getInt("code", 0);

                if (code == 200) {
                    log.info(
                            "Successfully revoked consumer {} authorization from Model API {}",
                            consumerId,
                            adpAIAuthConfig.getModelApiId());
                    return;
                }

                // 获取错误信息
                String message =
                        responseJson.getStr("message", responseJson.getStr("msg", "Unknown error"));

                // 如果是资源不存在（已被删除），只记录警告，不抛异常
                if (message != null
                        && (message.contains("not found")
                                || message.contains("不存在")
                                || message.contains("NotFound")
                                || code == 404)) {
                    log.warn(
                            "Consumer authorization already removed or not found: consumerId={},"
                                    + " modelApiId={}, message={}",
                            consumerId,
                            adpAIAuthConfig.getModelApiId(),
                            message);
                    return;
                }

                // 其他错误抛出异常
                String errorMsg =
                        "Failed to revoke consumer authorization from Model API: " + message;
                log.error(errorMsg);
                throw new BusinessException(ErrorCode.GATEWAY_ERROR, errorMsg);
            }

            throw new BusinessException(
                    ErrorCode.GATEWAY_ERROR,
                    "Failed to revoke consumer authorization from Model API, HTTP status: "
                            + response.getStatusCode());
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error(
                    "Error revoking consumer {} authorization from Model API {}",
                    consumerId,
                    adpAIAuthConfig.getModelApiId(),
                    e);
            throw new BusinessException(
                    ErrorCode.INTERNAL_ERROR,
                    "Error revoking consumer authorization from Model API: " + e.getMessage());
        } finally {
            client.close();
        }
    }

    /**
     * 获取Model API授权的authId
     * 使用 /modelapi/listModelApiConsumers 接口查询授权信息
     */
    private String getAuthIdForModelApi(
            Gateway gateway, String modelApiId, String consumerId, AdpAIGatewayConfig config) {
        AdpAIGatewayClient client = new AdpAIGatewayClient(config);
        try {
            // 使用正确的API来查询Model API的消费者授权信息
            String url = client.getFullUrl("/modelapi/listModelApiConsumers");

            // 构建请求体，根据您提供的API格式
            String requestBody =
                    String.format(
                            "{\"gwInstanceId\": \"%s\", \"modelApiId\": \"%s\", \"engineType\":"
                                    + " \"higress\", \"current\": 1, \"size\": 10}",
                            gateway.getGatewayId(), modelApiId);

            HttpEntity<String> requestEntity = client.createRequestEntity(requestBody);

            ResponseEntity<String> response =
                    client.getRestTemplate()
                            .exchange(url, HttpMethod.POST, requestEntity, String.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                cn.hutool.json.JSONObject responseJson = JSONUtil.parseObj(response.getBody());
                Integer code = responseJson.getInt("code", 0);

                if (code != null && code == 200) {
                    // 解析返回的授权信息，查找匹配的consumerId对应的authId
                    cn.hutool.json.JSONObject data = responseJson.getJSONObject("data");
                    if (data != null && data.containsKey("records")) {
                        cn.hutool.json.JSONArray records = data.getJSONArray("records");
                        if (records != null) {
                            for (int i = 0; i < records.size(); i++) {
                                cn.hutool.json.JSONObject record = records.getJSONObject(i);
                                String recordConsumerId = record.getStr("appId");
                                String authId = record.getStr("authId"); // 根据实际API返回结构，授权ID字段可能是id

                                if (consumerId.equals(recordConsumerId)) {
                                    return authId; // 找到匹配的authId
                                }
                            }
                        }
                    }
                }

                String msg =
                        responseJson.getStr("message", responseJson.getStr("msg", "Unknown error"));
                log.warn("Failed to get model API consumers for authId lookup: {}", msg);
            }

            log.warn("Failed to call /modelapi/listModelApiConsumers for authId lookup");

            // 如果上述方法失败，返回null
            return null;

        } catch (Exception e) {
            log.error("Error getting authId for model API", e);
            // 如果API调用失败，返回null
            return null;
        } finally {
            client.close();
        }
    }

    @Override
    public HttpApiApiInfo fetchAPI(Gateway gateway, String apiId) {
        return null;
    }

    @Override
    public GatewayType getGatewayType() {
        return GatewayType.ADP_AI_GATEWAY;
    }

    @Override
    public List<URI> fetchGatewayUris(Gateway gateway) {
        return Collections.emptyList();
    }

    @Override
    public PageResult<GatewayResult> fetchGateways(Object param, int page, int size) {
        if (!(param instanceof QueryAdpAIGatewayParam)) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "param");
        }
        return fetchGateways((QueryAdpAIGatewayParam) param, page, size);
    }

    public PageResult<GatewayResult> fetchGateways(
            QueryAdpAIGatewayParam param, int page, int size) {
        AdpAIGatewayConfig config = new AdpAIGatewayConfig();
        config.setBaseUrl(param.getBaseUrl());
        config.setPort(param.getPort());

        // 根据认证类型设置不同的认证信息
        if ("Seed".equals(param.getAuthType())) {
            if (param.getAuthSeed() == null || param.getAuthSeed().trim().isEmpty()) {
                throw new BusinessException(ErrorCode.INVALID_PARAMETER, "Seed认证方式下authSeed不能为空");
            }
            config.setAuthSeed(param.getAuthSeed());
        } else if ("Header".equals(param.getAuthType())) {
            if (param.getAuthHeaders() == null || param.getAuthHeaders().isEmpty()) {
                throw new BusinessException(
                        ErrorCode.INVALID_PARAMETER, "Header认证方式下authHeaders不能为空");
            }
            // 将authHeaders转换为配置
            List<AdpAIGatewayConfig.AuthHeader> configHeaders = new ArrayList<>();
            for (QueryAdpAIGatewayParam.AuthHeader paramHeader : param.getAuthHeaders()) {
                AdpAIGatewayConfig.AuthHeader configHeader = new AdpAIGatewayConfig.AuthHeader();
                configHeader.setKey(paramHeader.getKey());
                configHeader.setValue(paramHeader.getValue());
                configHeaders.add(configHeader);
            }
            config.setAuthHeaders(configHeaders);
        } else {
            throw new BusinessException(
                    ErrorCode.INVALID_PARAMETER, "不支持的认证类型: " + param.getAuthType());
        }

        AdpAIGatewayClient client = new AdpAIGatewayClient(config);
        try {
            String url = client.getFullUrl("/gatewayInstance/listInstances");
            String requestBody = String.format("{\"current\": %d, \"size\": %d}", page, size);
            HttpEntity<String> requestEntity = client.createRequestEntity(requestBody);

            ResponseEntity<AdpGatewayInstanceResult> response =
                    client.getRestTemplate()
                            .exchange(
                                    url,
                                    HttpMethod.POST,
                                    requestEntity,
                                    AdpGatewayInstanceResult.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                AdpGatewayInstanceResult result = response.getBody();
                if (result.getCode() == 200 && result.getData() != null) {
                    return convertToGatewayResult(result.getData(), page, size);
                }
                String msg = result.getMessage() != null ? result.getMessage() : result.getMsg();
                throw new BusinessException(ErrorCode.GATEWAY_ERROR, msg);
            }
            throw new BusinessException(ErrorCode.GATEWAY_ERROR, "Failed to call ADP gateway API");
        } catch (Exception e) {
            log.error("Error fetching ADP gateways", e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, e.getMessage());
        } finally {
            client.close();
        }
    }

    private PageResult<GatewayResult> convertToGatewayResult(
            AdpGatewayInstanceResult.AdpGatewayInstanceData data, int page, int size) {
        List<GatewayResult> gateways = new ArrayList<>();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        if (data.getRecords() != null) {
            for (AdpGatewayInstanceResult.AdpGatewayInstance instance : data.getRecords()) {
                LocalDateTime createTime = null;
                try {
                    if (instance.getCreateTime() != null) {
                        createTime = LocalDateTime.parse(instance.getCreateTime(), formatter);
                    }
                } catch (Exception e) {
                    log.warn("Failed to parse create time: {}", instance.getCreateTime(), e);
                }
                GatewayResult gateway =
                        GatewayResult.builder()
                                .gatewayId(instance.getGwInstanceId())
                                .gatewayName(instance.getName())
                                .gatewayType(GatewayType.ADP_AI_GATEWAY)
                                .createAt(createTime)
                                .build();
                gateways.add(gateway);
            }
        }
        return PageResult.of(gateways, page, size, data.getTotal() != null ? data.getTotal() : 0);
    }

    /**
     * 将协议字符串映射到AIProtocol枚举
     * OPENAI_COMPATIBLE映射到OPENAI，因为它们本质上是同一个协议
     */
    private String mapProtocol(String protocol) {
        if ("OPENAI_COMPATIBLE".equalsIgnoreCase(protocol)) {
            return "OpenAI/V1"; // 对应AIProtocol.OPENAI
        }
        return protocol;
    }

    // ==================== ADP AI 服务响应 DTO ====================

    @Data
    public static class AdpAiServiceListResult {
        private Integer code;
        private String msg;
        private String message;
        private AdpAiServiceData data;

        @Data
        public static class AdpAiServiceData {
            private Integer total;
            private Integer current;
            private Integer size;
            private List<AdpAiServiceItem> records;
        }

        @Data
        public static class AdpAiServiceItem {
            private String id; // 服务ID
            private String apiName; // 服务名称
            private String description; // 描述
            private String basePath; // 基础路径
            private List<String> pathList; // 路径列表
            private List<String> domainNameList; // 域名列表
            private String protocol; // 协议
            private String sceneType; // 场景类型
            // 根据实际接口补充其他字段
        }
    }

    @Data
    public static class AdpAiServiceDetailResult {
        private Integer code;
        private String msg;
        private String message;
        private AdpAiServiceDetail data;

        @Data
        public static class AdpAiServiceDetail {
            private String id;
            private String apiName;
            private String description;
            private String basePath; // 服务地址
            private List<String> pathList; // 路径列表
            private List<String> domainNameList; // 域名列表
            private String protocol; // 协议类型
            private String sceneType; // 场景类型
            // 根据实际接口补充其他字段
        }
    }

    @Data
    public static class AdpMcpServerDetailResult {
        private Integer code;
        private String msg;
        private String message;
        private AdpMcpServerDetail data;

        @Data
        public static class AdpMcpServerDetail {
            private String gwInstanceId;
            private String name;
            private String description;
            private List<String> domains;
            private List<Service> services;
            private ConsumerAuthInfo consumerAuthInfo;
            private String rawConfigurations;
            private String type;
            private String dsn;
            private String dbType;
            private String upstreamPathPrefix;

            @Data
            public static class Service {
                private String name;
                private Integer port;
                private String version;
                private Integer weight;
            }

            @Data
            public static class ConsumerAuthInfo {
                private String type;
                private Boolean enable;
                private List<String> allowedConsumers;
            }
        }
    }
}
