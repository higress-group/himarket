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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.alibaba.himarket.utils.JsonUtil;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

class OpenAPIToolsConfigConverterTest {

    @Test
    void shouldDeserializeRawGatewayConfigWithoutUnknownCredentialFields() {
        String raw =
                String.join(
                        "\n",
                        "server:",
                        "  name: time",
                        "  type: mcp-proxy",
                        "  transport: sse",
                        "  mcpServerURL: http://example.com/sse",
                        "  defaultUpstreamSecurity:",
                        "    id: ApiKeyAuth",
                        "  securitySchemes:",
                        "    - id: ApiKeyAuth",
                        "      type: apiKey",
                        "      in: header",
                        "      name: Authorization",
                        "      defaultCredential: Bearer sk-test-secret",
                        "tools:",
                        "  - name: get_current_time",
                        "    description: Get current time",
                        "    args:",
                        "      - name: timezone",
                        "        description: IANA timezone name",
                        "        type: string",
                        "        required: true",
                        "        default: Asia/Shanghai",
                        "    requestTemplate:",
                        "      url: http://example.com",
                        "      security:",
                        "        id: ApiKeyAuth",
                        "    responseTemplate: {}");

        String result = OpenAPIToolsConfigConverter.convertRawConfigToJson(raw);

        assertNotNull(result);
        assertFalse(result.contains("defaultCredential"));
        assertFalse(result.contains("Authorization"));
        assertFalse(result.contains("Bearer"));

        JsonNode root = JsonUtil.readTree(result);
        assertEquals("time", root.path("server").path("name").asText());
        JsonNode tool = root.path("tools").get(0);
        assertEquals("get_current_time", tool.path("name").asText());
        assertEquals("Get current time", tool.path("description").asText());

        JsonNode arg = tool.path("args").get(0);
        assertEquals("timezone", arg.path("name").asText());
        assertEquals("string", arg.path("type").asText());
        assertTrue(arg.path("required").asBoolean());
    }
}
