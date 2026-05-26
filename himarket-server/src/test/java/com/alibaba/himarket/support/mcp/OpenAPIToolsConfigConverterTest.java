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

package com.alibaba.himarket.support.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.alibaba.himarket.support.api.spec.OpenAPIToolsConfig;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OpenAPIToolsConfigConverterTest {

    @Test
    void shouldConvertMcpSdkJsonSchemaInputSchema() {
        McpSchema.JsonSchema inputSchema =
                new McpSchema.JsonSchema(
                        "object",
                        Map.of("query", Map.of("type", "string", "description", "Search query")),
                        List.of("query"),
                        null,
                        null,
                        null);
        McpSchema.Tool tool =
                McpSchema.Tool.builder()
                        .name("search")
                        .description("Search documents")
                        .inputSchema(inputSchema)
                        .build();

        OpenAPIToolsConfig config =
                OpenAPIToolsConfigConverter.convertFromToolList("docs", List.of(tool));

        assertEquals("docs", config.getServer().getName());
        assertEquals(1, config.getTools().size());
        assertEquals("search", config.getTools().get(0).getName());
        assertEquals(1, config.getTools().get(0).getArgs().size());
        OpenAPIToolsConfig.Arg arg = config.getTools().get(0).getArgs().get(0);
        assertEquals("query", arg.getName());
        assertEquals("string", arg.getType());
        assertEquals("Search query", arg.getDescription());
        assertTrue(arg.isRequired());
    }
}
