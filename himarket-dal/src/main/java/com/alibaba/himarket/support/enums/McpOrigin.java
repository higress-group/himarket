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

package com.alibaba.himarket.support.enums;

/**
 * MCP Server 来源。
 */
public enum McpOrigin {
    /** 管理员在 Admin 后台手动创建 */
    ADMIN,
    /** 从网关导入 */
    GATEWAY,
    /** 从 Nacos 导入 */
    NACOS,
    /** 通过 Open API 注册 */
    OPEN_API,
    /** 从 AgentRuntime 注册 */
    AGENTRUNTIME,
    /** 开发者在 Portal 端注册 */
    USER,
    /** 从第三方供应商 API 导入 */
    VENDOR_IMPORT;

    /**
     * 从字符串解析，大小写不敏感，无法识别时返回 ADMIN。
     */
    public static McpOrigin fromString(String value) {
        if (value == null || value.isBlank()) {
            return ADMIN;
        }
        try {
            return valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return ADMIN;
        }
    }
}
