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

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/**
 * 将网关返回的 tools 配置（可能是 YAML 或 JSON）标准化为合法 JSON 字符串。
 * 网关的 MCP tools 字段可能是 YAML 格式的纯文本，直接写入 MySQL JSON 列会报错。
 */
@Slf4j
public final class McpToolsConfigParser {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private McpToolsConfigParser() {}

    /**
     * 将 tools 配置标准化为合法 JSON 字符串。
     * <ul>
     *   <li>已经是合法 JSON → 原样返回</li>
     *   <li>YAML 格式 → 解析后转为 JSON</li>
     *   <li>空白值 → 返回 null</li>
     * </ul>
     */
    @SuppressWarnings("unchecked")
    public static String normalize(String raw) {
        if (StrUtil.isBlank(raw)) {
            return null;
        }
        String trimmed = raw.trim();

        // 已经是合法 JSON（数组或对象）
        if ((trimmed.startsWith("[") || trimmed.startsWith("{")) && JSONUtil.isTypeJSON(trimmed)) {
            return trimmed;
        }

        // 尝试按 YAML 解析
        try {
            Yaml yaml = new Yaml(new SafeConstructor(new LoaderOptions()));
            Object parsed = yaml.load(raw);
            if (parsed == null) {
                return null;
            }
            // 网关格式: { server: "...", tools: [...] }，提取 tools 字段
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
            // 纯字符串等标量，不是有效的 tools 配置
            return null;
        } catch (Exception e) {
            log.warn("tools_config 既非 JSON 也非 YAML，忽略: {}", e.getMessage());
            return null;
        }
    }
}
