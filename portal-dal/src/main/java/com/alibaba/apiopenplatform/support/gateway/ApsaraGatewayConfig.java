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

package com.alibaba.apiopenplatform.support.gateway;

import com.alibaba.apiopenplatform.support.common.Encrypted;
import lombok.Data;

@Data
public class ApsaraGatewayConfig {

    private String regionId;

    private String accessKeyId;

    @Encrypted
    private String accessKeySecret;

    /** 可选：STS 临时凭证 */
    @Encrypted
    private String securityToken;

    /** POP 路由相关 */
    private String domain;
    private String product;
    private String version;

    /** 业务头 */
    private String xAcsOrganizationId;
    private String xAcsCallerSdkSource;
    private String xAcsResourceGroupId;
    private String xAcsCallerType;
    private String xAcsRoleId;

    public String buildUniqueKey() {
        // 包含所有影响身份识别和路由的字段，确保配置唯一性准确
        return String.format("%s:%s:%s:%s:%s:%s:%s:%s:%s:%s:%s:%s",
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
}
