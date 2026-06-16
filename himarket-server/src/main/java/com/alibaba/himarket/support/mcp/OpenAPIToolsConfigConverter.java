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

import com.alibaba.himarket.support.api.spec.OpenAPIToolsConfig;
import com.alibaba.himarket.support.common.Strings;
import com.alibaba.himarket.utils.JsonUtil;
import com.alibaba.nacos.api.ai.model.mcp.McpServerDetailInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpTool;
import com.alibaba.nacos.api.ai.model.mcp.McpToolSpecification;
import com.fasterxml.jackson.core.type.TypeReference;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.CollectionUtils;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/**
 * Converter that produces {@link OpenAPIToolsConfig} from Nacos or MCP SDK types.
 */
@Slf4j
public final class OpenAPIToolsConfigConverter {

    /**
     * Convert from Nacos MCP server detail info.
     *
     * @param mcpServerDetailInfo the Nacos MCP server detail
     * @return the converted OpenAPIToolsConfig
     */
    public static OpenAPIToolsConfig convertFromNacos(McpServerDetailInfo mcpServerDetailInfo) {
        OpenAPIToolsConfig config = new OpenAPIToolsConfig();

        config.setServer(
                OpenAPIToolsConfig.Server.builder()
                        .name(mcpServerDetailInfo.getName())
                        .allowTools(Collections.singletonList(mcpServerDetailInfo.getName()))
                        .build());

        McpToolSpecification toolSpec = mcpServerDetailInfo.getToolSpec();
        if (toolSpec != null && !CollectionUtils.isEmpty(toolSpec.getTools())) {
            config.setTools(
                    toolSpec.getTools().stream()
                            .map(OpenAPIToolsConfigConverter::convertNacosTool)
                            .filter(Objects::nonNull)
                            .toList());
        }

        return config;
    }

    /**
     * Convert from MCP SDK tool list.
     *
     * @param mcpServerName the MCP server name
     * @param toolList the list of MCP SDK tools
     * @return the converted OpenAPIToolsConfig
     */
    public static OpenAPIToolsConfig convertFromToolList(
            String mcpServerName, List<McpSchema.Tool> toolList) {
        OpenAPIToolsConfig config = new OpenAPIToolsConfig();
        if (CollectionUtils.isEmpty(toolList)) {
            return config;
        }

        config.setServer(OpenAPIToolsConfig.Server.builder().name(mcpServerName).build());
        config.setTools(
                toolList.stream()
                        .map(OpenAPIToolsConfigConverter::convertMcpSchemaTool)
                        .filter(Objects::nonNull)
                        .toList());
        return config;
    }

    /**
     * Convert raw gateway tools config to JSON.
     *
     * @param raw raw tools config in JSON or YAML
     * @return JSON config, or null when the input cannot be parsed
     */
    public static String convertRawConfigToJson(String raw) {
        OpenAPIToolsConfig config = convertFromRawConfig(raw);
        return config == null ? null : JsonUtil.toJson(config);
    }

    /**
     * Convert raw gateway tools config.
     *
     * @param raw raw tools config in JSON or YAML
     * @return tools config, or null when the input cannot be parsed
     */
    public static OpenAPIToolsConfig convertFromRawConfig(String raw) {
        if (Strings.isBlank(raw)) {
            return null;
        }

        String trimmed = raw.trim();
        if (trimmed.startsWith("{")) {
            Map<String, Object> parsed = JsonUtil.parse(trimmed, new TypeReference<>() {});
            parsed.putIfAbsent("format", "OPEN_API");
            return JsonUtil.convert(parsed, OpenAPIToolsConfig.class);
        }

        try {
            Yaml yaml = new Yaml(new SafeConstructor(new LoaderOptions()));
            Object parsed = yaml.load(raw);
            if (parsed instanceof Map<?, ?> rawMap) {
                Map<String, Object> config = new LinkedHashMap<>();
                rawMap.forEach(
                        (key, value) -> {
                            if (key != null) {
                                config.put(String.valueOf(key), value);
                            }
                        });
                config.putIfAbsent("format", "OPEN_API");
                return JsonUtil.convert(config, OpenAPIToolsConfig.class);
            }
            return parsed == null ? null : JsonUtil.convert(parsed, OpenAPIToolsConfig.class);
        } catch (Exception e) {
            log.warn(
                    "Failed to parse MCP tools config, tools will be omitted, errorMessage={}",
                    e.getMessage());
            return null;
        }
    }

    private static OpenAPIToolsConfig.OpenAPITool convertNacosTool(McpTool mcpTool) {
        if (mcpTool == null || Strings.isBlank(mcpTool.getName())) {
            return null;
        }

        return OpenAPIToolsConfig.OpenAPITool.builder()
                .name(mcpTool.getName())
                .description(mcpTool.getDescription())
                .args(convertArgs(mcpTool.getInputSchema()))
                .build();
    }

    private static OpenAPIToolsConfig.OpenAPITool convertMcpSchemaTool(McpSchema.Tool mcpTool) {
        if (mcpTool == null || Strings.isBlank(mcpTool.name())) {
            return null;
        }

        Map<String, Object> inputSchemaMap = new HashMap<>();
        inputSchemaMap.put("type", mcpTool.inputSchema().type());
        inputSchemaMap.put("properties", mcpTool.inputSchema().properties());
        inputSchemaMap.put("required", mcpTool.inputSchema().required());
        inputSchemaMap.put("additionalProperties", mcpTool.inputSchema().additionalProperties());
        inputSchemaMap.put("definitions", mcpTool.inputSchema().definitions());
        List<OpenAPIToolsConfig.Arg> args = convertArgs(inputSchemaMap);

        return OpenAPIToolsConfig.OpenAPITool.builder()
                .name(mcpTool.name())
                .description(mcpTool.description())
                .args(args)
                .build();
    }

    @SuppressWarnings("unchecked")
    private static List<OpenAPIToolsConfig.Arg> convertArgs(Map<String, Object> inputSchema) {
        Map<String, Object> properties = null;
        if (inputSchema != null) {
            Object schemaProperties = inputSchema.get("properties");
            if (schemaProperties instanceof Map<?, ?> rawProperties && !rawProperties.isEmpty()) {
                properties = toStringObjectMap(rawProperties);
            }
        }

        if (CollectionUtils.isEmpty(properties)) {
            return new ArrayList<>();
        }

        List<String> requiredFields = requiredFields(inputSchema);

        return properties.entrySet().stream()
                .map(
                        entry -> {
                            Map<String, Object> propertyMap =
                                    (Map<String, Object>) entry.getValue();
                            String name = entry.getKey();

                            OpenAPIToolsConfig.Arg arg =
                                    JsonUtil.convert(propertyMap, OpenAPIToolsConfig.Arg.class);
                            arg.setName(name);
                            arg.setRequired(requiredFields.contains(name));

                            return arg;
                        })
                .toList();
    }

    private static List<String> requiredFields(Map<String, Object> inputSchema) {
        if (inputSchema == null || !(inputSchema.get("required") instanceof List<?> rawRequired)) {
            return new ArrayList<>();
        }
        return rawRequired.stream()
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .toList();
    }

    private static Map<String, Object> toStringObjectMap(Map<?, ?> rawMap) {
        Map<String, Object> result = new LinkedHashMap<>();
        rawMap.forEach(
                (key, value) -> {
                    if (key != null) {
                        result.put(String.valueOf(key), value);
                    }
                });
        return result;
    }
}
