package com.alibaba.apiopenplatform.service.gateway.client;

import cn.hutool.crypto.digest.DigestUtil;
import com.alibaba.apiopenplatform.core.exception.BusinessException;
import com.alibaba.apiopenplatform.core.exception.ErrorCode;
import com.alibaba.apiopenplatform.support.gateway.AdpAIGatewayConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

import java.util.Base64;
import java.util.function.Function;

/**
 * ADP AI网关客户端
 * 支持两种模式：
 * 1. SDK模式：使用阿里云SDK调用APIG服务
 * 2. HTTP模式：直接HTTP调用网关API
 */
@Slf4j
public class AdpAIGatewayClient extends GatewayClient {

    private final AdpAIGatewayConfig config;
    private final RestTemplate restTemplate;  // HTTP客户端

    public AdpAIGatewayClient(AdpAIGatewayConfig config) {
        this.config = config;
        this.restTemplate = new RestTemplate();
    }

    // 统一使用 HTTP 调用 ADP AI 网关，不再包含 SDK 模式逻辑

    // ==================== HTTP模式方法 ====================

    /**
     * 执行HTTP操作
     */
    public <E> E executeHTTP(Function<HttpEntity<String>, E> function) {
        if (restTemplate == null) {
            throw new BusinessException(ErrorCode.UNSUPPORTED_OPERATION, 
                "当前配置不支持HTTP模式，请配置baseUrl和port");
        }
        
        try {
            HttpEntity<String> requestEntity = createRequestEntity(null);
            return function.apply(requestEntity);
        } catch (Exception e) {
            log.error("Error executing ADP HTTP request", e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, e.getMessage());
        }
    }

    /**
     * 构建带必需鉴权头的请求实体
     */
    public HttpEntity<String> createRequestEntity(String body) {
        HttpHeaders headers = buildAuthHeaders();
        if (body == null) {
            return new HttpEntity<>(headers);
        }
        return new HttpEntity<>(body, headers);
    }

    /**
     * 生成必需的鉴权头
     */
    private HttpHeaders buildAuthHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        
        // 根据配置选择认证方式
        if (config.getAuthSeed() != null && !config.getAuthSeed().trim().isEmpty()) {
            // 使用 Seed 认证
            String authHeader = createBasicAuthHeader();
            headers.set("Authorization", authHeader);
        } else if (config.getAuthHeaders() != null && !config.getAuthHeaders().isEmpty()) {
            // 使用 Header 认证
            for (AdpAIGatewayConfig.AuthHeader authHeader : config.getAuthHeaders()) {
                if (authHeader.getKey() != null && authHeader.getValue() != null) {
                    headers.set(authHeader.getKey(), authHeader.getValue());
                }
            }
        } else {
            throw new BusinessException(ErrorCode.UNSUPPORTED_OPERATION, "ADP 认证配置缺失，请配置 authSeed 或 authHeaders");
        }
        
        return headers;
    }

    /**
     * 创建Basic认证头
     */
    private String createBasicAuthHeader() {
        String hashedAuthSeed = DigestUtil.sha256Hex(config.getAuthSeed());
        String credentials = "admin:" + hashedAuthSeed;
        String base64Credentials = Base64.getEncoder().encodeToString(credentials.getBytes());
        return "Basic " + base64Credentials;
    }

    // ==================== 通用方法 ====================

    /**
     * 获取基础URL
     */
    public String getBaseUrl() {
        return config.getBaseUrl();
    }

    /**
     * 获取端口
     */
    public Integer getPort() {
        return config.getPort();
    }

    /**
     * 构建完整的URL
     */
    public String getFullUrl(String path) {
        String baseUrl = config.getBaseUrl();
        
        // 如果 baseUrl 已经包含协议前缀，直接使用
        if (baseUrl.startsWith("http://") || baseUrl.startsWith("https://")) {
            return String.format("%s:%d%s", baseUrl, config.getPort(), path);
        }
        
        // 如果没有前缀，默认添加 http:// 前缀
        return String.format("http://%s:%d%s", baseUrl, config.getPort(), path);
    }

    /**
     * 获取RestTemplate（HTTP模式）
     */
    public RestTemplate getRestTemplate() {
        return restTemplate;
    }

    @Override
    public void close() {
        // RestTemplate 不需要手动关闭
    }
}
