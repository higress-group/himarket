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

package com.alibaba.himarket.support.portal;

import java.util.List;
import lombok.Data;

@Data
public class PortalSettingConfig {

    /**
     * Built-in username/password authentication, enabled by default
     */
    private Boolean builtinAuthEnabled = true;

    /**
     * OIDC configurations
     */
    private List<OidcConfig> oidcConfigs;

    /**
     * Enable auto-approval for developer registrations
     */
    private Boolean autoApproveDevelopers = false;

    /**
     * Enable auto-approval for subscription requests
     */
    private Boolean autoApproveSubscriptions = true;

    /**
     * OAuth2 configurations
     */
    private List<OAuth2Config> oauth2Configs;

    /**
     * Search engine configuration (New)
     * Each Portal can only configure one search engine
     * null means not configured
     */
    private SearchEngineConfig searchEngineConfig;
}
