package com.alibaba.apiopenplatform.service.gateway.factory;

import java.util.concurrent.TimeUnit;

import com.alibaba.apiopenplatform.config.SlsConfig;
import com.alibaba.apiopenplatform.core.exception.BusinessException;
import com.alibaba.apiopenplatform.core.exception.ErrorCode;
import com.alibaba.apiopenplatform.support.enums.SlsAuthType;

import com.aliyun.openservices.log.Client;
import com.aliyun.openservices.log.common.auth.Credentials;
import com.aliyun.openservices.log.common.auth.DefaultCredentials;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * SLS客户端工厂，根据配置文件自动选择STS或AK/SK认证方式
 *
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class SlsClientFactory {

    private final SlsConfig slsConfig;

    /**
     * STS模式的Client缓存（按userId缓存，25分钟过期）
     * Client创建成本较高，应该缓存复用
     */
    private final Cache<String, Client> stsClientCache = Caffeine.newBuilder()
        .maximumSize(1000)
        .expireAfterWrite(25, TimeUnit.MINUTES)
        .build();

    /**
     * 根据配置创建SLS客户端
     *
     * @param userId 用户ID（仅当authType=STS时需要）
     * @return SLS客户端
     */
    public Client createClient(String userId) {
        SlsAuthType authType = slsConfig.getAuthType();
        if (authType == SlsAuthType.STS) {
            return createClientWithSts(userId);
        } else {
            return createClientWithAkSk();
        }
    }

    /**
     * 使用STS方式创建SLS客户端（缓存Client对象）
     *
     * @param userId 用户ID
     * @return SLS客户端
     */
    private Client createClientWithSts(String userId) {
        throw new UnsupportedOperationException("STS not support");
    }

    /**
     * 使用AK/SK方式创建SLS客户端（使用配置文件中的AK/SK）
     *
     * @return SLS客户端
     */
    private Client createClientWithAkSk() {
        String accessKeyId = slsConfig.getAccessKeyId();
        String accessKeySecret = slsConfig.getAccessKeySecret();

        if (!StringUtils.hasText(accessKeyId) || !StringUtils.hasText(accessKeySecret)) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                "AccessKeyId and AccessKeySecret must be configured in application.yaml when authType is AK_SK");
        }

        String endpoint = getEffectiveEndpoint();

        try {
            Credentials credentials = new DefaultCredentials(accessKeyId, accessKeySecret);
            log.debug("Creating SLS client with AK/SK, endpoint: {}", endpoint);
            return new Client(endpoint, credentials, null);
        } catch (Exception e) {
            log.error("Failed to create SLS client with AK/SK", e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Failed to create SLS client with AK/SK");
        }
    }

    /**
     * 获取有效的endpoint
     *
     * @return 有效的endpoint
     */
    private String getEffectiveEndpoint() {
        String endpoint = slsConfig.getEndpoint();
        if (!StringUtils.hasText(endpoint)) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "SLS endpoint must be configured in application.yml");
        }
        return endpoint;
    }

}
