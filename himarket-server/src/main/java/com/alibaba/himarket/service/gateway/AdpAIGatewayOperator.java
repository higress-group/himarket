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

import cn.hutool.core.util.StrUtil;
import com.alibaba.himarket.core.exception.BusinessException;
import com.alibaba.himarket.core.exception.ErrorCode;
import com.alibaba.himarket.dto.params.gateway.QueryAdpAIGatewayParam;
import com.alibaba.himarket.dto.result.agent.AgentAPIResult;
import com.alibaba.himarket.dto.result.common.DomainResult;
import com.alibaba.himarket.dto.result.common.PageResult;
import com.alibaba.himarket.dto.result.consumer.CredentialContext;
import com.alibaba.himarket.dto.result.gateway.AdpGatewayInstanceResult;
import com.alibaba.himarket.dto.result.gateway.GatewayResult;
import com.alibaba.himarket.dto.result.httpapi.APIResult;
import com.alibaba.himarket.dto.result.httpapi.HttpRouteResult;
import com.alibaba.himarket.dto.result.httpapi.ServiceResult;
import com.alibaba.himarket.dto.result.mcp.AdpMcpServerListResult;
import com.alibaba.himarket.dto.result.mcp.GatewayMcpServerResult;
import com.alibaba.himarket.dto.result.mcp.McpConfigResult;
import com.alibaba.himarket.dto.result.model.AIGWModelAPIResult;
import com.alibaba.himarket.dto.result.model.GatewayModelAPIResult;
import com.alibaba.himarket.dto.result.model.ModelConfigResult;
import com.alibaba.himarket.entity.Consumer;
import com.alibaba.himarket.entity.ConsumerCredential;
import com.alibaba.himarket.entity.Gateway;
import com.alibaba.himarket.entity.ProductRef;
import com.alibaba.himarket.service.gateway.client.AdpAIGatewayClient;
import com.alibaba.himarket.support.consumer.AdpAIAuthConfig;
import com.alibaba.himarket.support.consumer.ConsumerAuthConfig;
import com.alibaba.himarket.support.enums.GatewayType;
import com.alibaba.himarket.support.enums.McpFromType;
import com.alibaba.himarket.support.enums.ProductType;
import com.alibaba.himarket.support.gateway.AdpAIGatewayConfig;
import com.alibaba.himarket.support.gateway.GatewayConfig;
import com.alibaba.himarket.support.product.APIGRefConfig;
import com.alibaba.himarket.utils.JsonUtil;
import com.aliyun.sdk.service.apig20240327.models.HttpApiApiInfo;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

/**
 * ADP AI gateway operator.
 */
@Service
@Slf4j
public class AdpAIGatewayOperator extends GatewayOperator {

    private static final String LOCALIZED_NOT_FOUND_MESSAGE = "\u4e0d\u5b58\u5728";

    private final Map configGeneratorRegistry;

    public AdpAIGatewayOperator(Map configGeneratorRegistry) {
        super();
        this.configGeneratorRegistry = configGeneratorRegistry;
    }

    @Override
    public PageResult<APIResult> fetchHTTPAPIs(Gateway gateway, int page, int size) {
        return null;
    }

    @Override
    public PageResult<APIResult> fetchRESTAPIs(Gateway gateway, int page, int size) {
        return null;
    }

    @Override
    public PageResult<? extends GatewayMcpServerResult> fetchMcpServers(
            Gateway gateway, int page, int size) {
        AdpAIGatewayConfig config = gateway.getAdpAIGatewayConfig();
        if (config == null) {
            throw new BusinessException(
                    ErrorCode.INVALID_PARAMETER, "ADP AI gateway config is missing");
        }

        AdpAIGatewayClient client = new AdpAIGatewayClient(config);
        try {
            String url = client.getFullUrl("/mcpServer/listMcpServers");
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
                    List<GatewayMcpServerResult> items = new ArrayList<>();
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
                    ErrorCode.GATEWAY_ERROR, "Failed to call ADP /mcpServer/listMcpServers");
        } catch (Exception e) {
            log.error(
                    "Failed to fetch ADP MCP servers, dependency=ADP, operation=listMcpServers,"
                            + " gatewayId={}, page={}, size={}, errorType={}, errorMessage={}",
                    gateway.getGatewayId(),
                    page,
                    size,
                    e.getClass().getSimpleName(),
                    e.getMessage(),
                    e);
            throw new BusinessException(
                    ErrorCode.INTERNAL_ERROR,
                    e,
                    "Failed to fetch ADP MCP servers: " + e.getMessage());
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
            throw new BusinessException(
                    ErrorCode.INVALID_PARAMETER, "ADP AI gateway config is missing");
        }

        AdpAIGatewayClient client = new AdpAIGatewayClient(config);
        try {
            String url = client.getFullUrl("/modelapi/listModelApis");
            String requestBody =
                    String.format(
                            "{\"size\": %d, \"currentPage\": %d, \"gwInstanceId\": \"%s\"}",
                            size, page, gateway.getGatewayId());
            HttpEntity<String> requestEntity = client.createRequestEntity(requestBody);

            ResponseEntity<AdpAiServiceListResult> response =
                    client.getRestTemplate()
                            .exchange(
                                    url,
                                    HttpMethod.POST,
                                    requestEntity,
                                    AdpAiServiceListResult.class);

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
                                        .toList();
                    }

                    int total =
                            result.getData().getTotal() != null ? result.getData().getTotal() : 0;
                    return PageResult.of(items, page, size, total);
                }
                String msg = result.getMessage() != null ? result.getMessage() : result.getMsg();
                throw new BusinessException(ErrorCode.GATEWAY_ERROR, msg);
            }
            throw new BusinessException(
                    ErrorCode.GATEWAY_ERROR, "Failed to call ADP /modelapi/listModelApis");
        } catch (Exception e) {
            log.error(
                    "Failed to fetch ADP model APIs, dependency=ADP, operation=listModelApis,"
                            + " gatewayId={}, page={}, size={}, errorType={}, errorMessage={}",
                    gateway.getGatewayId(),
                    page,
                    size,
                    e.getClass().getSimpleName(),
                    e.getMessage(),
                    e);
            throw new BusinessException(
                    ErrorCode.INTERNAL_ERROR,
                    e,
                    "Failed to fetch ADP model APIs: " + e.getMessage());
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
            throw new BusinessException(
                    ErrorCode.INVALID_PARAMETER, "ADP AI gateway config is missing");
        }

        APIGRefConfig apigRefConfig = (APIGRefConfig) conf;
        if (apigRefConfig == null || apigRefConfig.getMcpServerName() == null) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "MCP server name is missing");
        }

        AdpAIGatewayClient client = new AdpAIGatewayClient(config);
        try {
            String url = client.getFullUrl("/mcpServer/getMcpServer");

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
                    ErrorCode.GATEWAY_ERROR, "Failed to call ADP /mcpServer/getMcpServer");
        } catch (Exception e) {
            log.error(
                    "Failed to fetch ADP MCP config, dependency=ADP, operation=getMcpServer,"
                            + " gatewayId={}, mcpServerName={}, errorType={}, errorMessage={}",
                    gateway.getGatewayId(),
                    apigRefConfig.getMcpServerName(),
                    e.getClass().getSimpleName(),
                    e.getMessage(),
                    e);
            throw new BusinessException(
                    ErrorCode.INTERNAL_ERROR,
                    e,
                    "Failed to fetch ADP MCP config: " + e.getMessage());
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
            throw new BusinessException(
                    ErrorCode.INVALID_PARAMETER, "ADP AI gateway config is missing");
        }

        APIGRefConfig refConfig = (APIGRefConfig) conf;
        if (refConfig == null || refConfig.getModelApiId() == null) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "Model API ID is missing");
        }

        AdpAIGatewayClient client = new AdpAIGatewayClient(config);
        try {
            String url = client.getFullUrl("/modelapi/getModelApi");

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
            throw new BusinessException(
                    ErrorCode.GATEWAY_ERROR, "Failed to call ADP /modelapi/getModelApi");

        } catch (Exception e) {
            log.error(
                    "Failed to fetch ADP model config, dependency=ADP, operation=getModelApi,"
                            + " gatewayId={}, modelApiId={}, errorType={}, errorMessage={}",
                    gateway.getGatewayId(),
                    refConfig.getModelApiId(),
                    e.getClass().getSimpleName(),
                    e.getMessage(),
                    e);
            throw new BusinessException(
                    ErrorCode.INTERNAL_ERROR,
                    e,
                    "Failed to fetch ADP model config: " + e.getMessage());
        } finally {
            client.close();
        }
    }

    @Override
    public CredentialContext fetchApiCredential(
            Gateway gateway, ProductType productType, ProductRef productRef) {
        return null;
    }

    /**
     * Converts ADP MCP server details to MCP configuration JSON.
     */
    private String convertToMCPConfig(
            AdpMcpServerDetailResult.AdpMcpServerDetail data, AdpAIGatewayConfig config) {
        McpConfigResult mcpConfig = new McpConfigResult();
        mcpConfig.setMcpServerName(data.getName());

        McpConfigResult.McpServerConfig serverConfig = new McpConfigResult.McpServerConfig();
        serverConfig.setPath("/mcp-servers/" + data.getName());

        List<DomainResult> domains = getGatewayAccessDomains(data.getGwInstanceId(), config);
        if (domains != null && !domains.isEmpty()) {
            serverConfig.setDomains(domains);
        } else {
            if (data.getServices() != null && !data.getServices().isEmpty()) {
                List<DomainResult> fallbackDomains =
                        data.getServices().stream()
                                .map(
                                        service ->
                                                DomainResult.builder()
                                                        .domain(service.getName())
                                                        .port(service.getPort())
                                                        .protocol("http")
                                                        .build())
                                .toList();
                serverConfig.setDomains(fallbackDomains);
            }
        }

        mcpConfig.setMcpServerConfig(serverConfig);

        mcpConfig.setTools(data.getRawConfigurations());

        mcpConfig.setFromType(
                "OPEN_API".equalsIgnoreCase(data.getType())
                        ? McpFromType.HTTP_TO_MCP
                        : McpFromType.NATIVE_MCP);

        McpConfigResult.McpMetadata meta = new McpConfigResult.McpMetadata();
        meta.setSource(GatewayType.ADP_AI_GATEWAY.name());
        mcpConfig.setMeta(meta);

        return JsonUtil.toJson(mcpConfig);
    }

    /**
     * Fetches gateway access information and builds domain entries.
     */
    private List<DomainResult> getGatewayAccessDomains(
            String gwInstanceId, AdpAIGatewayConfig config) {
        AdpAIGatewayClient client = new AdpAIGatewayClient(config);
        try {
            String url = client.getFullUrl("/gatewayInstance/getInstanceInfo");
            String requestBody = String.format("{\"gwInstanceId\": \"%s\"}", gwInstanceId);
            HttpEntity<String> requestEntity = client.createRequestEntity(requestBody);

            ResponseEntity<String> response =
                    client.getRestTemplate()
                            .exchange(url, HttpMethod.POST, requestEntity, String.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                ObjectNode root = JsonUtil.readObjectNode(response.getBody());
                int code = root.path("code").asInt();
                if (code == 200 && root.has("data")) {
                    ObjectNode dataObj = (ObjectNode) root.get("data");
                    if (dataObj != null && dataObj.has("accessMode")) {
                        ArrayNode arr = (ArrayNode) dataObj.get("accessMode");
                        List<AdpGatewayInstanceResult.AccessMode> accessModes =
                                JsonUtil.convertToList(
                                        arr, AdpGatewayInstanceResult.AccessMode.class);
                        return buildDomainsFromAccessModes(accessModes);
                    }
                    log.warn(
                            "Gateway instance access mode is missing, dependency=ADP,"
                                    + " operation=getInstanceInfo, gatewayId={}",
                            gwInstanceId);
                    return null;
                }
                String message = root.has("message") ? root.get("message").asText() : null;
                if (message == null || message.isEmpty()) {
                    message = root.has("msg") ? root.get("msg").asText() : null;
                }
                log.warn(
                        "Failed to fetch gateway access info, dependency=ADP,"
                                + " operation=getInstanceInfo, gatewayId={}, errorMessage={}",
                        gwInstanceId,
                        message);
                return null;
            }
            log.warn(
                    "Failed to call gateway access API, dependency=ADP,"
                            + " operation=getInstanceInfo, gatewayId={}, httpStatus={}",
                    gwInstanceId,
                    response.getStatusCode());
            return null;
        } catch (Exception e) {
            log.error(
                    "Failed to fetch gateway access info, dependency=ADP,"
                            + " operation=getInstanceInfo, gatewayId={}, errorType={},"
                            + " errorMessage={}",
                    gwInstanceId,
                    e.getClass().getSimpleName(),
                    e.getMessage(),
                    e);
            return null;
        } finally {
            client.close();
        }
    }

    /**
     * Builds domain entries from gateway instance access information.
     */
    private List<DomainResult> buildDomainsFromAccessInfo(
            AdpGatewayInstanceResult.AdpGatewayInstanceData data) {
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

        // LoadBalancer: externalIps:80
        if ("LoadBalancer".equalsIgnoreCase(accessMode.getAccessModeType())) {
            if (accessMode.getExternalIps() != null && !accessMode.getExternalIps().isEmpty()) {
                for (String externalIp : accessMode.getExternalIps()) {
                    if (externalIp == null || externalIp.isEmpty()) {
                        continue;
                    }
                    DomainResult domain =
                            DomainResult.builder()
                                    .domain(externalIp)
                                    .port(80)
                                    .protocol("http")
                                    .build();
                    domains.add(domain);
                }
            }
        }

        // NodePort: ips + ports to ip:nodePort
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
                                            .domain(ip)
                                            .port(Integer.parseInt(nodePort))
                                            .protocol("http")
                                            .build();
                            domains.add(domain);
                        }
                    }
                }
            }
        }

        // Fallback: only externalIps to :80
        if (domains.isEmpty()
                && accessMode.getExternalIps() != null
                && !accessMode.getExternalIps().isEmpty()) {
            for (String externalIp : accessMode.getExternalIps()) {
                if (externalIp == null || externalIp.isEmpty()) {
                    continue;
                }
                DomainResult domain =
                        DomainResult.builder().domain(externalIp).port(80).protocol("http").build();
                domains.add(domain);
            }
        }

        return domains;
    }

    /**
     * Converts an ADP model API item to the gateway model API result.
     */
    private GatewayModelAPIResult convertToModelAPIResult(
            AdpAiServiceListResult.AdpAiServiceItem item) {
        return AIGWModelAPIResult.builder()
                .modelApiId(item.getId())
                .modelApiName(item.getApiName())
                .build();
    }

    /**
     * Converts ADP model details to ModelConfigResult JSON.
     */
    private String convertToModelConfigJson(
            AdpAiServiceDetailResult.AdpAiServiceDetail data,
            Gateway gateway,
            AdpAIGatewayConfig config) {

        List<DomainResult> domains = null;
        if (data.getDomainNameList() != null && !data.getDomainNameList().isEmpty()) {
            domains =
                    data.getDomainNameList().stream()
                            .map(
                                    domain ->
                                            DomainResult.builder()
                                                    .domain(domain)
                                                    .protocol("http")
                                                    .build())
                            .toList();
        } else {
            domains = getGatewayAccessDomains(gateway.getGatewayId(), config);
        }

        ModelConfigResult.ModelAPIConfig apiConfig =
                ModelConfigResult.ModelAPIConfig.builder()
                        .aiProtocols(Collections.singletonList(mapProtocol(data.getProtocol())))
                        .modelCategory(mapSceneType(data.getSceneType()))
                        .routes(buildRoutesFromAdpService(data, domains))
                        .services(buildServicesFromAdpService(data))
                        .build();

        ModelConfigResult modelConfig = new ModelConfigResult();
        modelConfig.setModelAPIConfig(apiConfig);

        return JsonUtil.toJson(modelConfig);
    }

    /**
     * Builds route entries from ADP service data.
     */
    private List<HttpRouteResult> buildRoutesFromAdpService(
            AdpAiServiceDetailResult.AdpAiServiceDetail data, List<DomainResult> domains) {
        if (data.getMethodPathList() == null || data.getMethodPathList().isEmpty()) {
            // Some MODEL_API responses omit methodPathList. Use basePath plus the default OpenAI
            // chat completion path so BaseUrlExtractor can still resolve the base URL.
            if (domains != null && !domains.isEmpty()) {
                String defaultPath =
                        (data.getBasePath() != null ? data.getBasePath() : "")
                                + "/v1/chat/completions";
                HttpRouteResult route = new HttpRouteResult();
                route.setDomains(domains);
                route.setMatch(
                        HttpRouteResult.RouteMatchResult.builder()
                                .methods(Collections.singletonList("POST"))
                                .path(
                                        HttpRouteResult.RouteMatchPath.builder()
                                                .value(defaultPath)
                                                .type("Exact")
                                                .build())
                                .build());
                route.setBuiltin(false);
                return Collections.singletonList(route);
            }
            return Collections.emptyList();
        }

        List<HttpRouteResult> routes = new ArrayList<>();
        for (AdpAiServiceDetailResult.MethodPath methodPath : data.getMethodPathList()) {
            HttpRouteResult route = new HttpRouteResult();

            route.setDomains(domains);

            String fullPath =
                    data.getBasePath() != null
                            ? data.getBasePath() + methodPath.getPath()
                            : methodPath.getPath();
            HttpRouteResult.RouteMatchResult matchResult =
                    HttpRouteResult.RouteMatchResult.builder()
                            .methods(
                                    Collections.singletonList(
                                            methodPath.getMethod() != null
                                                    ? methodPath.getMethod()
                                                    : "POST"))
                            .path(
                                    HttpRouteResult.RouteMatchPath.builder()
                                            .value(fullPath)
                                            .type("Exact")
                                            .build())
                            .build();
            route.setMatch(matchResult);

            route.setDescription(data.getDescription());

            route.setBuiltin(false);

            routes.add(route);
        }

        return routes;
    }

    /**
     * Builds service entries from ADP service data.
     */
    private List<ServiceResult> buildServicesFromAdpService(
            AdpAiServiceDetailResult.AdpAiServiceDetail data) {
        if (data.getDomainNameList() == null || data.getDomainNameList().isEmpty()) {
            return Collections.emptyList();
        }

        List<ServiceResult> services = new ArrayList<>();
        for (String domainName : data.getDomainNameList()) {
            String[] parts = domainName.split(":");
            String host = parts[0];
            Integer port = parts.length > 1 ? Integer.parseInt(parts[1]) : 80;

            ServiceResult service = new ServiceResult();
            service.setName(host);
            service.setPort(port);
            service.setProtocol(data.getProtocol() != null ? data.getProtocol() : "http");
            service.setWeight(100);

            services.add(service);
        }

        return services;
    }

    @Override
    public String createConsumer(
            Consumer consumer, ConsumerCredential credential, GatewayConfig config) {
        AdpAIGatewayConfig adpConfig = config.getAdpAIGatewayConfig();
        if (adpConfig == null) {
            throw new BusinessException(
                    ErrorCode.INVALID_PARAMETER, "ADP AI gateway config is missing");
        }

        // Include a developer suffix to avoid consumer name collisions across developers.
        String mark =
                consumer.getDeveloperId()
                        .substring(Math.max(0, consumer.getDeveloperId().length() - 8));
        String gwConsumerName = StrUtil.format("{}-{}", consumer.getName(), mark);

        AdpAIGatewayClient client = new AdpAIGatewayClient(adpConfig);
        try {
            ObjectNode requestData = JsonUtil.createObjectNode();
            requestData.put("authType", 5);
            requestData.put("apiKeyLocationType", "BEARER");

            if (credential.getApiKeyConfig() != null
                    && credential.getApiKeyConfig().getCredentials() != null
                    && !credential.getApiKeyConfig().getCredentials().isEmpty()) {
                String key = credential.getApiKeyConfig().getCredentials().get(0).getApiKey();
                requestData.put("key", key);
            }

            requestData.put("appName", gwConsumerName);
            requestData.put("gwInstanceId", config.getGatewayId());

            String url = client.getFullUrl("/application/createApp");
            String requestBody = requestData.toString();
            HttpEntity<String> requestEntity = client.createRequestEntity(requestBody);

            log.info(
                    "Creating ADP gateway consumer, dependency=ADP, operation=createApp,"
                            + " gatewayId={}, consumerName={}, authType={}",
                    config.getGatewayId(),
                    gwConsumerName,
                    requestData.path("authType").asInt());

            ResponseEntity<String> response =
                    client.getRestTemplate()
                            .exchange(url, HttpMethod.POST, requestEntity, String.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                log.info(
                        "ADP gateway create consumer response received, dependency=ADP,"
                                + " operation=createApp, gatewayId={}, consumerName={},"
                                + " httpStatus={}",
                        config.getGatewayId(),
                        gwConsumerName,
                        response.getStatusCode());
                return extractConsumerIdFromResponse(response.getBody(), gwConsumerName);
            }
            throw new BusinessException(
                    ErrorCode.GATEWAY_ERROR, "Failed to create consumer in ADP gateway");
        } catch (BusinessException e) {
            log.error(
                    "Failed to create ADP gateway consumer, dependency=ADP, operation=createApp,"
                            + " gatewayId={}, consumerName={}, errorType={}, errorMessage={}",
                    config.getGatewayId(),
                    gwConsumerName,
                    e.getClass().getSimpleName(),
                    e.getMessage(),
                    e);
            throw e;
        } catch (Exception e) {
            log.error(
                    "Failed to create ADP gateway consumer, dependency=ADP, operation=createApp,"
                            + " gatewayId={}, consumerName={}, errorType={}, errorMessage={}",
                    config.getGatewayId(),
                    gwConsumerName,
                    e.getClass().getSimpleName(),
                    e.getMessage(),
                    e);
            throw new BusinessException(
                    ErrorCode.INTERNAL_ERROR,
                    "Failed to create ADP gateway consumer: " + e.getMessage());
        } finally {
            client.close();
        }
    }

    /**
     * Extracts the consumer ID from an ADP create-app response.
     */
    private String extractConsumerIdFromResponse(String responseBody, String defaultConsumerId) {
        try {
            ObjectNode responseJson = JsonUtil.readObjectNode(responseBody);
            if (responseJson.path("code").asInt(0) == 200 && responseJson.has("data")) {
                JsonNode dataNode = responseJson.get("data");
                if (dataNode != null && !dataNode.isNull()) {
                    if (dataNode.isTextual()) {
                        return dataNode.asText();
                    }
                    if (dataNode.isObject()) {
                        ObjectNode data = (ObjectNode) dataNode;
                        if (data.has("applicationId")) {
                            return data.path("applicationId").asText(null);
                        }
                        return data.toString();
                    }
                    return dataNode.toString();
                }
            }
            return defaultConsumerId;
        } catch (Exception e) {
            log.warn(
                    "Failed to parse ADP create consumer response, dependency=ADP,"
                            + " operation=createApp, fallbackConsumerId={}, errorType={},"
                            + " errorMessage={}",
                    defaultConsumerId,
                    e.getClass().getSimpleName(),
                    e.getMessage(),
                    e);
            return defaultConsumerId;
        }
    }

    @Override
    public void updateConsumer(
            String consumerId, ConsumerCredential credential, GatewayConfig config) {
        AdpAIGatewayConfig adpConfig = config.getAdpAIGatewayConfig();
        if (adpConfig == null) {
            throw new BusinessException(
                    ErrorCode.INVALID_PARAMETER, "ADP AI gateway config is missing");
        }

        AdpAIGatewayClient client = new AdpAIGatewayClient(adpConfig);
        try {

            String apiKey = null;
            if (credential != null
                    && credential.getApiKeyConfig() != null
                    && credential.getApiKeyConfig().getCredentials() != null
                    && !credential.getApiKeyConfig().getCredentials().isEmpty()) {
                apiKey = credential.getApiKeyConfig().getCredentials().get(0).getApiKey();
            }

            String url = client.getFullUrl("/application/modifyApp");

            String appId = consumerId;
            String appName = consumerId;
            String description = "Consumer managed by Portal";
            Integer authType = 5;
            String authTypeName = "API_KEY";
            Boolean enable = true;

            ObjectNode requestData = JsonUtil.createObjectNode();
            requestData.put("appId", appId);
            requestData.put("appName", appName);
            requestData.put("authType", authType);
            requestData.put("apiKeyLocationType", "BEARER");
            requestData.put("authTypeName", authTypeName);
            requestData.put("description", description);
            requestData.put("enable", enable);
            if (apiKey != null) {
                requestData.put("key", apiKey);
            }
            ArrayNode groupsArr = JsonUtil.createArray();
            groupsArr.add("true");
            requestData.set("groups", groupsArr);
            requestData.put("gwInstanceId", config.getGatewayId());

            String requestBody = requestData.toString();
            HttpEntity<String> requestEntity = client.createRequestEntity(requestBody);

            log.info(
                    "Updating ADP gateway consumer, dependency=ADP, operation=modifyApp,"
                            + " gatewayId={}, consumerId={}",
                    config.getGatewayId(),
                    consumerId);

            ResponseEntity<String> response =
                    client.getRestTemplate()
                            .exchange(url, HttpMethod.POST, requestEntity, String.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                ObjectNode responseJson = JsonUtil.readObjectNode(response.getBody());
                int code = responseJson.path("code").asInt(0);
                if (code == 200) {
                    log.info(
                            "Updated ADP gateway consumer, dependency=ADP, operation=modifyApp,"
                                    + " gatewayId={}, consumerId={}",
                            config.getGatewayId(),
                            consumerId);
                    return;
                }
                String message =
                        responseJson.has("message") ? responseJson.get("message").asText() : null;
                if (message == null || message.isEmpty()) {
                    message =
                            responseJson.has("msg")
                                    ? responseJson.get("msg").asText()
                                    : "Unknown error";
                }
                throw new BusinessException(
                        ErrorCode.GATEWAY_ERROR,
                        "Failed to update ADP gateway consumer: " + message);
            }
            throw new BusinessException(
                    ErrorCode.GATEWAY_ERROR, "Failed to call ADP /application/modifyApp");
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error(
                    "Failed to update ADP gateway consumer, dependency=ADP, operation=modifyApp,"
                            + " gatewayId={}, consumerId={}, errorType={}, errorMessage={}",
                    config.getGatewayId(),
                    consumerId,
                    e.getClass().getSimpleName(),
                    e.getMessage(),
                    e);
            throw new BusinessException(
                    ErrorCode.INTERNAL_ERROR,
                    "Failed to update ADP gateway consumer: " + e.getMessage());
        } finally {
            client.close();
        }
    }

    @Override
    public void deleteConsumer(String consumerId, GatewayConfig config) {
        AdpAIGatewayConfig adpConfig = config.getAdpAIGatewayConfig();
        if (adpConfig == null) {
            throw new BusinessException(
                    ErrorCode.INVALID_PARAMETER, "ADP AI gateway config is missing");
        }

        AdpAIGatewayClient client = new AdpAIGatewayClient(adpConfig);
        try {

            String url = client.getFullUrl("/application/deleteApp");
            String requestBody =
                    String.format(
                            "{\"appId\": \"%s\", \"gwInstanceId\": \"%s\"}",
                            consumerId, config.getGatewayId());
            HttpEntity<String> requestEntity = client.createRequestEntity(requestBody);

            log.info(
                    "Deleting ADP gateway consumer, dependency=ADP, operation=deleteApp,"
                            + " gatewayId={}, consumerId={}",
                    config.getGatewayId(),
                    consumerId);

            ResponseEntity<String> response =
                    client.getRestTemplate()
                            .exchange(url, HttpMethod.POST, requestEntity, String.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                ObjectNode responseJson = JsonUtil.readObjectNode(response.getBody());
                int code = responseJson.path("code").asInt(0);
                if (code == 200) {
                    log.info(
                            "Deleted ADP gateway consumer, dependency=ADP, operation=deleteApp,"
                                    + " gatewayId={}, consumerId={}",
                            config.getGatewayId(),
                            consumerId);
                    return;
                }
                String message =
                        responseJson.has("message") ? responseJson.get("message").asText() : null;
                if (message == null || message.isEmpty()) {
                    message =
                            responseJson.has("msg")
                                    ? responseJson.get("msg").asText()
                                    : "Unknown error";
                }
                throw new BusinessException(
                        ErrorCode.GATEWAY_ERROR,
                        "Failed to delete ADP gateway consumer: " + message);
            }
            throw new BusinessException(
                    ErrorCode.GATEWAY_ERROR, "Failed to call ADP /application/deleteApp");
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error(
                    "Failed to delete ADP gateway consumer, dependency=ADP, operation=deleteApp,"
                            + " gatewayId={}, consumerId={}, errorType={}, errorMessage={}",
                    config.getGatewayId(),
                    consumerId,
                    e.getClass().getSimpleName(),
                    e.getMessage(),
                    e);
            throw new BusinessException(
                    ErrorCode.INTERNAL_ERROR,
                    "Failed to delete ADP gateway consumer: " + e.getMessage());
        } finally {
            client.close();
        }
    }

    @Override
    public boolean isConsumerExists(String consumerId, GatewayConfig config) {
        AdpAIGatewayConfig adpConfig = config.getAdpAIGatewayConfig();
        if (adpConfig == null) {
            log.warn(
                    "ADP gateway config is missing, dependency=ADP, operation=getApp,"
                            + " consumerId={}",
                    consumerId);
            return false;
        }

        AdpAIGatewayClient client = new AdpAIGatewayClient(adpConfig);
        try {

            String url = client.getFullUrl("/application/getApp");
            String requestBody =
                    String.format(
                            "{\"%s\": \"%s\", \"%s\": \"%s\"}",
                            "gwInstanceId", config.getGatewayId(), "appId", consumerId);
            HttpEntity<String> requestEntity = client.createRequestEntity(requestBody);

            ResponseEntity<String> response =
                    client.getRestTemplate()
                            .exchange(url, HttpMethod.POST, requestEntity, String.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                ObjectNode responseJson = JsonUtil.readObjectNode(response.getBody());
                int code = responseJson.path("code").asInt(0);
                JsonNode dataNode = responseJson.get("data");
                return code == 200 && dataNode != null && !dataNode.isNull();
            }
            return false;
        } catch (Exception e) {
            log.warn(
                    "Failed to check whether ADP gateway consumer exists, dependency=ADP,"
                            + " operation=getApp, gatewayId={}, consumerId={}, errorType={},"
                            + " errorMessage={}",
                    config.getGatewayId(),
                    consumerId,
                    e.getClass().getSimpleName(),
                    e.getMessage(),
                    e);
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
            throw new BusinessException(
                    ErrorCode.INVALID_PARAMETER, "ADP AI gateway config is missing");
        }

        APIGRefConfig apigRefConfig = (APIGRefConfig) refConfig;
        if (apigRefConfig == null) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "APIGRefConfig is missing");
        }

        ConsumerAuthConfig result = null;

        if (apigRefConfig.getMcpServerName() != null) {
            result = authorizeMcpServerConsumer(gateway, consumerId, apigRefConfig);
        }

        if (apigRefConfig.getModelApiId() != null) {
            authorizeModelApiConsumer(gateway, consumerId, apigRefConfig);
        }

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
     * Authorizes a consumer to access an MCP server.
     */
    private ConsumerAuthConfig authorizeMcpServerConsumer(
            Gateway gateway, String consumerId, APIGRefConfig apigRefConfig) {
        AdpAIGatewayConfig adpConfig = gateway.getAdpAIGatewayConfig();
        AdpAIGatewayClient client = new AdpAIGatewayClient(adpConfig);
        try {
            ObjectNode requestData = JsonUtil.createObjectNode();
            requestData.put("mcpServerName", apigRefConfig.getMcpServerName());
            ArrayNode consumersArr = JsonUtil.createArray();
            consumersArr.add(consumerId);
            requestData.set("consumers", consumersArr);
            requestData.put("gwInstanceId", gateway.getGatewayId());

            String url = client.getFullUrl("/mcpServer/addMcpServerConsumers");
            String requestBody = requestData.toString();
            HttpEntity<String> requestEntity = client.createRequestEntity(requestBody);

            log.info(
                    "Authorizing ADP gateway consumer to MCP server, dependency=ADP,"
                            + " operation=addMcpServerConsumers, gatewayId={}, consumerId={},"
                            + " mcpServerName={}",
                    gateway.getGatewayId(),
                    consumerId,
                    apigRefConfig.getMcpServerName());

            ResponseEntity<String> response =
                    client.getRestTemplate()
                            .exchange(url, HttpMethod.POST, requestEntity, String.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                ObjectNode responseJson = JsonUtil.readObjectNode(response.getBody());
                int code = responseJson.path("code").asInt(0);

                if (code == 200) {
                    log.info(
                            "Authorized ADP gateway consumer to MCP server, dependency=ADP,"
                                + " operation=addMcpServerConsumers, gatewayId={}, consumerId={},"
                                + " mcpServerName={}",
                            gateway.getGatewayId(),
                            consumerId,
                            apigRefConfig.getMcpServerName());

                    AdpAIAuthConfig authConfig =
                            AdpAIAuthConfig.builder()
                                    .mcpServerName(apigRefConfig.getMcpServerName())
                                    .consumerId(consumerId)
                                    .gwInstanceId(gateway.getGatewayId())
                                    .build();

                    return ConsumerAuthConfig.builder().adpAIAuthConfig(authConfig).build();
                } else {
                    String message =
                            responseJson.has("message")
                                    ? responseJson.get("message").asText()
                                    : null;
                    if (message == null || message.isEmpty()) {
                        message =
                                responseJson.has("msg")
                                        ? responseJson.get("msg").asText()
                                        : "Unknown error";
                    }
                    throw new BusinessException(
                            ErrorCode.GATEWAY_ERROR,
                            "Failed to authorize consumer to MCP server: " + message);
                }
            }
            throw new BusinessException(
                    ErrorCode.GATEWAY_ERROR, "Failed to authorize consumer to MCP server");
        } catch (BusinessException e) {
            log.error(
                    "Failed to authorize ADP gateway consumer to MCP server, dependency=ADP,"
                            + " operation=addMcpServerConsumers, gatewayId={}, consumerId={},"
                            + " mcpServerName={}, errorType={}, errorMessage={}",
                    gateway.getGatewayId(),
                    consumerId,
                    apigRefConfig.getMcpServerName(),
                    e.getClass().getSimpleName(),
                    e.getMessage(),
                    e);
            throw e;
        } catch (Exception e) {
            log.error(
                    "Failed to authorize ADP gateway consumer to MCP server, dependency=ADP,"
                            + " operation=addMcpServerConsumers, gatewayId={}, consumerId={},"
                            + " mcpServerName={}, errorType={}, errorMessage={}",
                    gateway.getGatewayId(),
                    consumerId,
                    apigRefConfig.getMcpServerName(),
                    e.getClass().getSimpleName(),
                    e.getMessage(),
                    e);
            throw new BusinessException(
                    ErrorCode.INTERNAL_ERROR,
                    "Failed to authorize consumer to MCP server: " + e.getMessage());
        } finally {
            client.close();
        }
    }

    /**
     * Authorizes a consumer to access a Model API.
     */
    private void authorizeModelApiConsumer(
            Gateway gateway, String consumerId, APIGRefConfig apigRefConfig) {
        AdpAIGatewayConfig adpConfig = gateway.getAdpAIGatewayConfig();
        AdpAIGatewayClient client = new AdpAIGatewayClient(adpConfig);
        try {
            ObjectNode requestData = JsonUtil.createObjectNode();
            requestData.put("gwInstanceId", gateway.getGatewayId());
            requestData.put("modelApiId", apigRefConfig.getModelApiId());
            ArrayNode consumerIdsArr = JsonUtil.createArray();
            consumerIdsArr.add(consumerId);
            requestData.set("consumerIds", consumerIdsArr);

            String url = client.getFullUrl("/modelapi/batchGrantModelApi");
            String requestBody = requestData.toString();
            HttpEntity<String> requestEntity = client.createRequestEntity(requestBody);

            log.info(
                    "Authorizing ADP gateway consumer to Model API, dependency=ADP,"
                            + " operation=batchGrantModelApi, gatewayId={}, consumerId={},"
                            + " modelApiId={}",
                    gateway.getGatewayId(),
                    consumerId,
                    apigRefConfig.getModelApiId());

            ResponseEntity<String> response =
                    client.getRestTemplate()
                            .exchange(url, HttpMethod.POST, requestEntity, String.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                ObjectNode responseJson = JsonUtil.readObjectNode(response.getBody());
                int code = responseJson.path("code").asInt(0);

                if (code == 200) {
                    log.info(
                            "Authorized ADP gateway consumer to Model API, dependency=ADP,"
                                    + " operation=batchGrantModelApi, gatewayId={}, consumerId={},"
                                    + " modelApiId={}",
                            gateway.getGatewayId(),
                            consumerId,
                            apigRefConfig.getModelApiId());
                    return;
                } else {
                    String message =
                            responseJson.has("message")
                                    ? responseJson.get("message").asText()
                                    : null;
                    if (message == null || message.isEmpty()) {
                        message =
                                responseJson.has("msg")
                                        ? responseJson.get("msg").asText()
                                        : "Unknown error";
                    }
                    throw new BusinessException(
                            ErrorCode.GATEWAY_ERROR,
                            "Failed to authorize consumer to Model API: " + message);
                }
            }
            throw new BusinessException(
                    ErrorCode.GATEWAY_ERROR, "Failed to authorize consumer to Model API");
        } catch (BusinessException e) {
            log.error(
                    "Failed to authorize ADP gateway consumer to Model API, dependency=ADP,"
                            + " operation=batchGrantModelApi, gatewayId={}, consumerId={},"
                            + " modelApiId={}, errorType={}, errorMessage={}",
                    gateway.getGatewayId(),
                    consumerId,
                    apigRefConfig.getModelApiId(),
                    e.getClass().getSimpleName(),
                    e.getMessage(),
                    e);
            throw e;
        } catch (Exception e) {
            log.error(
                    "Failed to authorize ADP gateway consumer to Model API, dependency=ADP,"
                            + " operation=batchGrantModelApi, gatewayId={}, consumerId={},"
                            + " modelApiId={}, errorType={}, errorMessage={}",
                    gateway.getGatewayId(),
                    consumerId,
                    apigRefConfig.getModelApiId(),
                    e.getClass().getSimpleName(),
                    e.getMessage(),
                    e);
            throw new BusinessException(
                    ErrorCode.INTERNAL_ERROR,
                    "Failed to authorize consumer to Model API: " + e.getMessage());
        } finally {
            client.close();
        }
    }

    @Override
    public void revokeConsumerAuthorization(
            Gateway gateway, String consumerId, ConsumerAuthConfig authConfig) {
        AdpAIAuthConfig adpAIAuthConfig = authConfig.getAdpAIAuthConfig();
        if (adpAIAuthConfig == null) {
            log.warn(
                    "ADP authorization config is empty, dependency=ADP,"
                            + " operation=revokeConsumerAuthorization, gatewayId={}, consumerId={}",
                    gateway.getGatewayId(),
                    consumerId);
            return;
        }

        AdpAIGatewayConfig adpConfig = gateway.getAdpAIGatewayConfig();
        if (adpConfig == null) {
            throw new BusinessException(
                    ErrorCode.INVALID_PARAMETER, "ADP AI gateway config is missing");
        }

        if (adpAIAuthConfig.getMcpServerName() != null) {
            revokeMcpServerConsumerAuthorization(gateway, consumerId, adpAIAuthConfig);
        }

        if (adpAIAuthConfig.getModelApiId() != null) {
            revokeModelApiConsumerAuthorization(gateway, consumerId, adpAIAuthConfig);
        }
    }

    /**
     * Revokes a consumer authorization from an MCP server.
     */
    private void revokeMcpServerConsumerAuthorization(
            Gateway gateway, String consumerId, AdpAIAuthConfig adpAIAuthConfig) {
        AdpAIGatewayConfig adpConfig = gateway.getAdpAIGatewayConfig();
        AdpAIGatewayClient client = new AdpAIGatewayClient(adpConfig);
        try {
            ObjectNode requestData = JsonUtil.createObjectNode();
            requestData.put("mcpServerName", adpAIAuthConfig.getMcpServerName());
            ArrayNode consumersArr = JsonUtil.createArray();
            consumersArr.add(consumerId);
            requestData.set("consumers", consumersArr);
            requestData.put("gwInstanceId", gateway.getGatewayId());

            String url = client.getFullUrl("/mcpServer/deleteMcpServerConsumers");
            String requestBody = requestData.toString();
            HttpEntity<String> requestEntity = client.createRequestEntity(requestBody);

            log.info(
                    "Revoking ADP gateway consumer authorization from MCP server, dependency=ADP,"
                            + " operation=deleteMcpServerConsumers, gatewayId={}, consumerId={},"
                            + " mcpServerName={}",
                    gateway.getGatewayId(),
                    consumerId,
                    adpAIAuthConfig.getMcpServerName());

            ResponseEntity<String> response =
                    client.getRestTemplate()
                            .exchange(url, HttpMethod.POST, requestEntity, String.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                ObjectNode responseJson = JsonUtil.readObjectNode(response.getBody());
                int code = responseJson.path("code").asInt(0);

                if (code == 200) {
                    log.info(
                            "Revoked ADP gateway consumer authorization from MCP server,"
                                    + " dependency=ADP, operation=deleteMcpServerConsumers,"
                                    + " gatewayId={}, consumerId={}, mcpServerName={}",
                            gateway.getGatewayId(),
                            consumerId,
                            adpAIAuthConfig.getMcpServerName());
                    return;
                }

                String message =
                        responseJson.has("message") ? responseJson.get("message").asText() : null;
                if (message == null || message.isEmpty()) {
                    message =
                            responseJson.has("msg")
                                    ? responseJson.get("msg").asText()
                                    : "Unknown error";
                }

                // ADP can return localized not-found messages when the authorization was already
                // removed. Treat these responses as idempotent success.
                if (message != null
                        && (message.contains("not found")
                                || message.contains(LOCALIZED_NOT_FOUND_MESSAGE)
                                || message.contains("NotFound")
                                || code == 404)) {
                    log.warn(
                            "ADP MCP authorization already removed or not found, dependency=ADP,"
                                    + " operation=deleteMcpServerConsumers, gatewayId={},"
                                    + " consumerId={}, mcpServerName={}, errorMessage={}",
                            gateway.getGatewayId(),
                            consumerId,
                            adpAIAuthConfig.getMcpServerName(),
                            message);
                    return;
                }

                String errorMsg =
                        "Failed to revoke consumer authorization from MCP server: " + message;
                log.error(
                        "Failed to revoke ADP MCP authorization, dependency=ADP,"
                            + " operation=deleteMcpServerConsumers, gatewayId={}, consumerId={},"
                            + " mcpServerName={}, errorMessage={}",
                        gateway.getGatewayId(),
                        consumerId,
                        adpAIAuthConfig.getMcpServerName(),
                        message);
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
                    "Failed to revoke ADP MCP authorization, dependency=ADP,"
                            + " operation=deleteMcpServerConsumers, gatewayId={}, consumerId={},"
                            + " mcpServerName={}, errorType={}, errorMessage={}",
                    gateway.getGatewayId(),
                    consumerId,
                    adpAIAuthConfig.getMcpServerName(),
                    e.getClass().getSimpleName(),
                    e.getMessage(),
                    e);
            throw new BusinessException(
                    ErrorCode.INTERNAL_ERROR,
                    "Failed to revoke consumer authorization: " + e.getMessage());
        } finally {
            client.close();
        }
    }

    /**
     * Revokes a consumer authorization from a Model API.
     */
    private void revokeModelApiConsumerAuthorization(
            Gateway gateway, String consumerId, AdpAIAuthConfig adpAIAuthConfig) {
        AdpAIGatewayConfig adpConfig = gateway.getAdpAIGatewayConfig();
        AdpAIGatewayClient client = new AdpAIGatewayClient(adpConfig);
        try {
            String authId =
                    getAuthIdForModelApi(
                            gateway, adpAIAuthConfig.getModelApiId(), consumerId, adpConfig);

            if (authId == null) {
                log.warn(
                        "No authId found for ADP model API authorization, dependency=ADP,"
                                + " operation=listModelApiConsumers, gatewayId={}, consumerId={},"
                                + " modelApiId={}",
                        gateway.getGatewayId(),
                        consumerId,
                        adpAIAuthConfig.getModelApiId());
                return;
            }

            ObjectNode requestData = JsonUtil.createObjectNode();
            requestData.put("gwInstanceId", gateway.getGatewayId());
            requestData.put("authId", authId);

            String url = client.getFullUrl("/modelapi/revokeModelApiGrant");
            String requestBody = requestData.toString();
            HttpEntity<String> requestEntity = client.createRequestEntity(requestBody);

            log.info(
                    "Revoking ADP gateway consumer authorization from Model API, dependency=ADP,"
                            + " operation=revokeModelApiGrant, gatewayId={}, consumerId={},"
                            + " modelApiId={}",
                    gateway.getGatewayId(),
                    consumerId,
                    adpAIAuthConfig.getModelApiId());

            ResponseEntity<String> response =
                    client.getRestTemplate()
                            .exchange(url, HttpMethod.POST, requestEntity, String.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                ObjectNode responseJson = JsonUtil.readObjectNode(response.getBody());
                int code = responseJson.path("code").asInt(0);

                if (code == 200) {
                    log.info(
                            "Revoked ADP gateway consumer authorization from Model API,"
                                    + " dependency=ADP, operation=revokeModelApiGrant,"
                                    + " gatewayId={}, consumerId={}, modelApiId={}",
                            gateway.getGatewayId(),
                            consumerId,
                            adpAIAuthConfig.getModelApiId());
                    return;
                }

                String message =
                        responseJson.has("message") ? responseJson.get("message").asText() : null;
                if (message == null || message.isEmpty()) {
                    message =
                            responseJson.has("msg")
                                    ? responseJson.get("msg").asText()
                                    : "Unknown error";
                }

                // ADP can return localized not-found messages when the authorization was already
                // removed. Treat these responses as idempotent success.
                if (message != null
                        && (message.contains("not found")
                                || message.contains(LOCALIZED_NOT_FOUND_MESSAGE)
                                || message.contains("NotFound")
                                || code == 404)) {
                    log.warn(
                            "ADP model API authorization already removed or not found,"
                                    + " dependency=ADP, operation=revokeModelApiGrant,"
                                    + " gatewayId={}, consumerId={}, modelApiId={},"
                                    + " errorMessage={}",
                            gateway.getGatewayId(),
                            consumerId,
                            adpAIAuthConfig.getModelApiId(),
                            message);
                    return;
                }

                String errorMsg =
                        "Failed to revoke consumer authorization from Model API: " + message;
                log.error(
                        "Failed to revoke ADP model API authorization, dependency=ADP,"
                                + " operation=revokeModelApiGrant, gatewayId={}, consumerId={},"
                                + " modelApiId={}, errorMessage={}",
                        gateway.getGatewayId(),
                        consumerId,
                        adpAIAuthConfig.getModelApiId(),
                        message);
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
                    "Failed to revoke ADP model API authorization, dependency=ADP,"
                            + " operation=revokeModelApiGrant, gatewayId={}, consumerId={},"
                            + " modelApiId={}, errorType={}, errorMessage={}",
                    gateway.getGatewayId(),
                    consumerId,
                    adpAIAuthConfig.getModelApiId(),
                    e.getClass().getSimpleName(),
                    e.getMessage(),
                    e);
            throw new BusinessException(
                    ErrorCode.INTERNAL_ERROR,
                    "Failed to revoke consumer authorization from Model API: " + e.getMessage());
        } finally {
            client.close();
        }
    }

    /**
     * Looks up the authId for a Model API consumer authorization.
     */
    private String getAuthIdForModelApi(
            Gateway gateway, String modelApiId, String consumerId, AdpAIGatewayConfig config) {
        AdpAIGatewayClient client = new AdpAIGatewayClient(config);
        try {
            String url = client.getFullUrl("/modelapi/listModelApiConsumers");

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
                ObjectNode responseJson = JsonUtil.readObjectNode(response.getBody());
                int code = responseJson.path("code").asInt(0);

                if (code == 200) {
                    ObjectNode data = (ObjectNode) responseJson.get("data");
                    if (data != null && data.has("records")) {
                        ArrayNode records = (ArrayNode) data.get("records");
                        if (records != null) {
                            for (int i = 0; i < records.size(); i++) {
                                ObjectNode record = (ObjectNode) records.get(i);
                                String recordConsumerId = record.path("appId").asText(null);
                                String authId = record.path("authId").asText(null);

                                if (consumerId.equals(recordConsumerId)) {
                                    return authId;
                                }
                            }
                        }
                    }
                }

                String msg =
                        responseJson.has("message") ? responseJson.get("message").asText() : null;
                if (msg == null || msg.isEmpty()) {
                    msg =
                            responseJson.has("msg")
                                    ? responseJson.get("msg").asText()
                                    : "Unknown error";
                }
                log.warn(
                        "Failed to get ADP model API consumers for authId lookup,"
                                + " dependency=ADP, operation=listModelApiConsumers, gatewayId={},"
                                + " modelApiId={}, consumerId={}, errorMessage={}",
                        gateway.getGatewayId(),
                        modelApiId,
                        consumerId,
                        msg);
            }

            log.warn(
                    "Failed to call ADP model API consumer lookup, dependency=ADP,"
                            + " operation=listModelApiConsumers, gatewayId={}, modelApiId={},"
                            + " consumerId={}",
                    gateway.getGatewayId(),
                    modelApiId,
                    consumerId);

            return null;

        } catch (Exception e) {
            log.error(
                    "Failed to get ADP model API authId, dependency=ADP,"
                            + " operation=listModelApiConsumers, gatewayId={}, modelApiId={},"
                            + " consumerId={}, errorType={}, errorMessage={}",
                    gateway.getGatewayId(),
                    modelApiId,
                    consumerId,
                    e.getClass().getSimpleName(),
                    e.getMessage(),
                    e);
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

        if ("Seed".equals(param.getAuthType())) {
            if (param.getAuthSeed() == null || param.getAuthSeed().trim().isEmpty()) {
                throw new BusinessException(
                        ErrorCode.INVALID_PARAMETER,
                        "authSeed is required for Seed authentication");
            }
            config.setAuthSeed(param.getAuthSeed());
        } else if ("Header".equals(param.getAuthType())) {
            if (param.getAuthHeaders() == null || param.getAuthHeaders().isEmpty()) {
                throw new BusinessException(
                        ErrorCode.INVALID_PARAMETER,
                        "authHeaders are required for Header authentication");
            }
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
                    ErrorCode.INVALID_PARAMETER,
                    "Unsupported authentication type: " + param.getAuthType());
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
            log.error(
                    "Failed to fetch ADP gateways, dependency=ADP, operation=listInstances,"
                            + " page={}, size={}, errorType={}, errorMessage={}",
                    page,
                    size,
                    e.getClass().getSimpleName(),
                    e.getMessage(),
                    e);
            throw new BusinessException(
                    ErrorCode.INTERNAL_ERROR, e, "Failed to fetch ADP gateways: " + e.getMessage());
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
                    log.warn(
                            "Failed to parse ADP gateway create time, dependency=ADP,"
                                    + " operation=listInstances, gatewayId={}, createTime={},"
                                    + " errorType={}, errorMessage={}",
                            instance.getGwInstanceId(),
                            instance.getCreateTime(),
                            e.getClass().getSimpleName(),
                            e.getMessage(),
                            e);
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
     * Maps ADP protocol values to HiMarket AI protocol values.
     */
    private String mapProtocol(String protocol) {
        if ("OPENAI_COMPATIBLE".equalsIgnoreCase(protocol)) {
            return "OpenAI/V1";
        }
        return protocol;
    }

    /**
     * Maps ADP scene types to HiMarket model categories.
     */
    private String mapSceneType(String sceneType) {
        if (sceneType == null) {
            return null;
        }
        return switch (sceneType) {
            case "TEXT_GENERATION" -> "TEXT";
            case "IMAGE_GENERATION" -> "Image";
            case "VIDEO_GENERATION" -> "Video";
            default -> sceneType;
        };
    }

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
            private String id;
            private String apiName;
            private String description;
            private String basePath;
            private List<String> pathList;
            private List<String> domainNameList;
            private String protocol;
            private String sceneType;
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
            private String basePath;
            private Boolean basePathRemove;
            private List<MethodPath> methodPathList;
            private List<String> domainNameList;
            private String protocol;
            private String sceneType;
        }

        @Data
        public static class MethodPath {
            private String path;
            private String method;
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
