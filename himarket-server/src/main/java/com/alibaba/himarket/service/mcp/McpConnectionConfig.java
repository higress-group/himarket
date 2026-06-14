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

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.Data;

/**
 * Typed parser for MCP connectionConfig JSON.
 *
 * <p>Supported formats:
 * <ol>
 *   <li>mcpServers format: { "mcpServers": { "name": { "command": "...", "env": {...} } } }</li>
 *   <li>single server format: { "command": "...", "args": [...], "env": {...} }</li>
 *   <li>wrapper format: { "mcpServerConfig": { "rawConfig": { ... } } }</li>
 * </ol>
 */
@Data
public class McpConnectionConfig {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Format 1: mcpServers wrapper.
     */
    private Map<String, McpServerEntry> mcpServers;

    /**
     * Format 2: single server with command.
     */
    private String command;

    private List<String> args;
    private Map<String, Object> env;

    /**
     * Format 3: mcpServerConfig wrapper.
     */
    private McpServerConfigWrapper mcpServerConfig;

    /**
     * Extra unknown fields, such as url or type in single server format.
     */
    private Map<String, Object> extra = new LinkedHashMap<>();

    @JsonAnySetter
    public void setExtra(String key, Object value) {
        extra.put(key, value);
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class McpServerEntry {
        private String command;
        private List<String> args;
        private Map<String, Object> env;

        /**
         * Extra unknown fields, such as url or type.
         */
        private Map<String, Object> extra = new LinkedHashMap<>();

        @JsonAnySetter
        public void setExtra(String key, Object value) {
            if (!"command".equals(key) && !"args".equals(key) && !"env".equals(key)) {
                extra.put(key, value);
            }
        }

        /**
         * Converts this entry to a map without env for mcpServers JSON serialization.
         */
        public Map<String, Object> toMapWithoutEnv() {
            Map<String, Object> map = new LinkedHashMap<>(extra);
            if (command != null) map.put("command", command);
            if (args != null) map.put("args", args);
            return map;
        }
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class McpServerConfigWrapper {
        @JsonProperty("rawConfig")
        private Object rawConfig;
    }

    /**
     * Parses a JSON string.
     */
    public static McpConnectionConfig parse(String json) throws JsonProcessingException {
        return MAPPER.readValue(json, McpConnectionConfig.class);
    }

    /**
     * Returns whether this config uses mcpServers format.
     */
    public boolean isMcpServersFormat() {
        return mcpServers != null && !mcpServers.isEmpty();
    }

    public boolean isSingleServerFormat() {
        return command != null;
    }

    public boolean isWrappedFormat() {
        return mcpServerConfig != null && mcpServerConfig.getRawConfig() != null;
    }

    /**
     * Extracts all env entries and converts values to strings.
     */
    public Map<String, String> extractAllEnv() {
        Map<String, String> result = new LinkedHashMap<>();
        if (isMcpServersFormat()) {
            for (McpServerEntry entry : mcpServers.values()) {
                if (entry.getEnv() != null) {
                    entry.getEnv().forEach((k, v) -> result.put(k, v != null ? v.toString() : ""));
                }
            }
        } else if (isSingleServerFormat() && env != null) {
            env.forEach((k, v) -> result.put(k, v != null ? v.toString() : ""));
        }
        return result;
    }

    /**
     * Builds mcpServers JSON without env values.
     *
     * @param defaultName server name used when the source format is single server
     */
    public String toMcpServersJsonWithoutEnv(String defaultName) throws JsonProcessingException {
        Map<String, Object> root = new LinkedHashMap<>();

        if (isMcpServersFormat()) {
            Map<String, Object> servers = new LinkedHashMap<>();
            for (Map.Entry<String, McpServerEntry> e : mcpServers.entrySet()) {
                servers.put(e.getKey(), e.getValue().toMapWithoutEnv());
            }
            root.put("mcpServers", servers);
        } else if (isSingleServerFormat()) {
            Map<String, Object> server = new LinkedHashMap<>(extra);
            if (command != null) server.put("command", command);
            if (args != null) server.put("args", args);
            Map<String, Object> servers = new LinkedHashMap<>();
            servers.put(defaultName, server);
            root.put("mcpServers", servers);
        }

        return MAPPER.writeValueAsString(root);
    }

    /**
     * Gets the rawConfig JSON string from wrapper format.
     */
    public String getRawConfigJson() throws JsonProcessingException {
        if (!isWrappedFormat()) return null;
        return MAPPER.writeValueAsString(mcpServerConfig.getRawConfig());
    }
}
