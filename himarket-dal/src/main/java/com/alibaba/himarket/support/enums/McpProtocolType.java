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

import lombok.Getter;

/**
 * MCP protocol type.
 *
 * <p>The enum supports canonical values and common aliases from external systems.
 */
@Getter
public enum McpProtocolType {
    STDIO("stdio"),
    SSE("sse"),
    STREAMABLE_HTTP("streamableHttp"),
    DUAL_HTTP("dualHttp");

    private final String value;

    McpProtocolType(String value) {
        this.value = value;
    }

    /**
     * Parses a raw protocol string in a case-insensitive way.
     *
     * @return parsed protocol type, or {@code null} when the raw value is not recognized
     */
    public static McpProtocolType fromString(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String lower = raw.trim().toLowerCase();
        if ("stdio".equals(lower)) return STDIO;
        if ("sse".equals(lower)) return SSE;
        if (lower.contains("dual")) return DUAL_HTTP;
        if (lower.contains("http")) return STREAMABLE_HTTP;
        return null;
    }

    /**
     * Normalizes a protocol type string while preserving unrecognized values.
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
        return this == STREAMABLE_HTTP || this == DUAL_HTTP;
    }

    public boolean isDualHttp() {
        return this == DUAL_HTTP;
    }

    /**
     * Converts this protocol type to an MCP transport mode.
     */
    public McpTransportMode toTransportMode() {
        return switch (this) {
            case SSE -> McpTransportMode.SSE;
            case STREAMABLE_HTTP, DUAL_HTTP -> McpTransportMode.STREAMABLE_HTTP;
            case STDIO -> null;
        };
    }

    /**
     * Resolves the transport mode from a raw protocol string.
     */
    public static McpTransportMode resolveTransportMode(String raw) {
        McpProtocolType type = fromString(raw);
        if (type != null && type.toTransportMode() != null) {
            return type.toTransportMode();
        }
        return McpTransportMode.SSE;
    }
}
