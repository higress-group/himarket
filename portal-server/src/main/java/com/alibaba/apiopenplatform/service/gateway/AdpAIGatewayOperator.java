package com.alibaba.apiopenplatform.service.gateway;

import com.alibaba.apiopenplatform.core.exception.BusinessException;
import com.alibaba.apiopenplatform.core.exception.ErrorCode;
import com.alibaba.apiopenplatform.dto.params.gateway.QueryAPIGParam;
import com.alibaba.apiopenplatform.dto.params.gateway.QueryAdpAIGatewayParam;
import com.alibaba.apiopenplatform.dto.result.*;
import com.alibaba.apiopenplatform.entity.Consumer;
import com.alibaba.apiopenplatform.entity.ConsumerCredential;
import com.alibaba.apiopenplatform.entity.Gateway;
import com.alibaba.apiopenplatform.support.consumer.ConsumerAuthConfig;
import com.alibaba.apiopenplatform.support.enums.GatewayType;
import com.alibaba.apiopenplatform.support.gateway.AdpAIGatewayConfig;
import com.alibaba.apiopenplatform.service.gateway.client.AdpAIGatewayClient;
import com.alibaba.apiopenplatform.support.gateway.GatewayConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * ADP AI网关操作器
 */
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
    public PageResult<? extends GatewayMCPServerResult> fetchMcpServers(Gateway gateway, int page, int size) {
        AdpAIGatewayConfig config = gateway.getAdpAIGatewayConfig();
        if (config == null) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "ADP AI Gateway 配置缺失");
        }

        AdpAIGatewayClient client = new AdpAIGatewayClient(config);
        try {
            String url = client.getFullUrl("/mcpServer/listMcpServers");
            String requestBody = String.format("{\"current\": %d, \"size\": %d}", page, size);
            HttpEntity<String> requestEntity = client.createRequestEntity(requestBody);

            ResponseEntity<AdpMcpServerListResult> response = client.getRestTemplate().exchange(
                    url, HttpMethod.POST, requestEntity, AdpMcpServerListResult.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                AdpMcpServerListResult result = response.getBody();
                if (result.getCode() != null && result.getCode() == 200 && result.getData() != null) {
                    List<GatewayMCPServerResult> items = new ArrayList<>();
                    if (result.getData().getRecords() != null) {
                        items.addAll(result.getData().getRecords());
                    }
                    int total = result.getData().getTotal() != null ? result.getData().getTotal() : 0;
                    return PageResult.of(items, page, size, total);
                }
                String msg = result.getMessage() != null ? result.getMessage() : result.getMsg();
                throw new BusinessException(ErrorCode.GATEWAY_ERROR, msg);
            }
            throw new BusinessException(ErrorCode.GATEWAY_ERROR, "调用 ADP /mcpServer/listMcpServers 失败");
        } catch (Exception e) {
            log.error("Error fetching ADP MCP servers", e);
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
        return "";
    }

    @Override
    public String createConsumer(Consumer consumer, ConsumerCredential credential, GatewayConfig config) {
        return "";
    }

    @Override
    public void updateConsumer(String consumerId, ConsumerCredential credential, GatewayConfig config) {

    }

    @Override
    public void deleteConsumer(String consumerId, GatewayConfig config) {

    }

    @Override
    public ConsumerAuthConfig authorizeConsumer(Gateway gateway, String consumerId, Object refConfig) {
        return null;
    }

    @Override
    public void revokeConsumerAuthorization(Gateway gateway, String consumerId, ConsumerAuthConfig authConfig) {

    }

    @Override
    public APIResult fetchAPI(Gateway gateway, String apiId) {
        return null;
    }

    @Override
    public GatewayType getGatewayType() {
        return GatewayType.ADP_AI_GATEWAY;
    }

    @Override
    public PageResult<GatewayResult> fetchGateways(Object param, int page, int size) {
        if (!(param instanceof QueryAdpAIGatewayParam)) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "param");
        }
        return fetchGateways((QueryAdpAIGatewayParam) param, page, size);
    }

    public PageResult<GatewayResult> fetchGateways(QueryAdpAIGatewayParam param, int page, int size) {
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
                throw new BusinessException(ErrorCode.INVALID_PARAMETER, "Header认证方式下authHeaders不能为空");
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
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "不支持的认证类型: " + param.getAuthType());
        }

        AdpAIGatewayClient client = new AdpAIGatewayClient(config);
        try {
            String url = client.getFullUrl("/gatewayInstance/listInstances");
            String requestBody = String.format("{\"current\": %d, \"size\": %d}", page, size);
            HttpEntity<String> requestEntity = client.createRequestEntity(requestBody);

            ResponseEntity<AdpGatewayInstanceResult> response = client.getRestTemplate().exchange(
                    url, HttpMethod.POST, requestEntity, AdpGatewayInstanceResult.class);

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

    private PageResult<GatewayResult> convertToGatewayResult(AdpGatewayInstanceResult.AdpGatewayInstanceData data, int page, int size) {
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
                GatewayResult gateway = GatewayResult.builder()
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
}
