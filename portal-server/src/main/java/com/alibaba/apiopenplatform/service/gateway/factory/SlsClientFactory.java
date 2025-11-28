package com.aliyun.csb.manager.sls;

import com.aliyun.csb.common.exception.BusinessException;
import com.aliyun.csb.config.SlsConfig;
import com.aliyun.csb.manager.edas.StsService;
import com.aliyun.csb.model.enums.SlsAuthType;
import com.aliyun.openservices.log.Client;
import com.aliyun.openservices.log.common.auth.Credentials;
import com.aliyun.openservices.log.common.auth.DefaultCredentails;
import com.aliyuncs.sts.model.v20150401.AssumeRoleWithServiceIdentityResponse;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.concurrent.TimeUnit;

/**
 * SLS客户端工厂，根据配置文件自动选择STS或AK/SK认证方式
 *
 * @author jingfeng.xjf
 * @date 2025/11/08
 */
@Component
@Slf4j
public class SlsClientFactory {

    @Autowired
    private SlsConfig slsConfig;

    @Autowired(required = false)
    private StsService stsService;

    /**
     * STS模式的Client缓存（按userId缓存，25分钟过期）
     * Client创建成本较高，应该缓存复用
     */
    private final Cache<String, Client> stsClientCache = CacheBuilder.newBuilder()
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
        if (stsService == null) {
            throw new BusinessException("STS service is not available, please check configuration");
        }
        if (!StringUtils.hasText(userId)) {
            throw new BusinessException("UserId is required for STS authentication");
        }

        try {
            return stsClientCache.get(userId, () -> {
                AssumeRoleWithServiceIdentityResponse.Credentials credentials = stsService.getStsToken(userId);
                Credentials slsCredentials = new DefaultCredentails(
                        credentials.getAccessKeyId(),
                        credentials.getAccessKeySecret(),
                        credentials.getSecurityToken()
                );
                String endpoint = getEffectiveEndpoint();
                log.info("Creating SLS client with STS for userId: {}, endpoint: {}", userId, endpoint);
                return new Client(endpoint, slsCredentials, null);
            });
        } catch (Exception e) {
            log.error("Failed to create SLS client with STS for userId: {}", userId, e);
            throw new BusinessException("Failed to create SLS client with STS, please check the organization's cloud resource access authorization status");
        }
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
            throw new BusinessException("AccessKeyId and AccessKeySecret must be configured in application.yml when authType is AK_SK");
        }

        String endpoint = getEffectiveEndpoint();

        try {
            Credentials credentials = new DefaultCredentails(accessKeyId, accessKeySecret);
            log.debug("Creating SLS client with AK/SK, endpoint: {}", endpoint);
            return new Client(endpoint, credentials, null);
        } catch (Exception e) {
            log.error("Failed to create SLS client with AK/SK", e);
            throw new BusinessException("Failed to create SLS client with AK/SK");
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
            throw new BusinessException("SLS endpoint must be configured in application.yml");
        }
        return endpoint;
    }

    /**
     * 获取SLS配置
     *
     * @return SLS配置
     */
    public SlsConfig getSlsConfig() {
        return slsConfig;
    }
}
