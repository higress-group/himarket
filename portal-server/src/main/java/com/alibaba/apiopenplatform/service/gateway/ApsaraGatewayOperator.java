package com.alibaba.apiopenplatform.service.gateway;

import cn.hutool.json.JSONUtil;
import com.alibaba.apiopenplatform.core.exception.BusinessException;
import com.alibaba.apiopenplatform.core.exception.ErrorCode;
import com.alibaba.apiopenplatform.dto.params.gateway.QueryApsaraGatewayParam;
import com.alibaba.apiopenplatform.dto.result.agent.AgentAPIResult;
import com.alibaba.apiopenplatform.dto.result.common.PageResult;
import com.alibaba.apiopenplatform.dto.result.gateway.GatewayResult;
import com.alibaba.apiopenplatform.dto.result.httpapi.APIResult;
import com.alibaba.apiopenplatform.dto.result.common.DomainResult;
import com.alibaba.apiopenplatform.dto.result.mcp.AdpMCPServerResult;
import com.alibaba.apiopenplatform.dto.result.mcp.GatewayMCPServerResult;
import com.alibaba.apiopenplatform.dto.result.mcp.MCPConfigResult;
import com.alibaba.apiopenplatform.dto.result.model.GatewayModelAPIResult;
import com.alibaba.apiopenplatform.entity.Consumer;
import com.alibaba.apiopenplatform.entity.ConsumerCredential;
import com.alibaba.apiopenplatform.entity.Gateway;
import com.alibaba.apiopenplatform.service.gateway.client.ApsaraStackGatewayClient;
import com.alibaba.apiopenplatform.support.consumer.AdpAIAuthConfig;
import com.alibaba.apiopenplatform.support.consumer.ConsumerAuthConfig;
import com.alibaba.apiopenplatform.support.enums.GatewayType;
import com.alibaba.apiopenplatform.support.gateway.GatewayConfig;
import com.alibaba.apiopenplatform.support.gateway.ApsaraGatewayConfig;
import com.alibaba.apiopenplatform.support.product.APIGRefConfig;
import com.aliyun.sdk.service.apig20240327.models.HttpApiApiInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import com.aliyun.apsarastack.csb220230206.models.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ApsaraGatewayOperator extends GatewayOperator<ApsaraStackGatewayClient> {

    @Override
    public PageResult<APIResult> fetchHTTPAPIs(Gateway gateway, int page, int size) {
        throw new UnsupportedOperationException("Apsara gateway not implemented for HTTP APIs listing");
    }

    @Override
    public PageResult<APIResult> fetchRESTAPIs(Gateway gateway, int page, int size) {
        throw new UnsupportedOperationException("Apsara gateway not implemented for REST APIs listing");
    }

    @Override
    public PageResult<? extends GatewayMCPServerResult> fetchMcpServers(Gateway gateway, int page, int size) {
        ApsaraGatewayConfig cfg = gateway.getApsaraGatewayConfig();
        if (cfg == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Apsara gateway config is null");
        }
        
        ApsaraStackGatewayClient client = new ApsaraStackGatewayClient(cfg);
        try {
            // 使用SDK获取MCP服务器列表
            ListMcpServersResponse response = client.ListMcpServers(gateway.getGatewayId(), page, size);
            
            if (response.getBody() == null) {
                return PageResult.of(new ArrayList<>(), page, size, 0);
            }
            
            // 修复类型不兼容问题
            // 根据错误信息，getData()返回的是ListMcpServersResponseBodyData类型
            ListMcpServersResponseBody.ListMcpServersResponseBodyData data = 
                response.getBody().getData();
            
            if (data == null) {
                return PageResult.of(new ArrayList<>(), page, size, 0);
            }
            
            int total = data.getTotal() != null ? data.getTotal() : 0;
            
            List<GatewayMCPServerResult> items = new ArrayList<>();
            // 使用工厂方法直接从SDK的record创建AdpMCPServerResult
            if (data.getRecords() != null) {
                items = data.getRecords().stream()
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
    public PageResult<? extends GatewayModelAPIResult> fetchModelAPIs(Gateway gateway, int page, int size) {
        return null;
    }

    @Override
    public String fetchAPIConfig(Gateway gateway, Object config) {
        throw new UnsupportedOperationException("Apsara gateway not implemented for API config export");
    }

    @Override
    public String fetchMcpConfig(Gateway gateway, Object conf) {
        ApsaraGatewayConfig config = gateway.getApsaraGatewayConfig();
        if (config == null) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "Apsara Gateway 配置缺失");
        }

        // 从conf参数中获取APIGRefConfig
        APIGRefConfig apigRefConfig = (APIGRefConfig) conf;
        if (apigRefConfig == null || apigRefConfig.getMcpServerName() == null) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "MCP Server 名称缺失");
        }

        ApsaraStackGatewayClient client = new ApsaraStackGatewayClient(config);
        try {
            // 使用SDK获取MCP Server详情
            GetMcpServerResponse response = client.GetMcpServer(
                gateway.getGatewayId(), 
                apigRefConfig.getMcpServerName()
            );
            
            if (response.getBody() == null || response.getBody().getData() == null) {
                throw new BusinessException(ErrorCode.GATEWAY_ERROR, "MCP Server不存在");
            }
            
            GetMcpServerResponseBody.GetMcpServerResponseBodyData data = response.getBody().getData();
            
            return convertToMCPConfig(data, gateway.getGatewayId(), config);
        } catch (Exception e) {
            log.error("Error fetching Apsara MCP config for server: {}", apigRefConfig.getMcpServerName(), e);
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
        return "";
    }

    /**
     * 将Apsara MCP Server详情转换为MCPConfigResult格式
     */
    private String convertToMCPConfig(GetMcpServerResponseBody.GetMcpServerResponseBodyData data, 
                                      String gwInstanceId, ApsaraGatewayConfig config) {
        MCPConfigResult mcpConfig = new MCPConfigResult();
        mcpConfig.setMcpServerName(data.getName());

        // 设置MCP Server配置
        MCPConfigResult.MCPServerConfig serverConfig = new MCPConfigResult.MCPServerConfig();
        serverConfig.setPath("/" + data.getName());
        
        // 获取网关实例访问信息并设置域名信息
        List<DomainResult> domains = getGatewayAccessDomains(gwInstanceId, config);
        if (domains != null && !domains.isEmpty()) {
            serverConfig.setDomains(domains);
        } else {
            // 如果无法获取网关访问信息，则使用原有的services信息作为备选
            if (data.getServices() != null && !data.getServices().isEmpty()) {
                List<DomainResult> fallbackDomains = data.getServices().stream()
                        .map(service -> DomainResult.builder()
                                .domain(service.getName() + ":" + service.getPort())
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
        meta.setSource(GatewayType.APSARA_GATEWAY.name());
        meta.setCreateFromType(data.getType());
        mcpConfig.setMeta(meta);

        return JSONUtil.toJsonStr(mcpConfig);
    }
    
    /**
     * 获取网关实例的访问信息并构建域名列表
     */
    private List<DomainResult> getGatewayAccessDomains(String gwInstanceId, ApsaraGatewayConfig config) {
        ApsaraStackGatewayClient client = new ApsaraStackGatewayClient(config);
        try {
            GetInstanceInfoResponse response = client.GetInstance(gwInstanceId);
            
            if (response.getBody() == null || response.getBody().getData() == null) {
                log.warn("Gateway instance not found, instanceId={}", gwInstanceId);
                return null;
            }
            
            GetInstanceInfoResponseBody.GetInstanceInfoResponseBodyData instanceData = response.getBody().getData();
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
    
    /**
     * 根据网关实例访问信息构建域名列表
     */
    private List<DomainResult> buildDomainsFromAccessModes(
            List<GetInstanceInfoResponseBody.GetInstanceInfoResponseBodyDataAccessMode> accessModes) {
        List<DomainResult> domains = new ArrayList<>();
        if (accessModes == null || accessModes.isEmpty()) {
            return domains;
        }
        
        GetInstanceInfoResponseBody.GetInstanceInfoResponseBodyDataAccessMode accessMode = accessModes.get(0);

        // 1) LoadBalancer: externalIps:80
        if ("LoadBalancer".equalsIgnoreCase(accessMode.getAccessModeType())) {
            if (accessMode.getExternalIps() != null && !accessMode.getExternalIps().isEmpty()) {
                for (String externalIp : accessMode.getExternalIps()) {
                    if (externalIp == null || externalIp.isEmpty()) {
                        continue;
                    }
                    DomainResult domain = DomainResult.builder()
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
                            DomainResult domain = DomainResult.builder()
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
        if (domains.isEmpty() && accessMode.getExternalIps() != null && !accessMode.getExternalIps().isEmpty()) {
            for (String externalIp : accessMode.getExternalIps()) {
                if (externalIp == null || externalIp.isEmpty()) {
                    continue;
                }
                DomainResult domain = DomainResult.builder()
                        .domain(externalIp + ":80")
                        .protocol("http")
                        .build();
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

        ApsaraStackGatewayClient client = new ApsaraStackGatewayClient(cfg);
        
        try {
            // 使用SDK的ListInstances方法获取网关实例列表
            // brokerEngineType默认为HIGRESS
            String brokerEngineType = p.getBrokerEngineType() != null ? p.getBrokerEngineType() : "HIGRESS";
            ListInstancesResponse response = client.ListInstances(page, size, brokerEngineType);
            
            if (response.getBody() == null || response.getBody().getData() == null) {
                return PageResult.of(new ArrayList<>(), page, size, 0);
            }
            
            ListInstancesResponseBody.ListInstancesResponseBodyData data = 
                response.getBody().getData();
            
            int total = data.getTotal() != null ? data.getTotal() : 0;
            
            List<GatewayResult> list = new ArrayList<>();
            if (data.getRecords() != null) {
                for (ListInstancesResponseBody.ListInstancesResponseBodyDataRecords record : data.getRecords()) {
                    GatewayResult gr = GatewayResult.builder()
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
    public String createConsumer(Consumer consumer, ConsumerCredential credential, GatewayConfig config) {
        ApsaraGatewayConfig apsaraConfig = config.getApsaraGatewayConfig();
        if (apsaraConfig == null) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "Apsara Gateway配置缺失");
        }

        Gateway gateway = config.getGateway();
        if (gateway == null || gateway.getGatewayId() == null) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "网关实例ID缺失");
        }

        ApsaraStackGatewayClient client = new ApsaraStackGatewayClient(apsaraConfig);
        try {
            // 从凭证中获取API Key
            String key = null;
            if (credential.getApiKeyConfig() != null && 
                credential.getApiKeyConfig().getCredentials() != null &&
                !credential.getApiKeyConfig().getCredentials().isEmpty()) {
                key = credential.getApiKeyConfig().getCredentials().get(0).getApiKey();
            }

            log.info("Creating consumer in Apsara gateway: gatewayId={}, consumerName={}, hasApiKey={}",
                gateway.getGatewayId(), consumer.getName(), key != null);

            CreateAppResponse response = client.CreateApp(
                gateway.getGatewayId(),
                consumer.getName(),
                key,
                5  // authType: 5 = API_KEY
            );

            // 检查响应
            if (response.getBody() != null) {
                log.info("CreateApp response: code={}, message={}, data={}",
                    response.getBody().getCode(),
                    response.getBody().getMsg(),
                    response.getBody().getData());

                // 检查状态码
                if (response.getBody().getCode() != null && response.getBody().getCode() == 200) {
                    if (response.getBody().getData() != null) {
                        // SDK返回的data就是appName，直接返回
                        return extractConsumerIdFromResponse(response.getBody().getData(), consumer.getName());
                    }
                    // 即使data为null，如果状态码是200，也认为创建成功，使用consumer name作为ID
                    log.warn("CreateApp succeeded but data is null, using consumer name as ID");
                    return consumer.getName();
                }
                // 状态码不是200，抛出详细错误信息
                String errorMsg = String.format("Failed to create consumer in Apsara gateway: code=%d, message=%s",
                    response.getBody().getCode(), response.getBody().getMsg());
                throw new BusinessException(ErrorCode.GATEWAY_ERROR, errorMsg);
            }
            throw new BusinessException(ErrorCode.GATEWAY_ERROR, "Failed to create consumer in Apsara gateway: empty response");
        } catch (BusinessException e) {
            log.error("Business error creating consumer in Apsara gateway", e);
            throw e;
        } catch (Exception e) {
            log.error("Error creating consumer in Apsara gateway", e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, 
                "Error creating consumer in Apsara gateway: " + e.getMessage());
        } finally {
            client.close();
        }
    }
    
    /**
     * 从SDK响应中提取消费者ID
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
    public void updateConsumer(String consumerId, ConsumerCredential credential, GatewayConfig config) {
        ApsaraGatewayConfig apsaraConfig = config.getApsaraGatewayConfig();
        if (apsaraConfig == null) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "Apsara Gateway配置缺失");
        }

        Gateway gateway = config.getGateway();
        if (gateway == null || gateway.getGatewayId() == null) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "网关实例ID缺失");
        }

        ApsaraStackGatewayClient client = new ApsaraStackGatewayClient(apsaraConfig);
        try {
            // 从凭证中提取API Key
            String apiKey = null;
            if (credential != null
                    && credential.getApiKeyConfig() != null
                    && credential.getApiKeyConfig().getCredentials() != null
                    && !credential.getApiKeyConfig().getCredentials().isEmpty()) {
                apiKey = credential.getApiKeyConfig().getCredentials().get(0).getApiKey();
            }

            // 明确各参数含义，避免重复使用consumerId带来的混淆
            String appId = consumerId;  // 应用ID，用于标识要更新的应用
            String appName = consumerId;  // 应用名称，通常与appId保持一致
            String description = "Consumer managed by Portal";  // 语义化的描述信息
            Integer authType = 5;  // 认证类型：API_KEY
            Boolean enable = true;  // 启用状态

            ModifyAppResponse response = client.ModifyApp(
                gateway.getGatewayId(),
                appId,
                appName,
                apiKey,
                authType,
                description,
                enable
            );

            if (response.getBody() != null && response.getBody().getCode() == 200) {
                log.info("Successfully updated consumer {} in Apsara gateway instance {}", 
                    consumerId, gateway.getGatewayId());
                return;
            }
            throw new BusinessException(ErrorCode.GATEWAY_ERROR, 
                "更新Apsara网关消费者失败");
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error updating consumer {} in Apsara gateway instance {}", 
                consumerId, gateway != null ? gateway.getGatewayId() : "unknown", e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, 
                "更新Apsara网关消费者异常: " + e.getMessage());
        } finally {
            client.close();
        }
    }

    @Override
    public void deleteConsumer(String consumerId, GatewayConfig config) {
        ApsaraGatewayConfig apsaraConfig = config.getApsaraGatewayConfig();
        if (apsaraConfig == null) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "Apsara Gateway配置缺失");
        }

        Gateway gateway = config.getGateway();
        if (gateway == null || gateway.getGatewayId() == null) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "网关实例ID缺失");
        }

        ApsaraStackGatewayClient client = new ApsaraStackGatewayClient(apsaraConfig);
        try {
            BatchDeleteAppResponse response = client.DeleteApp(
                gateway.getGatewayId(),
                consumerId
            );

            if (response.getBody() != null && response.getBody().getCode() == 200) {
                log.info("Successfully deleted consumer {} from Apsara gateway instance {}", 
                    consumerId, gateway.getGatewayId());
                return;
            }
            throw new BusinessException(ErrorCode.GATEWAY_ERROR, 
                "删除Apsara网关消费者失败");
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error deleting consumer {} from Apsara gateway instance {}", 
                consumerId, gateway != null ? gateway.getGatewayId() : "unknown", e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, 
                "删除Apsara网关消费者异常: " + e.getMessage());
        } finally {
            client.close();
        }
    }

    @Override
    public boolean isConsumerExists(String consumerId, GatewayConfig config) {
        ApsaraGatewayConfig apsaraConfig = config.getApsaraGatewayConfig();
        if (apsaraConfig == null) {
            log.warn("Apsara Gateway配置缺失，无法检查消费者存在性");
            return false;
        }

        Gateway gateway = config.getGateway();
        if (gateway == null || gateway.getGatewayId() == null) {
            log.warn("网关实例ID缺失，无法检查消费者存在性");
            return false;
        }

        ApsaraStackGatewayClient client = new ApsaraStackGatewayClient(apsaraConfig);
        try {
            // 获取所有应用列表，然后在客户端筛选
            ListAppsByGwInstanceIdResponse response = client.ListAppsByGwInstanceId(
                gateway.getGatewayId(),
                (Integer) null  // serviceType 为 null，获取所有类型
            );

            // data字段直接是List，不是包含records的对象
            if (response.getBody() != null && response.getBody().getData() != null) {
                // 遍历应用列表，查找匹配的consumerId（appName）
                return response.getBody().getData().stream()
                    .anyMatch(app -> consumerId.equals(app.getAppName()));
            }
            return false;
        } catch (Exception e) {
            log.warn("检查Apsara网关消费者存在性失败: consumerId={}", consumerId, e);
            return false;
        } finally {
            client.close();
        }
    }

    @Override
    public ConsumerAuthConfig authorizeConsumer(Gateway gateway, String consumerId, Object refConfig) {
        ApsaraGatewayConfig apsaraConfig = gateway.getApsaraGatewayConfig();
        if (apsaraConfig == null) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "Apsara Gateway配置缺失");
        }

        // 解析MCP Server配置
        APIGRefConfig apigRefConfig = (APIGRefConfig) refConfig;
        if (apigRefConfig == null || apigRefConfig.getMcpServerName() == null) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "MCP Server名称缺失");
        }

        ApsaraStackGatewayClient client = new ApsaraStackGatewayClient(apsaraConfig);
        try {
            // 调用SDK添加MCP Server消费者授权
            AddMcpServerConsumersResponse response = client.AddMcpServerConsumers(
                gateway.getGatewayId(),
                apigRefConfig.getMcpServerName(),
                Collections.singletonList(consumerId)  // consumerId就是appName
            );

            if (response.getBody() != null && response.getBody().getCode() == 200) {
                log.info("Successfully authorized consumer {} to MCP server {}", 
                    consumerId, apigRefConfig.getMcpServerName());
                
                // 构建授权配置返回结果
                AdpAIAuthConfig authConfig = AdpAIAuthConfig.builder()
                        .mcpServerName(apigRefConfig.getMcpServerName())
                        .consumerId(consumerId)
                        .gwInstanceId(gateway.getGatewayId())
                        .build();
                
                return ConsumerAuthConfig.builder()
                        .adpAIAuthConfig(authConfig)
                        .build();
            }
            throw new BusinessException(ErrorCode.GATEWAY_ERROR, 
                "Failed to authorize consumer to MCP server");
        } catch (BusinessException e) {
            log.error("Business error authorizing consumer to MCP server", e);
            throw e;
        } catch (Exception e) {
            log.error("Error authorizing consumer {} to MCP server {}", 
                consumerId, apigRefConfig.getMcpServerName(), e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, 
                "Error authorizing consumer to MCP server: " + e.getMessage());
        } finally {
            client.close();
        }
    }

    @Override
    public HttpApiApiInfo fetchAPI(Gateway gateway, String apiId) {
        throw new UnsupportedOperationException("Apsara gateway not implemented for fetch api");
    }

    @Override
    public void revokeConsumerAuthorization(Gateway gateway, String consumerId, ConsumerAuthConfig authConfig) {
        AdpAIAuthConfig adpAIAuthConfig = authConfig.getAdpAIAuthConfig();
        if (adpAIAuthConfig == null) {
            log.warn("Apsara 授权配置为空，无法撤销授权");
            return;
        }

        ApsaraGatewayConfig apsaraConfig = gateway.getApsaraGatewayConfig();
        if (apsaraConfig == null) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "Apsara Gateway配置缺失");
        }

        ApsaraStackGatewayClient client = new ApsaraStackGatewayClient(apsaraConfig);
        try {
            // 调用SDK删除MCP Server消费者授权
            DeleteMcpServerConsumersResponse response = client.DeleteMcpServerConsumers(
                gateway.getGatewayId(),
                adpAIAuthConfig.getMcpServerName(),
                Collections.singletonList(consumerId)  // consumerId就是appName
            );

            if (response.getBody() != null && response.getBody().getCode() == 200) {
                log.info("Successfully revoked consumer {} authorization from MCP server {}", 
                    consumerId, adpAIAuthConfig.getMcpServerName());
                return;
            }
            
            // 获取错误信息
            String message = response.getBody() != null ? response.getBody().getMsg() : "Unknown error";
            
            // 如果是资源不存在（已被删除），只记录警告，不抛异常
            if (message != null && (message.contains("not found") || message.contains("不存在") 
                    || message.contains("NotFound"))) {
                log.warn("Consumer authorization already removed or not found: consumerId={}, mcpServer={}, message={}",
                    consumerId, adpAIAuthConfig.getMcpServerName(), message);
                return;
            }
            
            // 其他错误抛出异常
            String errorMsg = "Failed to revoke consumer authorization from MCP server: " + message;
            log.error(errorMsg);
            throw new BusinessException(ErrorCode.GATEWAY_ERROR, errorMsg);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error revoking consumer {} authorization from MCP server {}", 
                consumerId, adpAIAuthConfig.getMcpServerName(), e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, 
                "Error revoking consumer authorization: " + e.getMessage());
        } finally {
            client.close();
        }
    }

    @Override
    public GatewayType getGatewayType() {
        return GatewayType.APSARA_GATEWAY;
    }

    @Override
    public String getDashboard(Gateway gateway, String type) {
        return null;
    }

    @Override
    public List<String> fetchGatewayIps(Gateway gateway) {
        return Collections.emptyList();
    }
}