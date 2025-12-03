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

package com.alibaba.apiopenplatform.support.portal;

import lombok.Data;

import java.util.List;

@Data
public class PortalSettingConfig {

    /**
     * 内置的账号密码认证，默认开启
     */
    private Boolean builtinAuthEnabled = true;

    /**
     * OIDC配置
     */
    private List<OidcConfig> oidcConfigs;

    /**
     * 开启自动审批开发者注册
     */
    private Boolean autoApproveDevelopers = false;

    /**
     * 开启自动审批订阅申请
     */
    private Boolean autoApproveSubscriptions = true;

    /**
     * OAuth2配置
     */
    private List<OAuth2Config> oauth2Configs;
    
    /**
     * 搜索引擎配置（新增）
     * 每个 Portal 只能配置一个搜索引擎
     * null 表示未配置
     */
    private SearchEngineConfig searchEngineConfig;
}
