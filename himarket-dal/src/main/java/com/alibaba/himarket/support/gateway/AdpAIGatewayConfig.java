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
import lombok.Data;

/** ADP网关配置 继承自APIGConfig，支持ADP网关特有的配置 */
@Data
public class AdpAIGatewayConfig {

    /** ADP网关的baseUrl，如果为空则使用默认的region构建 */
    private String baseUrl;

    /** ADP网关的端口 */
    private Integer port;

    /** ADP网关的认证种子 */
    private String authSeed;

    /** ADP网关的认证头列表 */
    private java.util.List<AuthHeader> authHeaders;

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
        
        // 验证认证配置，支持两种认证方式：Seed 或 Header
        return (StrUtil.isNotBlank(authSeed) && (authHeaders == null || authHeaders.isEmpty())) // Seed 方式
                || (authSeed == null && authHeaders != null && !authHeaders.isEmpty()); // Header 方式
    }
}
