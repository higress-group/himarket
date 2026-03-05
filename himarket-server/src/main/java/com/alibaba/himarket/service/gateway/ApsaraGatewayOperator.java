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
import cn.hutool.json.JSONUtil;
import com.alibaba.himarket.core.exception.BusinessException;
import com.alibaba.himarket.core.exception.ErrorCode;
import com.alibaba.himarket.dto.params.apsara.CreateAppRequest;
import com.alibaba.himarket.dto.params.apsara.ModifyAppRequest;
import com.alibaba.himarket.dto.params.gateway.QueryApsaraGatewayParam;
import com.alibaba.himarket.dto.result.agent.AgentAPIResult;
import com.alibaba.himarket.dto.result.apsara.*;
import com.alibaba.himarket.dto.result.common.DomainResult;
import com.alibaba.himarket.dto.result.common.PageResult;
import com.alibaba.himarket.dto.result.gateway.GatewayResult;
import com.alibaba.himarket.dto.result.httpapi.APIResult;
import com.alibaba.himarket.dto.result.httpapi.HttpRouteResult;
import com.alibaba.himarket.dto.result.httpapi.ServiceResult;
import com.alibaba.himarket.dto.result.mcp.AdpMCPServerResult;
import com.alibaba.himarket.dto.result.mcp.GatewayMCPServerResult;
import com.alibaba.himarket.dto.result.mcp.MCPConfigResult;
import com.alibaba.himarket.dto.result.model.AIGWModelAPIResult;
import com.alibaba.himarket.dto.result.model.GatewayModelAPIResult;
import com.alibaba.himarket.dto.result.model.ModelConfigResult;
import com.alibaba.himarket.entity.Consumer;
import com.alibaba.himarket.entity.ConsumerCredential;
import com.alibaba.himarket.entity.Gateway;
import com.alibaba.himarket.service.gateway.client.ApsaraGatewayClient;
import com.alibaba.himarket.support.consumer.AdpAIAuthConfig;
import com.alibaba.himarket.support.consumer.ConsumerAuthConfig;
import com.alibaba.himarket.support.enums.GatewayType;
import com.alibaba.himarket.support.gateway.ApsaraGatewayConfig;
import com.alibaba.himarket.support.gateway.GatewayConfig;
import com.alibaba.himarket.support.product.APIGRefConfig;
import com.aliyun.sdk.service.apig20240327.models.HttpApiApiInfo;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class ApsaraGatewayOperator extends GatewayOperator<ApsaraGatewayClient> {

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
    public PageResult<? extends GatewayMCPServerResult> fetchMcpServers(
            Gateway gateway, int page, int size) {
        ApsaraGatewayConfig cfg = gateway.getApsaraGatewayConfig();
        if (cfg == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Apsara gateway config is null");
        }

        ApsaraGatewayClient client = new ApsaraGatewayClient(cfg);
        try {
            // 使用SDK获取MCP服务器列表
            ListMcpServersResponse response =
                    client.listMcpServers(gateway.getGatewayId(), page, size);

            if (response.getBody() == null) {
                return PageResult.of(new ArrayList<>(), page, size, 0);
            }

            // 修复类型不兼容问题
            // 根据错误信息，getData()返回的是ListMcpServersResponseBodyData类型
            ListMcpServersResponse.ResponseData data = response.getBody().getData();

            if (data == null) {
                return PageResult.of(new ArrayList<>(), page, size, 0);
            }

            int total = data.getTotal() != null ? data.getTotal() : 0;

            List<GatewayMCPServerResult> items = new ArrayList<>();
            // 使用工厂方法直接从SDK的record创建AdpMCPServerResult
            if (data.getRecords() != null) {
                items =
                        data.getRecords().stream()
                                .map(AdpMCPServerResult::fromSdkRecord)
                                .collect(Collectors.toList());
            }

            return PageResult.of(items, page, size, total);
        } catch (Exception e) {
            log.error("Error fetching MCP servers by Apsara", e);
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
        ApsaraGatewayConfig cfg = gateway.getApsaraGatewayConfig();
        if (cfg == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Apsara gateway config is null");
        }

        ApsaraGatewayClient client = new ApsaraGatewayClient(cfg);

        try {
            // 使用Common Request的ListInstances方法获取网关实例列表
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
            log.error("Error listing Apsara model apis ", e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, e.getMessage());
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
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "Apsara Gateway 配置缺失");
        }

        // 从 conf 参数中获取 APIGRefConfig
        APIGRefConfig apigRefConfig = (APIGRefConfig) conf;
        if (apigRefConfig == null || apigRefConfig.getMcpServerName() == null) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "MCP Server 名称缺失");
        }

        ApsaraGatewayClient client = new ApsaraGatewayClient(config);
        try {
            // 使用 SDK 获取 MCP Server 详情
            GetMcpServerResponse response =
                    client.getMcpServer(gateway.getGatewayId(), apigRefConfig.getMcpServerName());

            if (response.getBody() == null || response.getBody().getData() == null) {
                throw new BusinessException(ErrorCode.GATEWAY_ERROR, "MCP Server 不存在");
            }

            GetMcpServerResponse.ResponseData data = response.getBody().getData();

            return convertToMCPConfig(data, gateway.getGatewayId(), config);
        } catch (Exception e) {
            log.error(
                    "Error fetching Apsara MCP config for server: {}",
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
        ApsaraGatewayConfig config = gateway.getApsaraGatewayConfig();
        if (config == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Apsara gateway config is null");
        }
        // 入参 conf 通常为 APIGRefConfig，包含选中的 apiId
        APIGRefConfig refConfig = (APIGRefConfig) conf;
        if (refConfig == null || refConfig.getModelApiId() == null) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "Model API ID 缺失");
        }

        ApsaraGatewayClient client = new ApsaraGatewayClient(config);
        try {
            // 使用 Common Request 获取 Model Config 详情
            GetModelApiResponse response =
                    client.getModelApi(gateway.getGatewayId(), refConfig.getModelApiId());

            if (response.getBody() == null || response.getBody().getData() == null) {
                throw new BusinessException(ErrorCode.GATEWAY_ERROR, "Model API 不存在");
            }

            GetModelApiResponse.ResponseData data = response.getBody().getData();

            return convertToModelConfigJson(data, gateway, config);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** 将 Apsara Model Config 模型详情转换为 ModelConfigResult JSON 字符串 */
    private String convertToModelConfigJson(
            GetModelApiResponse.ResponseData data, Gateway gateway, ApsaraGatewayConfig config) {
        // 设置访问域名/地址
        // 优先使用 getModelApi 返回的 domainNameList
        List<DomainResult> domains = new ArrayList<>();
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
                        .modelCategory(mapSceneType(data.getSceneType())) // 映射场景类型为模型类别
                        .routes(buildRoutesFromAdpService(data, domains)) // 从ADP服务数据构建routes
                        .services(buildServicesFromAdpService(data)) // 从ADP服务数据构建services
                        .build();

        ModelConfigResult modelConfig = new ModelConfigResult();
        modelConfig.setModelAPIConfig(apiConfig);

        return JSONUtil.toJsonStr(modelConfig);
    }

    /** 从ADP服务数据构建服务列表 */
    private List<ServiceResult> buildServicesFromAdpService(GetModelApiResponse.ResponseData data) {
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

    /**
     * 将 ADP SceneType 映射到 HiMarket ModelCategory
     * ADP 使用 TEXT_GENERATION/IMAGE_GENERATION 等，HiMarket 使用 TEXT/Image 等
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

    /** 从ADP服务数据构建路由列表 */
    private List<HttpRouteResult> buildRoutesFromAdpService(
            GetModelApiResponse.ResponseData data, List<DomainResult> domains) {
        if (data.getMethodPathList() == null || data.getMethodPathList().isEmpty()) {
            return Collections.emptyList();
        }

        List<HttpRouteResult> routes = new ArrayList<>();
        for (GetModelApiResponse.MethodPath methodPath : data.getMethodPathList()) {
            HttpRouteResult route = new HttpRouteResult();

            // 设置域名
            route.setDomains(domains);

            // 设置匹配规则，路径前面加上basePath
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

    @Override
    public String fetchMcpToolsForConfig(Gateway gateway, Object conf) {
        return null;
    }

    /** 将 Apsara MCP Server 详情转换为 MCPConfigResult 格式 */
    private String convertToMCPConfig(
            GetMcpServerResponse.ResponseData data,
            String gwInstanceId,
            ApsaraGatewayConfig config) {
        MCPConfigResult mcpConfig = new MCPConfigResult();
        mcpConfig.setMcpServerName(data.getName());

        // 设置 MCP Server 配置
        MCPConfigResult.MCPServerConfig serverConfig = new MCPConfigResult.MCPServerConfig();
        serverConfig.setPath("/mcp-servers/" + data.getName());

        // 获取网关实例访问信息并设置域名信息
        List<DomainResult> domains = getGatewayAccessDomains(gwInstanceId, config);
        if (domains != null && !domains.isEmpty()) {
            serverConfig.setDomains(domains);
        } else {
            // 如果无法获取网关访问信息，则使用原有的 services 信息作为备选
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

        // 设置工具配置
        mcpConfig.setTools(data.getRawConfigurations());

        // 设置元数据
        MCPConfigResult.McpMetadata meta = new MCPConfigResult.McpMetadata();
        meta.setSource(GatewayType.APSARA_GATEWAY.name());
        meta.setCreateFromType(data.getType());
        if (data.getType().equalsIgnoreCase("DIRECT_ROUTE")) {
            meta.setProtocol(
                    data.getDirectRouteConfig().getTransportType().equalsIgnoreCase("streamable")
                            ? "HTTP"
                            : "SSE");
        }

        mcpConfig.setMeta(meta);

        return JSONUtil.toJsonStr(mcpConfig);
    }

    /** 获取网关实例的访问信息并构建域名列表 */
    private List<DomainResult> getGatewayAccessDomains(
            String gwInstanceId, ApsaraGatewayConfig config) {
        ApsaraGatewayClient client = new ApsaraGatewayClient(config);
        try {
            GetInstanceInfoResponse response = client.getInstance(gwInstanceId);

            if (response.getBody() == null || response.getBody().getData() == null) {
                log.warn("Gateway instance not found, instanceId={}", gwInstanceId);
                return null;
            }

            GetInstanceInfoResponse.ResponseData instanceData = response.getBody().getData();
            if (instanceData.getAccessMode() != null && !instanceData.getAccessMode().isEmpty()) {
                return buildDomainsFromAccessModes(instanceData.getAccessMode());
            }

            log.warn("Gateway instance has no accessMode, instanceId={}", gwInstanceId);
            return null;
        } catch (Exception e) {
            log.error("Error fetching gateway access info for instance: {}", gwInstanceId, e);
            return null;
        } finally {
            client.close();
        }
    }

    /** 根据网关实例访问信息构建域名列表 */
    private List<DomainResult> buildDomainsFromAccessModes(
            List<GetInstanceInfoResponse.AccessMode> accessModes) {
        List<DomainResult> domains = new ArrayList<>();
        if (accessModes == null || accessModes.isEmpty()) {
            return domains;
        }

        GetInstanceInfoResponse.AccessMode accessMode = accessModes.get(0);

        // 1) LoadBalancer: externalIps:80
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

        // 3) fallback: only externalIps → :80
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
        // 将入参转换为配置
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
            // 使用 Common Request 的 ListInstances 方法获取网关实例列表
            // brokerEngineType 默认为 HIGRESS
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
            log.error("Error listing Apsara gateways", e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, e.getMessage());
        } finally {
            client.close();
        }
    }

    @Override
    public String createConsumer(
            Consumer consumer, ConsumerCredential credential, GatewayConfig config) {
        ApsaraGatewayConfig apsaraConfig = config.getApsaraGatewayConfig();
        if (apsaraConfig == null) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "Apsara Gateway 配置缺失");
        }

        Gateway gateway = config.getGateway();
        if (gateway == null || gateway.getGatewayId() == null) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "网关实例 ID 缺失");
        }

        // 使用 developerId 后 8 位作为后缀，避免不同 developer 的 consumer 名称冲突
        String mark =
                consumer.getDeveloperId()
                        .substring(Math.max(0, consumer.getDeveloperId().length() - 8));
        String gwConsumerName = StrUtil.format("{}-{}", consumer.getName(), mark);

        ApsaraGatewayClient client = new ApsaraGatewayClient(apsaraConfig);
        try {
            CreateAppRequest request = new CreateAppRequest();
            request.setGwInstanceId(gateway.getGatewayId());
            request.setAppName(gwConsumerName);
            applyCredentialToRequest(credential, request);

            log.info(
                    "Creating consumer in Apsara gateway: gatewayId={}, consumerName={},"
                            + " authType={}",
                    gateway.getGatewayId(),
                    gwConsumerName,
                    request.getAuthType());

            CreateAppResponse response = client.createApp(request);

            // 检查响应
            if (response.getBody() != null) {
                log.info(
                        "CreateApp response: code={}, message={}, data={}",
                        response.getBody().getCode(),
                        response.getBody().getMsg(),
                        response.getBody().getData());

                if (response.getBody().getCode() != null && response.getBody().getCode() == 200) {
                    if (response.getBody().getData() != null) {
                        return extractConsumerIdFromResponse(
                                response.getBody().getData(), gwConsumerName);
                    }
                    log.warn("CreateApp succeeded but data is null, using gwConsumerName as ID");
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
            log.error("Business error creating consumer in Apsara gateway", e);
            throw e;
        } catch (Exception e) {
            log.error("Error creating consumer in Apsara gateway", e);
            throw new BusinessException(
                    ErrorCode.INTERNAL_ERROR,
                    "Error creating consumer in Apsara gateway: " + e.getMessage());
        } finally {
            client.close();
        }
    }

    /** 从SDK响应中提取消费者ID */
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
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "Apsara Gateway 配置缺失");
        }

        Gateway gateway = config.getGateway();
        if (gateway == null || gateway.getGatewayId() == null) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "网关实例 ID 缺失");
        }

        ApsaraGatewayClient client = new ApsaraGatewayClient(apsaraConfig);
        try {
            ModifyAppRequest request = new ModifyAppRequest();
            request.setGwInstanceId(gateway.getGatewayId());
            request.setAppId(consumerId);
            request.setAppName(consumerId);
            request.setDescription("Consumer managed by Portal");
            applyCredentialToRequest(credential, request);

            ModifyAppResponse response = client.modifyApp(request);

            if (response.getBody() != null && response.getBody().getCode() == 200) {
                log.info(
                        "Successfully updated consumer {} in Apsara gateway instance {}",
                        consumerId,
                        gateway.getGatewayId());
                return;
            }
            throw new BusinessException(ErrorCode.GATEWAY_ERROR, "更新 Apsara 网关消费者失败");
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error(
                    "Error updating consumer {} in Apsara gateway instance {}",
                    consumerId,
                    gateway != null ? gateway.getGatewayId() : "unknown",
                    e);
            throw new BusinessException(
                    ErrorCode.INTERNAL_ERROR, "更新 Apsara 网关消费者异常：" + e.getMessage());
        } finally {
            client.close();
        }
    }

    /** 根据凭证类型设置认证参数到 CreateAppRequest */
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

    /** 根据凭证类型设置认证参数到 ModifyAppRequest */
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
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "Apsara Gateway 配置缺失");
        }

        Gateway gateway = config.getGateway();
        if (gateway == null || gateway.getGatewayId() == null) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "网关实例 ID 缺失");
        }

        ApsaraGatewayClient client = new ApsaraGatewayClient(apsaraConfig);
        try {
            BatchDeleteAppResponse response = client.deleteApp(gateway.getGatewayId(), consumerId);

            if (response.getBody() != null && response.getBody().getCode() == 200) {
                log.info(
                        "Successfully deleted consumer {} from Apsara gateway instance {}",
                        consumerId,
                        gateway.getGatewayId());
                return;
            }
            throw new BusinessException(ErrorCode.GATEWAY_ERROR, "删除 Apsara 网关消费者失败");
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error(
                    "Error deleting consumer {} from Apsara gateway instance {}",
                    consumerId,
                    gateway != null ? gateway.getGatewayId() : "unknown",
                    e);
            throw new BusinessException(
                    ErrorCode.INTERNAL_ERROR, "删除 Apsara 网关消费者异常：" + e.getMessage());
        } finally {
            client.close();
        }
    }

    @Override
    public boolean isConsumerExists(String consumerId, GatewayConfig config) {
        ApsaraGatewayConfig apsaraConfig = config.getApsaraGatewayConfig();
        if (apsaraConfig == null) {
            log.warn("Apsara Gateway 配置缺失，无法检查消费者存在性");
            return false;
        }

        Gateway gateway = config.getGateway();
        if (gateway == null || gateway.getGatewayId() == null) {
            log.warn("网关实例 ID 缺失，无法检查消费者存在性");
            return false;
        }

        ApsaraGatewayClient client = new ApsaraGatewayClient(apsaraConfig);
        try {
            // 获取所有应用列表，然后在客户端筛选
            ListAppsByGwInstanceIdResponse response =
                    client.listAppsByGwInstanceId(
                            gateway.getGatewayId(), null // serviceType 为 null，获取所有类型
                            );

            // data 字段直接是 List，不是包含 records 的对象
            if (response.getBody() != null && response.getBody().getData() != null) {
                // 遍历应用列表，查找匹配的 consumerId（appName）
                return response.getBody().getData().stream()
                        .anyMatch(app -> consumerId.equals(app.getAppName()));
            }
            return false;
        } catch (Exception e) {
            log.warn("检查 Apsara 网关消费者存在性失败：consumerId={}", consumerId, e);
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
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "Apsara Gateway 配置缺失");
        }

        APIGRefConfig apigRefConfig = (APIGRefConfig) refConfig;
        if (apigRefConfig == null) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "APIGRefConfig 配置缺失");
        }

        ConsumerAuthConfig result = null;

        // 如果是 MCP Server 配置
        if (apigRefConfig.getMcpServerName() != null) {
            result = authorizeMcpServerConsumer(gateway, consumerId, apigRefConfig);
        }

        // 如果是 Model API 配置
        if (apigRefConfig.getModelApiId() != null) {
            authorizeModelApiConsumer(gateway, consumerId, apigRefConfig);
            // Model API 授权不需要返回特定的 auth config
        }

        // 如果 result 为 null，说明是 Model API 的情况，返回通用配置
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

    /** 授权消费者到 MCP Server */
    private ConsumerAuthConfig authorizeMcpServerConsumer(
            Gateway gateway, String consumerId, APIGRefConfig apigRefConfig) {
        ApsaraGatewayConfig apsaraConfig = gateway.getApsaraGatewayConfig();
        if (apsaraConfig == null) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "Apsara Gateway 配置缺失");
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
                        "Successfully authorized consumer {} to MCP server {}",
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

    /** 授权消费者到 Model API */
    private void authorizeModelApiConsumer(
            Gateway gateway, String consumerId, APIGRefConfig apigRefConfig) {
        ApsaraGatewayConfig apsaraConfig = gateway.getApsaraGatewayConfig();
        if (apsaraConfig == null) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "Apsara Gateway 配置缺失");
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
                        "Successfully authorized consumer {} to Model API {}",
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
    public HttpApiApiInfo fetchAPI(Gateway gateway, String apiId) {
        throw new UnsupportedOperationException("Apsara gateway not implemented for fetch api");
    }

    @Override
    public void revokeConsumerAuthorization(
            Gateway gateway, String consumerId, ConsumerAuthConfig authConfig) {
        AdpAIAuthConfig adpAIAuthConfig = authConfig.getAdpAIAuthConfig();
        if (adpAIAuthConfig == null) {
            log.warn("Apsara 授权配置为空，无法撤销授权");
            return;
        }

        ApsaraGatewayConfig apsaraConfig = gateway.getApsaraGatewayConfig();
        if (apsaraConfig == null) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "Apsara Gateway 配置缺失");
        }

        // 如果是 MCP Server 授权，使用原有逻辑
        if (adpAIAuthConfig.getMcpServerName() != null) {
            revokeMcpServerConsumerAuthorization(gateway, consumerId, adpAIAuthConfig);
        }

        // 如果是 Model API 授权，使用新逻辑
        if (adpAIAuthConfig.getModelApiId() != null) {
            revokeModelApiConsumerAuthorization(gateway, consumerId, adpAIAuthConfig);
        }
    }

    /** 撤销消费者对 MCP Server 的授权 */
    private void revokeMcpServerConsumerAuthorization(
            Gateway gateway, String consumerId, AdpAIAuthConfig adpAIAuthConfig) {
        ApsaraGatewayConfig apsaraConfig = gateway.getApsaraGatewayConfig();
        if (apsaraConfig == null) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "Apsara Gateway 配置缺失");
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
                        "Successfully revoked consumer {} authorization from MCP server {}",
                        consumerId,
                        adpAIAuthConfig.getMcpServerName());
                return;
            }

            String message =
                    response.getBody() != null ? response.getBody().getMsg() : "Unknown error";

            // 如果是资源不存在（已被删除），只记录警告，不抛异常
            if (message != null
                    && (message.contains("not found")
                            || message.contains("不存在")
                            || message.contains("NotFound"))) {
                log.warn(
                        "Consumer authorization already removed or not found: consumerId={},"
                                + " mcpServer={}, message={}",
                        consumerId,
                        adpAIAuthConfig.getMcpServerName(),
                        message);
                return;
            }

            // 其他错误抛出异常
            String errorMsg = "Failed to revoke consumer authorization from MCP server: " + message;
            log.error(errorMsg);
            throw new BusinessException(ErrorCode.GATEWAY_ERROR, errorMsg);
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

    /** 撤销消费者对 Model API 的授权 */
    private void revokeModelApiConsumerAuthorization(
            Gateway gateway, String consumerId, AdpAIAuthConfig adpAIAuthConfig) {
        ApsaraGatewayConfig apsaraConfig = gateway.getApsaraGatewayConfig();
        if (apsaraConfig == null) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "Apsara Gateway 配置缺失");
        }

        ApsaraGatewayClient client = new ApsaraGatewayClient(apsaraConfig);
        try {
            // 首先查询 authId
            String authId =
                    getAuthIdForModelApi(
                            gateway, adpAIAuthConfig.getModelApiId(), consumerId, client);

            if (authId == null) {
                log.warn(
                        "No authId found for consumer {} and model API {}, skipping revocation",
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
                            "Successfully revoked consumer {} authorization from Model API {}",
                            consumerId,
                            adpAIAuthConfig.getModelApiId());
                    return;
                }

                String message = response.getBody().getMsg();

                // 如果是资源不存在（已被删除），只记录警告，不抛异常
                if (message != null
                        && (message.contains("not found")
                                || message.contains("不存在")
                                || message.contains("NotFound")
                                || (code != null && code == 404))) {
                    log.warn(
                            "Consumer authorization already removed or not found:"
                                    + " consumerId={}, modelApiId={}, message={}",
                            consumerId,
                            adpAIAuthConfig.getModelApiId(),
                            message);
                    return;
                }

                String errorMsg =
                        "Failed to revoke consumer authorization from Model API: " + message;
                log.error(errorMsg);
                throw new BusinessException(ErrorCode.GATEWAY_ERROR, errorMsg);
            }

            throw new BusinessException(
                    ErrorCode.GATEWAY_ERROR,
                    "Failed to revoke consumer authorization from Model API");
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

    /** 查询 Model API 的消费者授权列表，获取 authId */
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

            log.warn("Failed to get authId for model API consumer lookup");
            return null;
        } catch (Exception e) {
            log.error("Error getting authId for model API", e);
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
