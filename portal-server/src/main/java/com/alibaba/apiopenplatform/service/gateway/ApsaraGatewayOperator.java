package com.alibaba.apiopenplatform.service.gateway;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.alibaba.apiopenplatform.core.exception.BusinessException;
import com.alibaba.apiopenplatform.core.exception.ErrorCode;
import com.alibaba.apiopenplatform.dto.result.*;
import com.alibaba.apiopenplatform.entity.Gateway;
import com.alibaba.apiopenplatform.service.gateway.client.ApsaraStackGatewayClient;
import com.alibaba.apiopenplatform.support.consumer.ConsumerAuthConfig;
import com.alibaba.apiopenplatform.support.enums.GatewayType;
import com.alibaba.apiopenplatform.support.gateway.GatewayConfig;
import com.alibaba.apiopenplatform.support.gateway.ApsaraGatewayConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import com.aliyun.apsarastack.csb220230206.models.*;
import com.aliyuncs.http.MethodType;

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
        ApsaraStackGatewayClient client = getClient(gateway);
        try {
            // 使用SDK获取MCP服务器列表
            ListMcpServersResponse response = client.ListMcpServers(gateway.getGatewayId(), page, size);
            
            if (response.getBody() == null) {
                return PageResult.of(new java.util.ArrayList<>(), page, size, 0);
            }
            
            // 修复类型不兼容问题
            // 根据错误信息，getData()返回的是ListMcpServersResponseBodyData类型
            com.aliyun.apsarastack.csb220230206.models.ListMcpServersResponseBody.ListMcpServersResponseBodyData data = 
                response.getBody().getData();
            
            if (data == null) {
                return PageResult.of(new java.util.ArrayList<>(), page, size, 0);
            }
            
            int total = data.getTotal() != null ? data.getTotal() : 0;
            
            java.util.List<GatewayMCPServerResult> items = new java.util.ArrayList<>();
            // 修复records的类型引用
            if (data.getRecords() != null) {
                for (com.aliyun.apsarastack.csb220230206.models.ListMcpServersResponseBody.ListMcpServersResponseBodyDataRecords record : data.getRecords()) {
                    AdpMCPServerResult result = new AdpMCPServerResult();
                    // result.setMcpServerId(record.getMcpServerId());
                    result.setMcpServerName(record.getName());
                    // 根据需要设置其他字段
                    items.add(result);
                }
            }
            
            return PageResult.of(items, page, size, total);
        } catch (Exception e) {
            log.error("Error fetching MCP servers by Apsara", e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, e.getMessage());
        }
    }

    @Override
    public String fetchAPIConfig(Gateway gateway, Object config) {
        throw new UnsupportedOperationException("Apsara gateway not implemented for API config export");
    }

    @Override
    public String fetchMcpConfig(Gateway gateway, Object conf) {
        throw new UnsupportedOperationException("Apsara gateway not implemented for MCP config export");
    }

    @Override
    public PageResult<GatewayResult> fetchGateways(Object param, int page, int size) {
        // 将入参转换为配置
        com.alibaba.apiopenplatform.dto.params.gateway.QueryApsaraGatewayParam p =
                (com.alibaba.apiopenplatform.dto.params.gateway.QueryApsaraGatewayParam) param;
        
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
            ListInstancesResponse response = client.ListInstances(page, size);
            
            if (response.getBody() == null || response.getBody().getData() == null) {
                return PageResult.of(new java.util.ArrayList<>(), page, size, 0);
            }
            
            com.aliyun.apsarastack.csb220230206.models.ListInstancesResponseBody.ListInstancesResponseBodyData data = 
                response.getBody().getData();
            
            int total = data.getTotal() != null ? data.getTotal() : 0;
            
            java.util.List<GatewayResult> list = new java.util.ArrayList<>();
            if (data.getRecords() != null) {
                for (com.aliyun.apsarastack.csb220230206.models.ListInstancesResponseBody.ListInstancesResponseBodyDataRecords record : data.getRecords()) {
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
    public String createConsumer(com.alibaba.apiopenplatform.entity.Consumer consumer, com.alibaba.apiopenplatform.entity.ConsumerCredential credential, GatewayConfig config) {
        throw new UnsupportedOperationException("Apsara gateway not implemented for create consumer");
    }

    @Override
    public void updateConsumer(String consumerId, com.alibaba.apiopenplatform.entity.ConsumerCredential credential, GatewayConfig config) {
        throw new UnsupportedOperationException("Apsara gateway not implemented for update consumer");
    }

    @Override
    public void deleteConsumer(String consumerId, GatewayConfig config) {
        throw new UnsupportedOperationException("Apsara gateway not implemented for delete consumer");
    }

    @Override
    public boolean isConsumerExists(String consumerId, GatewayConfig config) {
        return true;
    }

    @Override
    public ConsumerAuthConfig authorizeConsumer(Gateway gateway, String consumerId, Object refConfig) {
        throw new UnsupportedOperationException("Apsara gateway not implemented for authorize consumer");
    }

    @Override
    public void revokeConsumerAuthorization(Gateway gateway, String consumerId, ConsumerAuthConfig authConfig) {
        throw new UnsupportedOperationException("Apsara gateway not implemented for revoke authorization");
    }

    @Override
    public APIResult fetchAPI(Gateway gateway, String apiId) {
        throw new UnsupportedOperationException("Apsara gateway not implemented for fetch api");
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
    protected ApsaraStackGatewayClient getClient(Gateway gateway) {
        return (ApsaraStackGatewayClient) super.getClient(gateway);
    }
}