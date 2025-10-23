package com.alibaba.apiopenplatform.service.gateway.client;

import com.alibaba.apiopenplatform.entity.Gateway;
import com.alibaba.apiopenplatform.support.gateway.ApsaraGatewayConfig;
import com.aliyun.teaopenapi.models.Config;
import com.aliyun.apsarastack.csb220230206.Client;
import com.aliyun.apsarastack.csb220230206.models.*;
import com.aliyun.teautil.models.RuntimeOptions;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

@Slf4j
public class ApsaraStackGatewayClient extends GatewayClient {

    private final ApsaraGatewayConfig config;
    private final Client client;
    private final RuntimeOptions runtime;

    public ApsaraStackGatewayClient(ApsaraGatewayConfig config) {
        this.config = config;
        this.client = createClient(config);
        this.runtime = new RuntimeOptions();
        // 根据示例设置运行时参数
        this.runtime.ignoreSSL = true;
        this.runtime.setConnectTimeout(3000);
        this.runtime.setReadTimeout(30000);
    }

    public static ApsaraStackGatewayClient fromGateway(Gateway gateway) {
        return new ApsaraStackGatewayClient(gateway.getApsaraGatewayConfig());
    }

    private Client createClient(ApsaraGatewayConfig config) {
        try {
            Config clientConfig = new Config()
                    .setRegionId(config.getRegionId())
                    .setAccessKeyId(config.getAccessKeyId())
                    .setAccessKeySecret(config.getAccessKeySecret());
            
            // 设置endpoint
            clientConfig.endpoint = config.getDomain();
            
            return new Client(clientConfig);
        } catch (Exception e) {
            log.error("Error creating ApsaraStack client", e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public void close() {
        // Client doesn't need explicit closing
    }

    /**
     * 构建ApsaraStack请求头
     * 根据配置设置必要的业务头信息
     */
    private Map<String, String> buildRequestHeaders() {
        Map<String, String> headers = new HashMap<>();
        if (config.getXAcsCallerSdkSource() != null) {
            headers.put("x-acs-caller-sdk-source", config.getXAcsCallerSdkSource());
        }
        if (config.getXAcsResourceGroupId() != null) {
            headers.put("x-acs-resourcegroupid", config.getXAcsResourceGroupId());
        }
        if (config.getXAcsOrganizationId() != null) {
            headers.put("x-acs-organizationid", config.getXAcsOrganizationId());
        }
        if (config.getXAcsCallerType() != null) {
            headers.put("x-acs-caller-type", config.getXAcsCallerType());
        }
        // 角色id
        if (config.getXAcsRoleId() != null) {
            headers.put("x-acs-roleId", config.getXAcsRoleId());
        }
        return headers;
    }

    public ListRoutesResponse ListRoutes(String instanceId, int current, int size) {
        try {
            ListRoutesRequest request = new ListRoutesRequest();
            request.setCurrent(current);
            request.setSize(size);
            request.setGwInstanceId(instanceId);
            
            return client.listRoutesWithOptions(request, buildRequestHeaders(), runtime);
        } catch (Exception e) {
            log.error("Error listing routes", e);
            throw new RuntimeException(e);
        }
    }
    
    public ListMcpServersResponse ListMcpServers(String instanceId, int current, int size) {
        try {
            ListMcpServersRequest request = new ListMcpServersRequest();
            request.setCurrent(current);
            request.setSize(size);
            request.setGwInstanceId(instanceId);
            
            return client.listMcpServersWithOptions(request, buildRequestHeaders(), runtime);
        } catch (Exception e) {
            log.error("Error listing MCP servers", e);
            throw new RuntimeException(e);
        }
    }
    
    public ListInstancesResponse ListInstances(int current, int size, String brokerEngineType) {
        try {
            ListInstancesRequest request = new ListInstancesRequest();
            request.setCurrent(current);
            request.setSize(size);
            if (brokerEngineType != null && !brokerEngineType.isEmpty()) {
                request.setBrokerEngineType(brokerEngineType);
            }
            
            return client.listInstancesWithOptions(request, buildRequestHeaders(), runtime);
        } catch (Exception e) {
            log.error("Error listing instances", e);
            throw new RuntimeException(e);
        }
    }
    
    /**
     * 获取MCP Server详情
     */
    public GetMcpServerResponse GetMcpServer(String gwInstanceId, String mcpServerName) {
        try {
            GetMcpServerRequest request = new GetMcpServerRequest();
            request.setGwInstanceId(gwInstanceId);
            request.setMcpServerName(mcpServerName);
            
            return client.getMcpServerWithOptions(request, buildRequestHeaders(), runtime);
        } catch (Exception e) {
            log.error("Error getting MCP server", e);
            throw new RuntimeException(e);
        }
    }
    
    /**
     * 获取网关实例详情
     */
    public GetInstanceInfoResponse GetInstance(String gwInstanceId) {
        try {
            GetInstanceInfoRequest request = new GetInstanceInfoRequest();
            request.setGwInstanceId(gwInstanceId);
            
            return client.getInstanceInfoWithOptions(request, buildRequestHeaders(), runtime);
        } catch (Exception e) {
            log.error("Error getting instance", e);
            throw new RuntimeException(e);
        }
    }
    
    /**
     * 创建应用（Consumer）
     */
    public CreateAppResponse CreateApp(String gwInstanceId, String appName, String key, Integer authType) {
        try {
            CreateAppRequest request = new CreateAppRequest();
            request.setGwInstanceId(gwInstanceId);
            request.setAppName(appName);
            request.setKey(key);
            request.setAuthType(authType);
            
            return client.createAppWithOptions(request, buildRequestHeaders(), runtime);
        } catch (Exception e) {
            log.error("Error creating app", e);
            throw new RuntimeException(e);
        }
    }
    
    /**
     * 更新应用（Consumer）
     */
    public ModifyAppResponse ModifyApp(String gwInstanceId, String appId, String appName, String key, Integer authType, String description, Boolean enable) {
        try {
            ModifyAppRequest request = new ModifyAppRequest();
            request.setGwInstanceId(gwInstanceId);
            request.setAppId(appId);
            request.setAppName(appName);
            if (key != null) {
                request.setKey(key);
            }
            request.setAuthType(authType);
            if (description != null) {
                request.setDescription(description);
            }
            if (enable != null) {
                // SDK使用isDisable字段，需要取反
                request.setIsDisable(!enable);
            }
            
            return client.modifyAppWithOptions(request, buildRequestHeaders(), runtime);
        } catch (Exception e) {
            log.error("Error modifying app", e);
            throw new RuntimeException(e);
        }
    }
    
    /**
     * 删除应用（Consumer）
     */
    public BatchDeleteAppResponse DeleteApp(String gwInstanceId, String appId) {
        try {
            BatchDeleteAppRequest request = new BatchDeleteAppRequest();
            request.setGwInstanceId(gwInstanceId);
            request.setAppIds(java.util.Collections.singletonList(appId));
            
            return client.batchDeleteAppWithOptions(request, buildRequestHeaders(), runtime);
        } catch (Exception e) {
            log.error("Error deleting app", e);
            throw new RuntimeException(e);
        }
    }
    
    /**
     * 查询应用列表（用于检查Consumer是否存在）
     * @param gwInstanceId 网关实例ID
     * @param serviceType 服务类型（可选）
     */
    public ListAppsByGwInstanceIdResponse ListAppsByGwInstanceId(String gwInstanceId, Integer serviceType) {
        try {
            ListAppsByGwInstanceIdRequest request = new ListAppsByGwInstanceIdRequest();
            request.setGwInstanceId(gwInstanceId);
            if (serviceType != null) {
                request.setServiceType(serviceType);
            }
            
            return client.listAppsByGwInstanceIdWithOptions(request, buildRequestHeaders(), runtime);
        } catch (Exception e) {
            log.error("Error listing apps by instance", e);
            throw new RuntimeException(e);
        }
    }
    
    /**
     * 为MCP Server添加授权的消费者
     */
    public AddMcpServerConsumersResponse AddMcpServerConsumers(String gwInstanceId, String mcpServerName, java.util.List<String> consumers) {
        try {
            AddMcpServerConsumersRequest request = new AddMcpServerConsumersRequest();
            request.setGwInstanceId(gwInstanceId);
            request.setMcpServerName(mcpServerName);
            request.setConsumers(consumers);
            
            return client.addMcpServerConsumersWithOptions(request, buildRequestHeaders(), runtime);
        } catch (Exception e) {
            log.error("Error adding MCP server consumers", e);
            throw new RuntimeException(e);
        }
    }
    
    /**
     * 删除MCP Server的授权消费者
     */
    public DeleteMcpServerConsumersResponse DeleteMcpServerConsumers(String gwInstanceId, String mcpServerName, java.util.List<String> consumers) {
        try {
            DeleteMcpServerConsumersRequest request = new DeleteMcpServerConsumersRequest();
            request.setGwInstanceId(gwInstanceId);
            request.setMcpServerName(mcpServerName);
            request.setConsumers(consumers);
            
            return client.deleteMcpServerConsumersWithOptions(request, buildRequestHeaders(), runtime);
        } catch (Exception e) {
            log.error("Error deleting MCP server consumers", e);
            throw new RuntimeException(e);
        }
    }
}