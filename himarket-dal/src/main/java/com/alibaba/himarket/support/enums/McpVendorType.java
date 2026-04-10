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
 * MCP 供应商类型。
 *
 * <p>供应商类型硬编码为枚举常量，不建表、不持久化。
 * 所有供应商均不需要管理员输入 API Key（经实际 API 测试验证）。
 */
public enum McpVendorType {
    MODELSCOPE("ModelScope"),
    MCP_REGISTRY("MCP Registry"),
    LOBEHUB("LobeHub");

    private final String displayName;

    McpVendorType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    /**
     * 该供应商是否需要管理员手动输入 API Key 认证。
     *
     * <p>经实际测试验证，所有供应商均不需要：ModelScope 列表和详情接口无需认证，
     * MCP_REGISTRY 为公开只读 API，LOBEHUB 使用 JWT client assertion 认证（后端自动处理）。
     */
    public boolean requiresApiKey() {
        return false;
    }
}
