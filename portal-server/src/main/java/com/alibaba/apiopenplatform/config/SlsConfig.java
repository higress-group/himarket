package com.aliyun.csb.config;

import com.aliyun.csb.model.enums.SlsAuthType;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * SLS日志服务配置
 *
 * @author jingfeng.xjf
 * @date 2025/11/08
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "sls")
public class SlsConfig {

    /**
     * 认证方式：STS 或 AK_SK
     */
    private SlsAuthType authType = SlsAuthType.AK_SK;

    /**
     * SLS服务端点
     * 例如: cn-hangzhou.log.aliyuncs.com
     */
    private String endpoint;

    /**
     * 访问密钥ID（仅当authType=AK_SK时使用）
     */
    private String accessKeyId;

    /**
     * 访问密钥（仅当authType=AK_SK时使用）
     */
    private String accessKeySecret;

    /**
     * 默认Project名称（可选）
     */
    private String defaultProject;

    /**
     * 默认Logstore名称（可选）
     */
    private String defaultLogstore;

    /**
     * AliyunLogConfig CR配置
     */
    private AliyunLogConfigProperties aliyunLogConfig = new AliyunLogConfigProperties();

    /**
     * AliyunLogConfig CR配置属性
     */
    @Data
    public static class AliyunLogConfigProperties {
        /**
         * CR所在的namespace
         */
        private String namespace = "apigateway-system";

        /**
         * CR的名称
         */
        private String crName = "apigateway-access-log";
    }

}
