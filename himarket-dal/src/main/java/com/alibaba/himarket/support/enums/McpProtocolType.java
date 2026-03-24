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
 * MCP 协议类型。
 *
 * <p>标准值只有三种，兼容外部系统传入的各种写法。
 */
public enum McpProtocolType {
    STDIO("stdio"),
    SSE("sse"),
    STREAMABLE_HTTP("streamableHttp");

    private final String value;

    McpProtocolType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    /**
     * 从原始字符串解析，大小写不敏感。
     * 兼容 streamable-http、StreamableHTTP、HTTP、Stdio 等写法。
     *
     * @return 解析后的枚举值，无法识别时返回 null
     */
    public static McpProtocolType fromString(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String lower = raw.trim().toLowerCase();
        if ("stdio".equals(lower)) return STDIO;
        if ("sse".equals(lower)) return SSE;
        if (lower.contains("http")) return STREAMABLE_HTTP;
        return null;
    }

    /**
     * 标准化协议类型字符串。
     * 兼容旧代码中直接使用字符串的场景。
     */
    public static String normalize(String raw) {
        McpProtocolType type = fromString(raw);
        return type != null ? type.value : (raw != null ? raw.trim() : raw);
    }

    public boolean isStdio() {
        return this == STDIO;
    }

    public boolean isSse() {
        return this == SSE;
    }

    public boolean isStreamableHttp() {
        return this == STREAMABLE_HTTP;
    }

    /**
     * 转换为 MCP 传输模式。
     * STDIO 不支持远程传输，返回 null。
     */
    public MCPTransportMode toTransportMode() {
        return switch (this) {
            case SSE -> MCPTransportMode.SSE;
            case STREAMABLE_HTTP -> MCPTransportMode.STREAMABLE_HTTP;
            case STDIO -> null;
        };
    }

    /**
     * 从原始协议字符串解析传输模式，默认 SSE。
     * 统一入口，避免各处重复写字符串判断。
     */
    public static MCPTransportMode resolveTransportMode(String raw) {
        McpProtocolType type = fromString(raw);
        if (type != null && type.toTransportMode() != null) {
            return type.toTransportMode();
        }
        return MCPTransportMode.SSE;
    }
}
