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
 * MCP connectionConfig JSON 的类型化解析。
 *
 * <p>支持三种格式：
 * <ol>
 *   <li>mcpServers 格式：{ "mcpServers": { "name": { "command": "...", "env": {...} } } }</li>
 *   <li>单 server 格式：{ "command": "...", "args": [...], "env": {...} }</li>
 *   <li>包装格式：{ "mcpServerConfig": { "rawConfig": { ... } } }</li>
 * </ol>
 */
@Data
public class McpConnectionConfig {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 格式1: mcpServers 包装 */
    private Map<String, McpServerEntry> mcpServers;

    /** 格式2: 单 server（有 command 字段） */
    private String command;

    private List<String> args;
    private Map<String, Object> env;

    /** 格式3: mcpServerConfig 包装 */
    private McpServerConfigWrapper mcpServerConfig;

    /** 保留其他未知字段（单 server 格式下可能有 url, type 等） */
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

        /** 保留其他未知字段（如 url, type 等） */
        private Map<String, Object> extra = new LinkedHashMap<>();

        @JsonAnySetter
        public void setExtra(String key, Object value) {
            if (!"command".equals(key) && !"args".equals(key) && !"env".equals(key)) {
                extra.put(key, value);
            }
        }

        /** 转为不含 env 的 Map（用于序列化到 mcpServers JSON） */
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
     * 从 JSON 字符串解析。
     */
    public static McpConnectionConfig parse(String json) throws JsonProcessingException {
        return MAPPER.readValue(json, McpConnectionConfig.class);
    }

    /**
     * 判断是哪种格式。
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
     * 提取所有 env 并返回合并结果（value 统一转为 String）。
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
     * 构建不含 env 的 mcpServers JSON。
     *
     * @param defaultName 当格式为单 server 时使用的 server 名称
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
     * 获取包装格式中的 rawConfig JSON 字符串。
     */
    public String getRawConfigJson() throws JsonProcessingException {
        if (!isWrappedFormat()) return null;
        return MAPPER.writeValueAsString(mcpServerConfig.getRawConfig());
    }
}
