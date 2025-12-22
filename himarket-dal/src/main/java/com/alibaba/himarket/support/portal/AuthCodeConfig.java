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

@Data
public class AuthCodeConfig {

    /**
     * Client credential
     */
    private String clientId;

    private String clientSecret;

    /**
     * Scopes
     */
    private String scopes;

    /**
     * Issuer
     */
    private String issuer;

    /**
     * Endpoint of authorization
     */
    private String authorizationEndpoint;

    /**
     * Endpoint of fetching access token
     */
    private String tokenEndpoint;

    /**
     * Endpoint of fetching user info
     */
    private String userInfoEndpoint;

    /**
     * JWK Set URI
     */
    private String jwkSetUri;

    /**
     * Redirect URI
     */
    private String redirectUri;
}
