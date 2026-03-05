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

package com.alibaba.himarket.dto.params.apsara;

import java.util.List;
import java.util.Map;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ModifyAppRequest extends ApsaraGatewayBaseRequest {

    private String accessKey;

    private String allowedHeaders;

    private String allowedMethods;

    private String allowedOrigins;

    private String appAddress;

    private String appCode;

    private String appId;

    private String appKey;

    private String appName;

    private String appSecret;

    private String appTagsStr;

    private Integer appType;

    private Integer authType;

    private String chargePersonName;

    private String description;

    private Long expireTime;

    private List<String> groups;

    private String gwInstanceId;

    private String ipWhiteList;

    private Boolean isDisable;

    private String key;

    private Map<String, ?> oauth2Payload;

    private Boolean openCross;

    private String password;

    private Map<String, ?> payload;

    private String requestHeader;

    private String secretKey;

    private String token;

    private Boolean useAuth;

    private Boolean useWhiteList;

    private String userId;

    private String userName;

    private Boolean isDisasterRecovery;

    /**
     * API Key位置类型 (HEADER/QUERY/BEARER)
     * higress 引擎使用
     */
    private String apiKeyLocationType;

    /**
     * API Key在Header/Query中的名称
     * 当 apiKeyLocationType 为 HEADER 或 QUERY 时需要提供
     * BEARER 类型不需要此字段（固定使用 Authorization）
     */
    private String keyName;
}
