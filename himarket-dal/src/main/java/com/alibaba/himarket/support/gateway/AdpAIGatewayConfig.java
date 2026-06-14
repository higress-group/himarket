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

package com.alibaba.himarket.support.gateway;

import cn.hutool.core.util.StrUtil;
import java.util.List;
import lombok.Data;

/**
 * ADP AI gateway configuration.
 */
@Data
public class AdpAIGatewayConfig {

    /**
     * ADP AI gateway base URL.
     */
    private String baseUrl;

    /**
     * ADP AI gateway port.
     */
    private Integer port;

    /**
     * Authentication seed for seed-based authorization.
     */
    private String authSeed;

    /**
     * Authentication headers for header-based authorization.
     */
    private List<AuthHeader> authHeaders;

    @Data
    public static class AuthHeader {
        private String key;
        private String value;

        public AuthHeader() {}

        public AuthHeader(String key, String value) {
            this.key = key;
            this.value = value;
        }
    }

    public boolean validate() {
        if (StrUtil.isBlank(baseUrl) || port == null) {
            return false;
        }

        boolean hasSeedAuth =
                StrUtil.isNotBlank(authSeed) && (authHeaders == null || authHeaders.isEmpty());
        boolean hasHeaderAuth = authSeed == null && authHeaders != null && !authHeaders.isEmpty();
        return hasSeedAuth || hasHeaderAuth;
    }
}
