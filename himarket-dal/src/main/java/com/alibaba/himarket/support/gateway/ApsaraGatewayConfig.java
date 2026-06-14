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

package com.alibaba.himarket.support.gateway;

import cn.hutool.core.util.StrUtil;
import com.alibaba.himarket.support.common.Encrypted;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class ApsaraGatewayConfig {

    private String regionId;

    private String accessKeyId;

    @Encrypted private String accessKeySecret;

    /**
     * Optional STS session token.
     */
    @Encrypted private String securityToken;

    /**
     * POP routing domain.
     */
    private String domain;

    private String product;
    private String version;

    /**
     * Organization header for Apsara requests.
     */
    @JsonProperty("xAcsOrganizationId")
    private String xAcsOrganizationId;

    @JsonProperty("xAcsCallerSdkSource")
    private String xAcsCallerSdkSource;

    @JsonProperty("xAcsResourceGroupId")
    private String xAcsResourceGroupId;

    @JsonProperty("xAcsCallerType")
    private String xAcsCallerType;

    @JsonProperty("xAcsRoleId")
    private String xAcsRoleId;

    public String buildUniqueKey() {
        return String.format(
                "%s:%s:%s:%s:%s:%s:%s:%s:%s:%s:%s:%s",
                accessKeyId,
                accessKeySecret,
                regionId,
                product,
                version,
                securityToken != null ? securityToken : "",
                domain != null ? domain : "",
                xAcsOrganizationId != null ? xAcsOrganizationId : "",
                xAcsCallerSdkSource != null ? xAcsCallerSdkSource : "",
                xAcsResourceGroupId != null ? xAcsResourceGroupId : "",
                xAcsCallerType != null ? xAcsCallerType : "",
                xAcsRoleId != null ? xAcsRoleId : "");
    }

    public boolean validate() {
        return StrUtil.isNotBlank(regionId)
                && StrUtil.isNotBlank(accessKeyId)
                && StrUtil.isNotBlank(accessKeySecret);
    }
}
