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

@Data
@Configuration
@ConfigurationProperties(prefix = "sls")
public class SlsConfig {

    /**
     * Authentication type, either STS or AK/SK.
     */
    private SlsAuthType authType = SlsAuthType.AK_SK;

    /**
     * SLS endpoint, for example cn-hangzhou.log.aliyuncs.com.
     */
    private String endpoint;

    /**
     * Checks whether the SLS configuration is valid.
     *
     * @return {@code true} if the configuration is valid
     */
    public boolean isConfigured() {
        return endpoint != null && !endpoint.trim().isEmpty();
    }

    /**
     * Access key ID, used only when authType is AK/SK.
     */
    private String accessKeyId;

    /**
     * Access key secret, used only when authType is AK/SK.
     */
    private String accessKeySecret;

    /**
     * Default project name, optional.
     */
    private String defaultProject;

    /**
     * Default Logstore name, optional.
     */
    private String defaultLogstore;

    /**
     * AliyunLogConfig custom resource settings.
     */
    private AliyunLogConfigProperties aliyunLogConfig = new AliyunLogConfigProperties();

    /**
     * AliyunLogConfig custom resource properties.
     */
    @Data
    public static class AliyunLogConfigProperties {
        /**
         * Namespace of the custom resource.
         */
        private String namespace = "apigateway-system";

        /**
         * Name of the custom resource.
         */
        private String crName = "apigateway-access-log";
    }
}
