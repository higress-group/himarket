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

package com.alibaba.himarket.dto.params.gateway;

import com.alibaba.himarket.dto.converter.InputConverter;
import com.alibaba.himarket.support.gateway.ApsaraGatewayConfig;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class QueryApsaraGatewayParam implements InputConverter<ApsaraGatewayConfig> {

    @NotBlank(message = "RegionId不能为空")
    @JsonProperty("regionId")
    private String regionId;

    @NotBlank(message = "AccessKeyId不能为空")
    @JsonProperty("accessKeyId")
    private String accessKeyId;

    @NotBlank(message = "AccessKeySecret不能为空")
    @JsonProperty("accessKeySecret")
    private String accessKeySecret;

    @JsonProperty("securityToken")
    private String securityToken;

    @NotBlank(message = "Domain不能为空")
    @JsonProperty("domain")
    private String domain;

    @NotBlank(message = "Product不能为空")
    @JsonProperty("product")
    private String product;

    @NotBlank(message = "Version不能为空")
    @JsonProperty("version")
    private String version;

    @NotBlank(message = "x-acs-organizationid不能为空")
    @JsonProperty("xAcsOrganizationId")
    private String xAcsOrganizationId;

    @JsonProperty("xAcsCallerSdkSource")
    private String xAcsCallerSdkSource;

    @JsonProperty("xAcsResourceGroupId")
    private String xAcsResourceGroupId;

    @JsonProperty("xAcsCallerType")
    private String xAcsCallerType;

    @JsonProperty("brokerEngineType")
    private String brokerEngineType;
}
