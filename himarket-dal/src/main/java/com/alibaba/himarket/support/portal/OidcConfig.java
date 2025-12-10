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
    /** 提供商 */
    private String provider;

    /** 对外的名称 */
    private String name;

    /** 登录按钮logo */
    private String logoUrl;

    /** 是否启用 */
    private boolean enabled = true;

    /** 授权类型，默认授权码 */
    private GrantType grantType = GrantType.AUTHORIZATION_CODE;

    /** 授权码模式配置 */
    private AuthCodeConfig authCodeConfig;

    /** 身份映射 */
    private IdentityMapping identityMapping = new IdentityMapping();
}
