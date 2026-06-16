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
import com.alibaba.himarket.dto.params.apsara.CreateAppRequest;
import com.alibaba.himarket.dto.params.apsara.ModifyAppRequest;
import com.alibaba.himarket.dto.params.gateway.QueryApsaraGatewayParam;
import com.alibaba.himarket.dto.result.agent.AgentAPIResult;
import com.alibaba.himarket.dto.result.apsara.AddMcpServerConsumersResponse;
import com.alibaba.himarket.dto.result.apsara.BatchDeleteAppResponse;
import com.alibaba.himarket.dto.result.apsara.BatchGrantModelApiResponse;
import com.alibaba.himarket.dto.result.apsara.CreateAppResponse;
import com.alibaba.himarket.dto.result.apsara.DeleteMcpServerConsumersResponse;
import com.alibaba.himarket.dto.result.apsara.GetInstanceInfoResponse;
import com.alibaba.himarket.dto.result.apsara.GetMcpServerResponse;
import com.alibaba.himarket.dto.result.apsara.GetModelApiResponse;
import com.alibaba.himarket.dto.result.apsara.ListAppsByGwInstanceIdResponse;
import com.alibaba.himarket.dto.result.apsara.ListInstancesResponse;
import com.alibaba.himarket.dto.result.apsara.ListMcpServersResponse;
import com.alibaba.himarket.dto.result.apsara.ListModelApiConsumersResponse;
import com.alibaba.himarket.dto.result.apsara.ListModelApisResponse;
import com.alibaba.himarket.dto.result.apsara.ModifyAppResponse;
import com.alibaba.himarket.dto.result.apsara.RevokeModelApiGrantResponse;
import com.alibaba.himarket.dto.result.common.DomainResult;
import com.alibaba.himarket.dto.result.common.PageResult;
import com.alibaba.himarket.dto.result.consumer.CredentialContext;
import com.alibaba.himarket.dto.result.gateway.GatewayResult;
import com.alibaba.himarket.dto.result.httpapi.APIResult;
import com.alibaba.himarket.dto.result.httpapi.HttpRouteResult;
import com.alibaba.himarket.dto.result.httpapi.ServiceResult;
import com.alibaba.himarket.dto.result.mcp.AdpMcpServerResult;
import com.alibaba.himarket.dto.result.mcp.GatewayMcpServerResult;
import com.alibaba.himarket.dto.result.mcp.McpConfigResult;
import com.alibaba.himarket.dto.result.model.AIGWModelAPIResult;
import com.alibaba.himarket.dto.result.model.GatewayModelAPIResult;
import com.alibaba.himarket.dto.result.model.ModelConfigResult;
import com.alibaba.himarket.entity.Consumer;
import com.alibaba.himarket.entity.ConsumerCredential;
import com.alibaba.himarket.entity.Gateway;
import com.alibaba.himarket.entity.ProductRef;
import com.alibaba.himarket.service.gateway.client.ApsaraGatewayClient;
import com.alibaba.himarket.support.consumer.AdpAIAuthConfig;
import com.alibaba.himarket.support.consumer.ConsumerAuthConfig;
import com.alibaba.himarket.support.enums.GatewayType;
import com.alibaba.himarket.support.enums.McpFromType;
import com.alibaba.himarket.support.enums.McpProtocolType;
import com.alibaba.himarket.support.enums.ProductType;
import com.alibaba.himarket.support.gateway.ApsaraGatewayConfig;
import com.alibaba.himarket.support.gateway.GatewayConfig;
import com.alibaba.himarket.support.product.APIGRefConfig;
import com.alibaba.himarket.utils.JsonUtil;
import com.aliyun.sdk.service.apig20240327.models.HttpApiApiInfo;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class ApsaraGatewayOperator extends GatewayOperator<ApsaraGatewayClient> {

    private static final String LOCALIZED_NOT_FOUND_MESSAGE = "\u4e0d\u5b58\u5728";

    @Override
    public PageResult<APIResult> fetchHTTPAPIs(Gateway gateway, int page, int size) {
        throw new UnsupportedOperationException(
                "Apsara gateway not implemented for HTTP APIs listing");
    }

    @Override
    public PageResult<APIResult> fetchRESTAPIs(Gateway gateway, int page, int size) {
        throw new UnsupportedOperationException(
                "Apsara gateway not implemented for REST APIs listing");
    }

    @Override
    public PageResult<? extends GatewayMcpServerResult> fetchMcpServers(
            Gateway gateway, int page, int size) {
        ApsaraGatewayConfig cfg = gateway.getApsaraGatewayConfig();
        if (cfg == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Apsara gateway config is null");
        }

        ApsaraGatewayClient client = new ApsaraGatewayClient(cfg);
        try {
            ListMcpServersResponse response =
                    client.listMcpServers(gateway.getGatewayId(), page, size);

            if (response.getBody() == null) {
                return PageResult.of(new ArrayList<>(), page, size, 0);
            }

            ListMcpServersResponse.ResponseData data = response.getBody().getData();

            if (data == null) {
                return PageResult.of(new ArrayList<>(), page, size, 0);
            }

            int total = data.getTotal() != null ? data.getTotal() : 0;

            List<GatewayMcpServerResult> items = new ArrayList<>();
            if (data.getRecords() != null) {
                items =
                        data.getRecords().stream()
                                .map(
                                        record ->
                                                (GatewayMcpServerResult)
                                                        AdpMcpServerResult.fromSdkRecord(record))
                                .toList();
            }

            return PageResult.of(items, page, size, total);
        } catch (Exception e) {
            log.error(
                    "Failed to fetch Apsara MCP servers, dependency=ApsaraGateway,"
                            + " operation=listMcpServers, gatewayId={}, page={}, size={},"
                            + " errorType={}, errorMessage={}",
                    gateway.getGatewayId(),
                    page,
                    size,
                    e.getClass().getSimpleName(),
                    e.getMessage(),
                    e);
            throw new BusinessException(
                    ErrorCode.INTERNAL_ERROR,
                    e,
                    "Failed to fetch Apsara MCP servers: " + e.getMessage());
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
        ApsaraGatewayConfig cfg = gateway.getApsaraGatewayConfig();
        if (cfg == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Apsara gateway config is null");
        }

        ApsaraGatewayClient client = new ApsaraGatewayClient(cfg);

        try {
            ListModelApisResponse response =
                    client.listModelApis(gateway.getGatewayId(), page, size);
            if (response == null || response.getBody() == null) {
                return PageResult.of(new ArrayList<>(), page, size, 0);
            }

            ListModelApisResponse.ResponseData data = response.getBody().getData();

            int total = data.getTotal() != null ? data.getTotal() : 0;

            List<AIGWModelAPIResult> list = new ArrayList<>();
            if (data.getRecords() != null) {
                for (ListModelApisResponse.Record record : data.getRecords()) {
                    AIGWModelAPIResult aigwModelAPIResult =
                            AIGWModelAPIResult.builder()
                                    .modelApiId(record.getId())
                                    .modelApiName(record.getApiName())
                                    .build();
                    list.add(aigwModelAPIResult);
                }
            }

            return PageResult.of(list, page, size, total);
        } catch (Exception e) {
            log.error(
                    "Failed to fetch Apsara model APIs, dependency=ApsaraGateway,"
                            + " operation=listModelApis, gatewayId={}, page={}, size={},"
                            + " errorType={}, errorMessage={}",
                    gateway.getGatewayId(),
                    page,
                    size,
                    e.getClass().getSimpleName(),
                    e.getMessage(),
                    e);
            throw new BusinessException(
                    ErrorCode.INTERNAL_ERROR,
                    e,
                    "Failed to fetch Apsara model APIs: " + e.getMessage());
        } finally {
            client.close();
        }
    }

    @Override
    public String fetchAPIConfig(Gateway gateway, Object config) {
        throw new UnsupportedOperationException(
                "Apsara gateway not implemented for API config export");
    }

    @Override
    public String fetchMcpConfig(Gateway gateway, Object conf) {
        ApsaraGatewayConfig config = gateway.getApsaraGatewayConfig();
        if (config == null) {
            throw new BusinessException(
                    ErrorCode.INVALID_PARAMETER, "Apsara gateway config is missing");
        }

        APIGRefConfig apigRefConfig = (APIGRefConfig) conf;
        if (apigRefConfig == null || apigRefConfig.getMcpServerName() == null) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "MCP server name is missing");
        }

        ApsaraGatewayClient client = new ApsaraGatewayClient(config);
        try {
            GetMcpServerResponse response =
                    client.getMcpServer(gateway.getGatewayId(), apigRefConfig.getMcpServerName());

            if (response.getBody() == null || response.getBody().getData() == null) {
                throw new BusinessException(ErrorCode.GATEWAY_ERROR, "MCP server does not exist");
            }

            GetMcpServerResponse.ResponseData data = response.getBody().getData();

            return convertToMCPConfig(data, gateway.getGatewayId(), config);
        } catch (Exception e) {
            log.error(
                    "Failed to fetch Apsara MCP config, dependency=ApsaraGateway,"
                            + " operation=getMcpServer, gatewayId={}, mcpServerName={},"
                            + " errorType={}, errorMessage={}",
                    gateway.getGatewayId(),
                    apigRefConfig.getMcpServerName(),
                    e.getClass().getSimpleName(),
                    e.getMessage(),
                    e);
            throw new BusinessException(
                    ErrorCode.INTERNAL_ERROR,
                    e,
                    "Failed to fetch Apsara MCP config: " + e.getMessage());
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
        ApsaraGatewayConfig config = gateway.getApsaraGatewayConfig();
        if (config == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Apsara gateway config is null");
        }
        APIGRefConfig refConfig = (APIGRefConfig) conf;
        if (refConfig == null || refConfig.getModelApiId() == null) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "Model API ID is missing");
        }

        ApsaraGatewayClient client = new ApsaraGatewayClient(config);
        try {
            GetModelApiResponse response =
                    client.getModelApi(gateway.getGatewayId(), refConfig.getModelApiId());

            if (response.getBody() == null || response.getBody().getData() == null) {
                throw new BusinessException(ErrorCode.GATEWAY_ERROR, "Model API does not exist");
            }

            GetModelApiResponse.ResponseData data = response.getBody().getData();

            return convertToModelConfigJson(data, gateway, config);
        } catch (Exception e) {
            log.error(
                    "Failed to fetch Apsara model config, dependency=ApsaraGateway,"
                            + " operation=getModelApi, gatewayId={}, modelApiId={}, errorType={},"
                            + " errorMessage={}",
                    gateway.getGatewayId(),
                    refConfig.getModelApiId(),
                    e.getClass().getSimpleName(),
                    e.getMessage(),
                    e);
            throw new BusinessException(
                    ErrorCode.INTERNAL_ERROR,
                    e,
                    "Failed to fetch Apsara model config: " + e.getMessage());
        } finally {
            client.close();
        }
    }

    /**
     * Converts Apsara model details to ModelConfigResult JSON.
     */
    private String convertToModelConfigJson(
            GetModelApiResponse.ResponseData data, Gateway gateway, ApsaraGatewayConfig config) {
        List<DomainResult> domains = new ArrayList<>();
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
     * Builds service entries from Apsara model service data.
     */
    private List<ServiceResult> buildServicesFromAdpService(GetModelApiResponse.ResponseData data) {
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

    /**
     * Maps Apsara protocol values to HiMarket AI protocol values.
     */
    private String mapProtocol(String protocol) {
        if ("OPENAI_COMPATIBLE".equalsIgnoreCase(protocol)) {
            return "OpenAI/V1";
        }
        return protocol;
    }

    /**
     * Maps Apsara scene types to HiMarket model categories.
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

    /**
     * Builds route entries from Apsara model service data.
     */
    private List<HttpRouteResult> buildRoutesFromAdpService(
            GetModelApiResponse.ResponseData data, List<DomainResult> domains) {
        if (data.getMethodPathList() == null || data.getMethodPathList().isEmpty()) {
            return Collections.emptyList();
        }

        List<HttpRouteResult> routes = new ArrayList<>();
        for (GetModelApiResponse.MethodPath methodPath : data.getMethodPathList()) {
            HttpRouteResult route = new HttpRouteResult();

            route.setDomains(domains);

            String path = methodPath.getPath();
            String fullPath = data.getBasePath() != null ? data.getBasePath() + path : path;
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

    @Override
    public CredentialContext fetchApiCredential(
            Gateway gateway, ProductType productType, ProductRef productRef) {
        return null;
    }

    /**
     * Converts Apsara MCP server details to MCP configuration JSON.
     */
    private String convertToMCPConfig(
            GetMcpServerResponse.ResponseData data,
            String gwInstanceId,
            ApsaraGatewayConfig config) {
        McpConfigResult mcpConfig = new McpConfigResult();
        mcpConfig.setMcpServerName(data.getName());

        McpConfigResult.McpServerConfig serverConfig = new McpConfigResult.McpServerConfig();
        serverConfig.setPath("/mcp-servers/" + data.getName());

        List<DomainResult> domains = getGatewayAccessDomains(gwInstanceId, config);
        if (domains != null && !domains.isEmpty()) {
            serverConfig.setDomains(domains);
        } else {
            if (data.getServices() != null && !data.getServices().isEmpty()) {
                List<DomainResult> fallbackDomains = new ArrayList<>();
                GetMcpServerResponse.Service service = data.getServices().get(0);
                if (service.getName() != null) {
                    DomainResult domain =
                            DomainResult.builder()
                                    .domain(service.getName())
                                    .port(80)
                                    .protocol("http")
                                    .build();
                    fallbackDomains.add(domain);
                }
                serverConfig.setDomains(fallbackDomains);
            }
        }

        mcpConfig.setMcpServerConfig(serverConfig);

        mcpConfig.setTools(data.getRawConfigurations());

        mcpConfig.setFromType(
                "OPEN_API".equalsIgnoreCase(data.getType())
                        ? McpFromType.HTTP_TO_MCP
                        : McpFromType.NATIVE_MCP);
        if (data.getType().equalsIgnoreCase("DIRECT_ROUTE")) {
            mcpConfig.setProtocol(
                    data.getDirectRouteConfig().getTransportType().equalsIgnoreCase("streamable")
                            ? McpProtocolType.STREAMABLE_HTTP
                            : McpProtocolType.SSE);
        }

        McpConfigResult.McpMetadata meta = new McpConfigResult.McpMetadata();
        meta.setSource(GatewayType.APSARA_GATEWAY.name());
        mcpConfig.setMeta(meta);

        return JsonUtil.toJson(mcpConfig);
    }

    /**
     * Fetches gateway access information and builds domain entries.
     */
    private List<DomainResult> getGatewayAccessDomains(
            String gwInstanceId, ApsaraGatewayConfig config) {
        ApsaraGatewayClient client = new ApsaraGatewayClient(config);
        try {
            GetInstanceInfoResponse response = client.getInstance(gwInstanceId);

            if (response.getBody() == null || response.getBody().getData() == null) {
                log.warn(
                        "Gateway instance not found, dependency=ApsaraGateway,"
                                + " operation=getInstance, gatewayId={}",
                        gwInstanceId);
                return null;
            }

            GetInstanceInfoResponse.ResponseData instanceData = response.getBody().getData();
            if (instanceData.getAccessMode() != null && !instanceData.getAccessMode().isEmpty()) {
                return buildDomainsFromAccessModes(instanceData.getAccessMode());
            }

            log.warn(
                    "Gateway instance access mode is missing, dependency=ApsaraGateway,"
                            + " operation=getInstance, gatewayId={}",
                    gwInstanceId);
            return null;
        } catch (Exception e) {
            log.error(
                    "Failed to fetch gateway access info, dependency=ApsaraGateway,"
                            + " operation=getInstance, gatewayId={}, errorType={}, errorMessage={}",
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
    private List<DomainResult> buildDomainsFromAccessModes(
            List<GetInstanceInfoResponse.AccessMode> accessModes) {
        List<DomainResult> domains = new ArrayList<>();
        if (accessModes == null || accessModes.isEmpty()) {
            return domains;
        }

        GetInstanceInfoResponse.AccessMode accessMode = accessModes.get(0);

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

    @Override
    public PageResult<GatewayResult> fetchGateways(Object param, int page, int size) {
        QueryApsaraGatewayParam p = (QueryApsaraGatewayParam) param;

        ApsaraGatewayConfig cfg = new ApsaraGatewayConfig();
        cfg.setRegionId(p.getRegionId());
        cfg.setAccessKeyId(p.getAccessKeyId());
        cfg.setAccessKeySecret(p.getAccessKeySecret());
        cfg.setSecurityToken(p.getSecurityToken());
        cfg.setDomain(p.getDomain());
        cfg.setProduct(p.getProduct());
        cfg.setVersion(p.getVersion());
        cfg.setXAcsOrganizationId(p.getXAcsOrganizationId());
        cfg.setXAcsCallerSdkSource(p.getXAcsCallerSdkSource());
        cfg.setXAcsResourceGroupId(p.getXAcsResourceGroupId());
        cfg.setXAcsCallerType(p.getXAcsCallerType());
        ApsaraGatewayClient client = new ApsaraGatewayClient(cfg);

        try {
            String brokerEngineType =
                    p.getBrokerEngineType() != null ? p.getBrokerEngineType() : "HIGRESS";
            ListInstancesResponse response = client.listInstances(page, size, brokerEngineType);

            if (response.getBody() == null || response.getBody().getData() == null) {
                return PageResult.of(new ArrayList<>(), page, size, 0);
            }

            ListInstancesResponse.ResponseData data = response.getBody().getData();

            int total = data.getTotal() != null ? data.getTotal() : 0;

            List<GatewayResult> list = new ArrayList<>();
            if (data.getRecords() != null) {
                for (ListInstancesResponse.Record record : data.getRecords()) {
                    GatewayResult gr =
                            GatewayResult.builder()
                                    .gatewayId(record.getGwInstanceId())
                                    .gatewayName(record.getName())
                                    .gatewayType(GatewayType.APSARA_GATEWAY)
                                    .build();
                    list.add(gr);
                }
            }

            return PageResult.of(list, page, size, total);
        } catch (Exception e) {
            log.error(
                    "Failed to fetch Apsara gateways, dependency=ApsaraGateway,"
                            + " operation=listInstances, page={}, size={}, brokerEngineType={},"
                            + " errorType={}, errorMessage={}",
                    page,
                    size,
                    p.getBrokerEngineType(),
                    e.getClass().getSimpleName(),
                    e.getMessage(),
                    e);
            throw new BusinessException(
                    ErrorCode.INTERNAL_ERROR,
                    e,
                    "Failed to fetch Apsara gateways: " + e.getMessage());
        } finally {
            client.close();
        }
    }

    @Override
    public String createConsumer(
            Consumer consumer, ConsumerCredential credential, GatewayConfig config) {
        ApsaraGatewayConfig apsaraConfig = config.getApsaraGatewayConfig();
        if (apsaraConfig == null) {
            throw new BusinessException(
                    ErrorCode.INVALID_PARAMETER, "Apsara gateway config is missing");
        }

        // Include a developer suffix to avoid consumer name collisions across developers.
        String mark =
                consumer.getDeveloperId()
                        .substring(Math.max(0, consumer.getDeveloperId().length() - 8));
        String gwConsumerName = StrUtil.format("{}-{}", consumer.getName(), mark);

        ApsaraGatewayClient client = new ApsaraGatewayClient(apsaraConfig);
        try {
            CreateAppRequest request = new CreateAppRequest();
            request.setGwInstanceId(config.getGatewayId());
            request.setAppName(gwConsumerName);
            applyCredentialToRequest(credential, request);

            log.info(
                    "Creating Apsara gateway consumer, dependency=ApsaraGateway,"
                            + " operation=createApp, gatewayId={}, consumerName={}, authType={}",
                    config.getGatewayId(),
                    gwConsumerName,
                    request.getAuthType());

            CreateAppResponse response = client.createApp(request);

            if (response.getBody() != null) {
                log.info(
                        "Apsara create consumer response received, dependency=ApsaraGateway,"
                                + " operation=createApp, gatewayId={}, consumerName={}, status={},"
                                + " errorMessage={}, data={}",
                        config.getGatewayId(),
                        gwConsumerName,
                        response.getBody().getCode(),
                        response.getBody().getMsg(),
                        response.getBody().getData());

                if (response.getBody().getCode() != null && response.getBody().getCode() == 200) {
                    if (response.getBody().getData() != null) {
                        return extractConsumerIdFromResponse(
                                response.getBody().getData(), gwConsumerName);
                    }
                    log.warn(
                            "Apsara create consumer response data is missing,"
                                + " dependency=ApsaraGateway, operation=createApp, gatewayId={},"
                                + " fallbackConsumerId={}",
                            config.getGatewayId(),
                            gwConsumerName);
                    return gwConsumerName;
                }
                String errorMsg =
                        String.format(
                                "Failed to create consumer in Apsara gateway: code=%d, message=%s",
                                response.getBody().getCode(), response.getBody().getMsg());
                throw new BusinessException(ErrorCode.GATEWAY_ERROR, errorMsg);
            }
            throw new BusinessException(
                    ErrorCode.GATEWAY_ERROR,
                    "Failed to create consumer in Apsara gateway: empty response");
        } catch (BusinessException e) {
            log.error(
                    "Failed to create Apsara gateway consumer, dependency=ApsaraGateway,"
                            + " operation=createApp, gatewayId={}, consumerName={}, errorType={},"
                            + " errorMessage={}",
                    config.getGatewayId(),
                    gwConsumerName,
                    e.getClass().getSimpleName(),
                    e.getMessage(),
                    e);
            throw e;
        } catch (Exception e) {
            log.error(
                    "Failed to create Apsara gateway consumer, dependency=ApsaraGateway,"
                            + " operation=createApp, gatewayId={}, consumerName={}, errorType={},"
                            + " errorMessage={}",
                    config.getGatewayId(),
                    gwConsumerName,
                    e.getClass().getSimpleName(),
                    e.getMessage(),
                    e);
            throw new BusinessException(
                    ErrorCode.INTERNAL_ERROR,
                    "Failed to create Apsara gateway consumer: " + e.getMessage());
        } finally {
            client.close();
        }
    }

    /**
     * Extracts the consumer ID from an SDK response payload.
     */
    private String extractConsumerIdFromResponse(Object data, String defaultConsumerId) {
        if (data != null) {
            if (data instanceof String) {
                return (String) data;
            }
            return data.toString();
        }
        return defaultConsumerId;
    }

    @Override
    public void updateConsumer(
            String consumerId, ConsumerCredential credential, GatewayConfig config) {
        ApsaraGatewayConfig apsaraConfig = config.getApsaraGatewayConfig();
        if (apsaraConfig == null) {
            throw new BusinessException(
                    ErrorCode.INVALID_PARAMETER, "Apsara gateway config is missing");
        }

        ApsaraGatewayClient client = new ApsaraGatewayClient(apsaraConfig);
        try {
            ModifyAppRequest request = new ModifyAppRequest();
            request.setGwInstanceId(config.getGatewayId());
            request.setAppId(consumerId);
            request.setAppName(consumerId);
            request.setDescription("Consumer managed by Portal");
            applyCredentialToRequest(credential, request);

            ModifyAppResponse response = client.modifyApp(request);

            if (response.getBody() != null && response.getBody().getCode() == 200) {
                log.info(
                        "Updated Apsara gateway consumer, dependency=ApsaraGateway,"
                                + " operation=modifyApp, gatewayId={}, consumerId={}",
                        config.getGatewayId(),
                        consumerId);
                return;
            }
            throw new BusinessException(
                    ErrorCode.GATEWAY_ERROR, "Failed to update Apsara gateway consumer");
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error(
                    "Failed to update Apsara gateway consumer, dependency=ApsaraGateway,"
                            + " operation=modifyApp, gatewayId={}, consumerId={}, errorType={},"
                            + " errorMessage={}",
                    config.getGatewayId(),
                    consumerId,
                    e.getClass().getSimpleName(),
                    e.getMessage(),
                    e);
            throw new BusinessException(
                    ErrorCode.INTERNAL_ERROR,
                    "Failed to update Apsara gateway consumer: " + e.getMessage());
        } finally {
            client.close();
        }
    }

    /**
     * Applies credential authentication settings to a CreateAppRequest.
     */
    private void applyCredentialToRequest(ConsumerCredential credential, CreateAppRequest request) {
        if (credential != null
                && credential.getApiKeyConfig() != null
                && credential.getApiKeyConfig().getCredentials() != null
                && !credential.getApiKeyConfig().getCredentials().isEmpty()) {
            request.setAuthType(5);
            request.setKey(credential.getApiKeyConfig().getCredentials().get(0).getApiKey());
            request.setApiKeyLocationType(
                    mapApiKeyLocationType(credential.getApiKeyConfig().getSource()));
            if ("HEADER".equals(request.getApiKeyLocationType())
                    || "QUERY".equals(request.getApiKeyLocationType())) {
                request.setKeyName(credential.getApiKeyConfig().getKey());
            }
        } else if (credential != null
                && credential.getHmacConfig() != null
                && credential.getHmacConfig().getCredentials() != null
                && !credential.getHmacConfig().getCredentials().isEmpty()) {
            request.setAuthType(7);
            request.setAccessKey(credential.getHmacConfig().getCredentials().get(0).getAk());
            request.setSecretKey(credential.getHmacConfig().getCredentials().get(0).getSk());
        } else {
            request.setAuthType(5);
            request.setApiKeyLocationType("BEARER");
        }
    }

    /**
     * Applies credential authentication settings to a ModifyAppRequest.
     */
    private void applyCredentialToRequest(ConsumerCredential credential, ModifyAppRequest request) {
        if (credential != null
                && credential.getApiKeyConfig() != null
                && credential.getApiKeyConfig().getCredentials() != null
                && !credential.getApiKeyConfig().getCredentials().isEmpty()) {
            request.setAuthType(5);
            request.setKey(credential.getApiKeyConfig().getCredentials().get(0).getApiKey());
            request.setApiKeyLocationType(
                    mapApiKeyLocationType(credential.getApiKeyConfig().getSource()));
            if ("HEADER".equals(request.getApiKeyLocationType())
                    || "QUERY".equals(request.getApiKeyLocationType())) {
                request.setKeyName(credential.getApiKeyConfig().getKey());
            }
        } else if (credential != null
                && credential.getHmacConfig() != null
                && credential.getHmacConfig().getCredentials() != null
                && !credential.getHmacConfig().getCredentials().isEmpty()) {
            request.setAuthType(7);
            request.setAccessKey(credential.getHmacConfig().getCredentials().get(0).getAk());
            request.setSecretKey(credential.getHmacConfig().getCredentials().get(0).getSk());
        } else {
            request.setAuthType(5);
            request.setApiKeyLocationType("BEARER");
        }
    }

    private String mapApiKeyLocationType(String source) {
        if (source == null) {
            return "BEARER";
        }
        return switch (source) {
            case "Header" -> "HEADER";
            case "Query" -> "QUERY";
            case "Bearer" -> "BEARER";
            default -> "BEARER";
        };
    }

    @Override
    public void deleteConsumer(String consumerId, GatewayConfig config) {
        ApsaraGatewayConfig apsaraConfig = config.getApsaraGatewayConfig();
        if (apsaraConfig == null) {
            throw new BusinessException(
                    ErrorCode.INVALID_PARAMETER, "Apsara gateway config is missing");
        }

        ApsaraGatewayClient client = new ApsaraGatewayClient(apsaraConfig);
        try {
            BatchDeleteAppResponse response = client.deleteApp(config.getGatewayId(), consumerId);

            if (response.getBody() != null && response.getBody().getCode() == 200) {
                log.info(
                        "Deleted Apsara gateway consumer, dependency=ApsaraGateway,"
                                + " operation=batchDeleteApp, gatewayId={}, consumerId={}",
                        config.getGatewayId(),
                        consumerId);
                return;
            }
            throw new BusinessException(
                    ErrorCode.GATEWAY_ERROR, "Failed to delete Apsara gateway consumer");
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error(
                    "Failed to delete Apsara gateway consumer, dependency=ApsaraGateway,"
                        + " operation=batchDeleteApp, gatewayId={}, consumerId={}, errorType={},"
                        + " errorMessage={}",
                    config.getGatewayId(),
                    consumerId,
                    e.getClass().getSimpleName(),
                    e.getMessage(),
                    e);
            throw new BusinessException(
                    ErrorCode.INTERNAL_ERROR,
                    "Failed to delete Apsara gateway consumer: " + e.getMessage());
        } finally {
            client.close();
        }
    }

    @Override
    public boolean isConsumerExists(String consumerId, GatewayConfig config) {
        ApsaraGatewayConfig apsaraConfig = config.getApsaraGatewayConfig();
        if (apsaraConfig == null) {
            log.warn(
                    "Apsara gateway config is missing, dependency=ApsaraGateway,"
                            + " operation=listAppsByGwInstanceId, consumerId={}",
                    consumerId);
            return false;
        }

        ApsaraGatewayClient client = new ApsaraGatewayClient(apsaraConfig);
        try {
            ListAppsByGwInstanceIdResponse response =
                    client.listAppsByGwInstanceId(config.getGatewayId(), null);

            if (response.getBody() != null && response.getBody().getData() != null) {
                return response.getBody().getData().stream()
                        .anyMatch(app -> consumerId.equals(app.getAppName()));
            }
            return false;
        } catch (Exception e) {
            log.warn(
                    "Failed to check whether Apsara gateway consumer exists,"
                            + " dependency=ApsaraGateway, operation=listAppsByGwInstanceId,"
                            + " gatewayId={}, consumerId={}, errorType={}, errorMessage={}",
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
        ApsaraGatewayConfig apsaraConfig = gateway.getApsaraGatewayConfig();
        if (apsaraConfig == null) {
            throw new BusinessException(
                    ErrorCode.INVALID_PARAMETER, "Apsara gateway config is missing");
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
        ApsaraGatewayConfig apsaraConfig = gateway.getApsaraGatewayConfig();
        if (apsaraConfig == null) {
            throw new BusinessException(
                    ErrorCode.INVALID_PARAMETER, "Apsara gateway config is missing");
        }

        ApsaraGatewayClient client = new ApsaraGatewayClient(apsaraConfig);
        try {
            AddMcpServerConsumersResponse response =
                    client.addMcpServerConsumers(
                            gateway.getGatewayId(),
                            apigRefConfig.getMcpServerName(),
                            Collections.singletonList(consumerId));

            if (response.getBody() != null && response.getBody().getCode() == 200) {
                log.info(
                        "Authorized Apsara gateway consumer to MCP server,"
                                + " dependency=ApsaraGateway, operation=addMcpServerConsumers,"
                                + " gatewayId={}, consumerId={}, mcpServerName={}",
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
            }
            throw new BusinessException(
                    ErrorCode.GATEWAY_ERROR, "Failed to authorize consumer to MCP server");
        } catch (BusinessException e) {
            log.error(
                    "Failed to authorize Apsara gateway consumer to MCP server,"
                            + " dependency=ApsaraGateway, operation=addMcpServerConsumers,"
                            + " gatewayId={}, consumerId={}, mcpServerName={}, errorType={},"
                            + " errorMessage={}",
                    gateway.getGatewayId(),
                    consumerId,
                    apigRefConfig.getMcpServerName(),
                    e.getClass().getSimpleName(),
                    e.getMessage(),
                    e);
            throw e;
        } catch (Exception e) {
            log.error(
                    "Failed to authorize Apsara gateway consumer to MCP server,"
                            + " dependency=ApsaraGateway, operation=addMcpServerConsumers,"
                            + " gatewayId={}, consumerId={}, mcpServerName={}, errorType={},"
                            + " errorMessage={}",
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
        ApsaraGatewayConfig apsaraConfig = gateway.getApsaraGatewayConfig();
        if (apsaraConfig == null) {
            throw new BusinessException(
                    ErrorCode.INVALID_PARAMETER, "Apsara gateway config is missing");
        }

        ApsaraGatewayClient client = new ApsaraGatewayClient(apsaraConfig);
        try {
            BatchGrantModelApiResponse response =
                    client.batchGrantModelApi(
                            gateway.getGatewayId(),
                            apigRefConfig.getModelApiId(),
                            Collections.singletonList(consumerId));

            if (response.getBody() != null
                    && response.getBody().getCode() != null
                    && response.getBody().getCode() == 200) {
                log.info(
                        "Authorized Apsara gateway consumer to Model API,"
                                + " dependency=ApsaraGateway, operation=batchGrantModelApi,"
                                + " gatewayId={}, consumerId={}, modelApiId={}",
                        gateway.getGatewayId(),
                        consumerId,
                        apigRefConfig.getModelApiId());
                return;
            }

            String message = response.getBody() != null ? response.getBody().getMsg() : null;
            throw new BusinessException(
                    ErrorCode.GATEWAY_ERROR,
                    "Failed to authorize consumer to Model API: "
                            + (message != null ? message : "Unknown error"));
        } catch (BusinessException e) {
            log.error(
                    "Failed to authorize Apsara gateway consumer to Model API,"
                            + " dependency=ApsaraGateway, operation=batchGrantModelApi,"
                            + " gatewayId={}, consumerId={}, modelApiId={}, errorType={},"
                            + " errorMessage={}",
                    gateway.getGatewayId(),
                    consumerId,
                    apigRefConfig.getModelApiId(),
                    e.getClass().getSimpleName(),
                    e.getMessage(),
                    e);
            throw e;
        } catch (Exception e) {
            log.error(
                    "Failed to authorize Apsara gateway consumer to Model API,"
                            + " dependency=ApsaraGateway, operation=batchGrantModelApi,"
                            + " gatewayId={}, consumerId={}, modelApiId={}, errorType={},"
                            + " errorMessage={}",
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
    public HttpApiApiInfo fetchAPI(Gateway gateway, String apiId) {
        throw new UnsupportedOperationException("Apsara gateway not implemented for fetch api");
    }

    @Override
    public void revokeConsumerAuthorization(
            Gateway gateway, String consumerId, ConsumerAuthConfig authConfig) {
        AdpAIAuthConfig adpAIAuthConfig = authConfig.getAdpAIAuthConfig();
        if (adpAIAuthConfig == null) {
            log.warn(
                    "Apsara authorization config is empty, dependency=ApsaraGateway,"
                            + " operation=revokeConsumerAuthorization, gatewayId={}, consumerId={}",
                    gateway.getGatewayId(),
                    consumerId);
            return;
        }

        ApsaraGatewayConfig apsaraConfig = gateway.getApsaraGatewayConfig();
        if (apsaraConfig == null) {
            throw new BusinessException(
                    ErrorCode.INVALID_PARAMETER, "Apsara gateway config is missing");
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
        ApsaraGatewayConfig apsaraConfig = gateway.getApsaraGatewayConfig();
        if (apsaraConfig == null) {
            throw new BusinessException(
                    ErrorCode.INVALID_PARAMETER, "Apsara gateway config is missing");
        }

        ApsaraGatewayClient client = new ApsaraGatewayClient(apsaraConfig);
        try {
            DeleteMcpServerConsumersResponse response =
                    client.deleteMcpServerConsumers(
                            gateway.getGatewayId(),
                            adpAIAuthConfig.getMcpServerName(),
                            Collections.singletonList(consumerId));

            if (response.getBody() != null && response.getBody().getCode() == 200) {
                log.info(
                        "Revoked Apsara gateway consumer authorization from MCP server,"
                                + " dependency=ApsaraGateway, operation=deleteMcpServerConsumers,"
                                + " gatewayId={}, consumerId={}, mcpServerName={}",
                        gateway.getGatewayId(),
                        consumerId,
                        adpAIAuthConfig.getMcpServerName());
                return;
            }

            String message =
                    response.getBody() != null ? response.getBody().getMsg() : "Unknown error";

            // Apsara can return localized not-found messages when the authorization was already
            // removed. Treat these responses as idempotent success.
            if (message != null
                    && (message.contains("not found")
                            || message.contains(LOCALIZED_NOT_FOUND_MESSAGE)
                            || message.contains("NotFound"))) {
                log.warn(
                        "Apsara MCP authorization already removed or not found,"
                                + " dependency=ApsaraGateway, operation=deleteMcpServerConsumers,"
                                + " gatewayId={}, consumerId={}, mcpServerName={}, errorMessage={}",
                        gateway.getGatewayId(),
                        consumerId,
                        adpAIAuthConfig.getMcpServerName(),
                        message);
                return;
            }

            String errorMsg = "Failed to revoke consumer authorization from MCP server: " + message;
            log.error(
                    "Failed to revoke Apsara MCP authorization, dependency=ApsaraGateway,"
                            + " operation=deleteMcpServerConsumers, gatewayId={}, consumerId={},"
                            + " mcpServerName={}, errorMessage={}",
                    gateway.getGatewayId(),
                    consumerId,
                    adpAIAuthConfig.getMcpServerName(),
                    message);
            throw new BusinessException(ErrorCode.GATEWAY_ERROR, errorMsg);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error(
                    "Failed to revoke Apsara MCP authorization, dependency=ApsaraGateway,"
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
        ApsaraGatewayConfig apsaraConfig = gateway.getApsaraGatewayConfig();
        if (apsaraConfig == null) {
            throw new BusinessException(
                    ErrorCode.INVALID_PARAMETER, "Apsara gateway config is missing");
        }

        ApsaraGatewayClient client = new ApsaraGatewayClient(apsaraConfig);
        try {
            String authId =
                    getAuthIdForModelApi(
                            gateway, adpAIAuthConfig.getModelApiId(), consumerId, client);

            if (authId == null) {
                log.warn(
                        "No authId found for Apsara model API authorization,"
                                + " dependency=ApsaraGateway, operation=listModelApiConsumers,"
                                + " gatewayId={}, consumerId={}, modelApiId={}",
                        gateway.getGatewayId(),
                        consumerId,
                        adpAIAuthConfig.getModelApiId());
                return;
            }

            RevokeModelApiGrantResponse response =
                    client.revokeModelApiGrant(gateway.getGatewayId(), authId);

            if (response.getBody() != null) {
                Integer code = response.getBody().getCode();
                if (code != null && code == 200) {
                    log.info(
                            "Revoked Apsara gateway consumer authorization from Model API,"
                                    + " dependency=ApsaraGateway, operation=revokeModelApiGrant,"
                                    + " gatewayId={}, consumerId={}, modelApiId={}",
                            gateway.getGatewayId(),
                            consumerId,
                            adpAIAuthConfig.getModelApiId());
                    return;
                }

                String message = response.getBody().getMsg();

                // Apsara can return localized not-found messages when the authorization was
                // already removed. Treat these responses as idempotent success.
                if (message != null
                        && (message.contains("not found")
                                || message.contains(LOCALIZED_NOT_FOUND_MESSAGE)
                                || message.contains("NotFound")
                                || (code != null && code == 404))) {
                    log.warn(
                            "Apsara model API authorization already removed or not found,"
                                    + " dependency=ApsaraGateway, operation=revokeModelApiGrant,"
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
                        "Failed to revoke Apsara model API authorization,"
                                + " dependency=ApsaraGateway, operation=revokeModelApiGrant,"
                                + " gatewayId={}, consumerId={}, modelApiId={}, errorMessage={}",
                        gateway.getGatewayId(),
                        consumerId,
                        adpAIAuthConfig.getModelApiId(),
                        message);
                throw new BusinessException(ErrorCode.GATEWAY_ERROR, errorMsg);
            }

            throw new BusinessException(
                    ErrorCode.GATEWAY_ERROR,
                    "Failed to revoke consumer authorization from Model API");
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error(
                    "Failed to revoke Apsara model API authorization, dependency=ApsaraGateway,"
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
            Gateway gateway, String modelApiId, String consumerId, ApsaraGatewayClient client) {
        try {
            ListModelApiConsumersResponse response =
                    client.listModelApiConsumers(gateway.getGatewayId(), modelApiId, 1, 10);

            if (response.getBody() != null
                    && response.getBody().getCode() != null
                    && response.getBody().getCode() == 200
                    && response.getBody().getData() != null
                    && response.getBody().getData().getRecords() != null) {
                for (ListModelApiConsumersResponse.Record record :
                        response.getBody().getData().getRecords()) {
                    if (consumerId.equals(record.getAppId())) {
                        return record.getAuthId();
                    }
                }
            }

            log.warn(
                    "Failed to get Apsara model API authId, dependency=ApsaraGateway,"
                            + " operation=listModelApiConsumers, gatewayId={}, modelApiId={},"
                            + " consumerId={}",
                    gateway.getGatewayId(),
                    modelApiId,
                    consumerId);
            return null;
        } catch (Exception e) {
            log.error(
                    "Failed to get Apsara model API authId, dependency=ApsaraGateway,"
                            + " operation=listModelApiConsumers, gatewayId={}, modelApiId={},"
                            + " consumerId={}, errorType={}, errorMessage={}",
                    gateway.getGatewayId(),
                    modelApiId,
                    consumerId,
                    e.getClass().getSimpleName(),
                    e.getMessage(),
                    e);
            return null;
        }
    }

    @Override
    public GatewayType getGatewayType() {
        return GatewayType.APSARA_GATEWAY;
    }

    @Override
    public List<URI> fetchGatewayUris(Gateway gateway) {
        return Collections.emptyList();
    }
}
