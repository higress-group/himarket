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

package com.alibaba.himarket.dto.result.idp;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class IdpTokenResult {

    /**
     * Access token
     */
    @JsonProperty("access_token")
    private String accessToken;

    /**
     * ID token
     */
    @JsonProperty("id_token")
    private String idToken;

    /**
     * Refresh token
     */
    @JsonProperty("refresh_token")
    private String refreshToken;

    /**
     * Token type
     */
    @JsonProperty("token_type")
    private String tokenType;

    /**
     * Expiration time in seconds
     */
    @JsonProperty("expires_in")
    private Integer expiresIn;

    /**
     * Scope
     */
    private String scope;
}
