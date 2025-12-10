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

import lombok.Data;

/**
 * @author zh
 */
@Data
public class AuthCodeConfig {

    /** 凭证 */
    private String clientId;

    private String clientSecret;

    /** 访问范围 */
    private String scopes;

    /** Issuer */
    private String issuer;

    /** 授权端点 */
    private String authorizationEndpoint;

    /** 令牌端点 */
    private String tokenEndpoint;

    /** 用户信息端点 */
    private String userInfoEndpoint;

    /** JWK Set URI */
    private String jwkSetUri;

    /** 重定向URI */
    private String redirectUri;
}
