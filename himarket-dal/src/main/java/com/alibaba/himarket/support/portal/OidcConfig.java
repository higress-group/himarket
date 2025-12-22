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

import com.alibaba.himarket.support.enums.GrantType;
import lombok.Data;

@Data
public class OidcConfig {
    /**
     * Provider
     */
    private String provider;

    /**
     * Name, must be unique
     */
    private String name;

    /**
     * Logo for display
     */
    private String logoUrl;

    /**
     * If true, this provider is enabled
     */
    private boolean enabled = true;

    /**
     * Grant type, default is authorization_code
     */
    private GrantType grantType = GrantType.AUTHORIZATION_CODE;

    /**
     * Config for authorization_code
     */
    private AuthCodeConfig authCodeConfig;

    /**
     * Identity mapping
     */
    private IdentityMapping identityMapping = new IdentityMapping();
}
