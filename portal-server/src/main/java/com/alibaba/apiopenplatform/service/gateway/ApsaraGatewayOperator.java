package com.alibaba.apiopenplatform.service.gateway;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.alibaba.apiopenplatform.core.exception.BusinessException;
import com.alibaba.apiopenplatform.core.exception.ErrorCode;
import com.alibaba.apiopenplatform.dto.result.*;
import com.alibaba.apiopenplatform.entity.Gateway;
import com.alibaba.apiopenplatform.service.gateway.client.ApsaraGatewayClient;
import com.alibaba.apiopenplatform.support.consumer.ConsumerAuthConfig;
import com.alibaba.apiopenplatform.support.enums.GatewayType;
import com.alibaba.apiopenplatform.support.gateway.GatewayConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import com.aliyuncs.http.MethodType;

@Service
@Slf4j
public class ApsaraGatewayOperator extends GatewayOperator<ApsaraGatewayClient> {

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
        ApsaraGatewayClient client = getClient(gateway);
        try {
            JSONObject body = JSONUtil.createObj()
                    .set("current", page)
                    .set("size", size)
                    .set("gwInstanceId", gateway.getGatewayId());
            return client.execute("/mcpServer/listMcpServers", MethodType.POST, body, data -> {
                if (!data.getJSONObject("data").getBool("asapiSuccess", false)) {
                    throw new BusinessException(ErrorCode.GATEWAY_ERROR, data.toString());
                }
                JSONObject inner = data.getJSONObject("data").getJSONObject("data");
                int total = inner.getInt("total", 0);
                java.util.List<com.alibaba.apiopenplatform.dto.result.AdpMCPServerResult> records =
                        JSONUtil.toList(inner.getJSONArray("records"), com.alibaba.apiopenplatform.dto.result.AdpMCPServerResult.class);
                java.util.List<GatewayMCPServerResult> items = new java.util.ArrayList<>(records);
                return PageResult.of(items, page, size, total);
            });
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
        ApsaraGatewayClient client = null;
        try {
            // 将入参转换为配置
            com.alibaba.apiopenplatform.dto.params.gateway.QueryApsaraGatewayParam p =
                    (com.alibaba.apiopenplatform.dto.params.gateway.QueryApsaraGatewayParam) param;
            com.alibaba.apiopenplatform.support.gateway.ApsaraGatewayConfig cfg = new com.alibaba.apiopenplatform.support.gateway.ApsaraGatewayConfig();
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

            client = new ApsaraGatewayClient(cfg);

            cn.hutool.json.JSONObject body = JSONUtil.createObj()
                    .set("current", page)
                    .set("size", size);

            return client.execute("/gatewayInstance/listInstances", MethodType.POST, body, data -> {
                cn.hutool.json.JSONObject d = data.getJSONObject("data");
                if (d == null) {
                    return PageResult.of(java.util.Collections.emptyList(), page, size, 0);
                }
                java.util.List<com.alibaba.apiopenplatform.dto.result.GatewayResult> list = new java.util.ArrayList<>();
                int total = d.getInt("total", 0);
                cn.hutool.json.JSONArray records = d.getJSONArray("records");
                if (records != null) {
                    for (Object obj : records) {
                        cn.hutool.json.JSONObject it = (cn.hutool.json.JSONObject) obj;
                        String id = it.getStr("gwInstanceId");
                        String name = it.getStr("name");
                        GatewayResult gr = GatewayResult.builder()
                                .gatewayId(id)
                                .gatewayName(name)
                                .gatewayType(GatewayType.APSARA_GATEWAY)
                                .build();
                        list.add(gr);
                    }
                }
                return PageResult.of(list, page, size, total);
            });
        } catch (Exception e) {
            log.error("Error listing Apsara gateways", e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, e.getMessage());
        } finally {
            if (client != null) {
                client.close();
            }
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
}
