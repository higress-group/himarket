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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class McpProtocolTypeTest {

    @Test
    void parsesNacosPrefixedProtocols() {
        assertEquals(McpProtocolType.SSE, McpProtocolType.fromString("mcp-sse"));
        assertEquals(McpProtocolType.STREAMABLE_HTTP, McpProtocolType.fromString("mcp-streamable"));
        assertEquals(McpProtocolType.STREAMABLE_HTTP, McpProtocolType.fromString("MCP-Streamable"));
    }

    @Test
    void keepsExistingProtocolParsing() {
        assertEquals(McpProtocolType.STDIO, McpProtocolType.fromString("stdio"));
        assertEquals(McpProtocolType.SSE, McpProtocolType.fromString("sse"));
        assertEquals(
                McpProtocolType.STREAMABLE_HTTP, McpProtocolType.fromString("streamable-http"));
        assertEquals(McpProtocolType.STREAMABLE_HTTP, McpProtocolType.fromString("http"));
        assertEquals(McpProtocolType.DUAL_HTTP, McpProtocolType.fromString("dualHttp"));
    }

    @Test
    void returnsNullForUnknownOrBlank() {
        assertNull(McpProtocolType.fromString(null));
        assertNull(McpProtocolType.fromString(" "));
        assertNull(McpProtocolType.fromString("dubbo"));
    }
}
