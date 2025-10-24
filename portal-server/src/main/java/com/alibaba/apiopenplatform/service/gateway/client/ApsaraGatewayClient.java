package com.alibaba.apiopenplatform.service.gateway.client;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.alibaba.apiopenplatform.core.exception.BusinessException;
import com.alibaba.apiopenplatform.core.exception.ErrorCode;
import com.alibaba.apiopenplatform.support.gateway.ApsaraGatewayConfig;
import com.aliyuncs.CommonRequest;
import com.aliyuncs.CommonResponse;
import com.aliyuncs.DefaultAcsClient;
import com.aliyuncs.IAcsClient;
import com.aliyuncs.exceptions.ClientException;
import com.aliyuncs.http.FormatType;
import com.aliyuncs.http.MethodType;
import com.aliyuncs.profile.DefaultProfile;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.util.function.Function;

@Slf4j
public class ApsaraGatewayClient extends GatewayClient {

    private final ApsaraGatewayConfig config;
    private final IAcsClient client;

    public ApsaraGatewayClient(ApsaraGatewayConfig config) {
        this.config = config;
        this.client = createClient(config);
    }

    private IAcsClient createClient(ApsaraGatewayConfig config) {
        DefaultProfile profile = DefaultProfile.getProfile(
                config.getRegionId(),
                config.getAccessKeyId(),
                config.getAccessKeySecret());
        if (config.getSecurityToken() != null && !config.getSecurityToken().isEmpty()) {
            profile = DefaultProfile.getProfile(
                    config.getRegionId(),
                    config.getAccessKeyId(),
                    config.getAccessKeySecret(),
                    config.getSecurityToken());
        }
        return new DefaultAcsClient(profile);
    }

    @Override
    public void close() {
        client.shutdown();
    }

    public <E> E execute(String uri, MethodType methodType, JSONObject body, Function<JSONObject, E> converter) {
        CommonRequest request = new CommonRequest();
        request.setSysDomain(config.getDomain());
        request.setSysProduct(config.getProduct());
        request.setSysVersion(config.getVersion());
        request.setSysUriPattern(uri);
        request.setSysMethod(methodType);
        // Ensure server returns JSON
        request.putHeadParameter("Accept", "application/json");

        if (body != null) {
            byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
            request.setHttpContent(bytes, StandardCharsets.UTF_8.name(), FormatType.JSON);
        }

        if (config.getXAcsCallerSdkSource() != null) {
            request.putHeadParameter("x-acs-caller-sdk-source", config.getXAcsCallerSdkSource());
        }
        if (config.getXAcsResourceGroupId() != null) {
            request.putHeadParameter("x-acs-resourcegroupid", config.getXAcsResourceGroupId());
        }
        if (config.getXAcsOrganizationId() != null) {
            request.putHeadParameter("x-acs-organizationid", config.getXAcsOrganizationId());
        }
        if (config.getXAcsCallerType() != null) {
            request.putHeadParameter("x-acs-caller-type", config.getXAcsCallerType());
        }

        try {
            CommonResponse response = client.getCommonResponse(request);
            JSONObject data = JSONUtil.parseObj(response.getData());
            return converter.apply(data);
        } catch (ClientException e) {
            log.error("Error executing Apsara request", e);
            throw new BusinessException(ErrorCode.GATEWAY_ERROR, e, 
                "Failed to communicate with Apsara gateway: " + e.getMessage());
        }
    }
}
