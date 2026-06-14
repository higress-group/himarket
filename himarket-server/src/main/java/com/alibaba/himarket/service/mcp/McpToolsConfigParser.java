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

package com.alibaba.himarket.service.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/**
 * Normalizes tools config returned by a gateway into valid JSON.
 *
 * <p>Gateway MCP tools may be plain YAML text; writing that directly to a MySQL JSON column fails.
 */
@Slf4j
public final class McpToolsConfigParser {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private McpToolsConfigParser() {}

    /**
     * Normalizes tools config into a valid JSON string.
     *
     * <ul>
     *   <li>Valid JSON is returned as-is</li>
     *   <li>YAML is parsed and converted to JSON</li>
     *   <li>Blank values return null</li>
     * </ul>
     */
    @SuppressWarnings("unchecked")
    public static String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String trimmed = raw.trim();

        try {
            OBJECT_MAPPER.readTree(trimmed);
            return trimmed;
        } catch (IOException e) {
            log.debug(
                    "tools_config is not JSON, trying YAML parsing, errorMessage={}",
                    e.getMessage());
        }

        try {
            Yaml yaml = new Yaml(new SafeConstructor(new LoaderOptions()));
            Object parsed = yaml.load(raw);
            if (parsed == null) {
                return null;
            }
            // Gateway format: { server: "...", tools: [...] }. Extract tools when present.
            if (parsed instanceof Map) {
                Map<String, Object> map = (Map<String, Object>) parsed;
                Object tools = map.get("tools");
                if (tools instanceof List) {
                    return OBJECT_MAPPER.writeValueAsString(tools);
                }
                return OBJECT_MAPPER.writeValueAsString(map);
            }
            if (parsed instanceof List) {
                return OBJECT_MAPPER.writeValueAsString(parsed);
            }
            return null;
        } catch (Exception e) {
            log.warn(
                    "tools_config is neither JSON nor YAML and will be ignored, errorMessage={}",
                    e.getMessage(),
                    e);
            return null;
        }
    }
}
