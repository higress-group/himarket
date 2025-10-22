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
    
    public ListInstancesResponse ListInstances(int current, int size) {
        try {
            ListInstancesRequest request = new ListInstancesRequest();
            request.setCurrent(current);
            request.setSize(size);
            
            return client.listInstancesWithOptions(request, buildRequestHeaders(), runtime);
        } catch (Exception e) {
            log.error("Error listing instances", e);
            throw new RuntimeException(e);
        }
    }
}