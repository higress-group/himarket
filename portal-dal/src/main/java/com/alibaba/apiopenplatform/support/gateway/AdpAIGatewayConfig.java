package com.alibaba.apiopenplatform.support.gateway;

import lombok.Data;

/**
 * ADP网关配置
 * 继承自APIGConfig，支持ADP网关特有的配置
 */
@Data
public class AdpAIGatewayConfig {

    /**
     * ADP网关的baseUrl，如果为空则使用默认的region构建
     */
    private String baseUrl;

    /**
     * ADP网关的端口
     */
    private Integer port;

    /**
     * ADP网关的认证种子
     */
    private String authSeed;
}
