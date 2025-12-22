/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.alibaba.himarket.config;

import com.alibaba.himarket.support.enums.SlsAuthType;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** SLS日志服务配置 */
@Data
@Configuration
@ConfigurationProperties(prefix = "sls")
public class SlsConfig {

    /** 认证方式:STS 或 AK_SK */
    private SlsAuthType authType = SlsAuthType.AK_SK;

    /** SLS服务端点 例如: cn-hangzhou.log.aliyuncs.com */
    private String endpoint;

    /**
     * 检查SLS配置是否有效
     *
     * @return true表示配置有效,false表示配置无效
     */
    public boolean isConfigured() {
        return endpoint != null && !endpoint.trim().isEmpty();
    }

    /** 访问密钥ID（仅当authType=AK_SK时使用） */
    private String accessKeyId;

    /** 访问密钥（仅当authType=AK_SK时使用） */
    private String accessKeySecret;

    /** 默认Project名称（可选） */
    private String defaultProject;

    /** 默认Logstore名称（可选） */
    private String defaultLogstore;

    /** AliyunLogConfig CR配置 */
    private AliyunLogConfigProperties aliyunLogConfig = new AliyunLogConfigProperties();

    /** AliyunLogConfig CR配置属性 */
    @Data
    public static class AliyunLogConfigProperties {
        /** CR所在的namespace */
        private String namespace = "apigateway-system";

        /** CR的名称 */
        private String crName = "apigateway-access-log";
    }
}
