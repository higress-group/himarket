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

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.map.MapUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.himarket.support.api.spec.OpenAPIToolsConfig;
import com.alibaba.nacos.api.ai.model.mcp.McpServerDetailInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpTool;
import com.alibaba.nacos.api.ai.model.mcp.McpToolSpecification;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.*;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

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

        Optional.ofNullable(mcpServerDetailInfo.getToolSpec())
                .map(McpToolSpecification::getTools)
                .filter(CollUtil::isNotEmpty)
                .ifPresent(
                        tools ->
                                config.setTools(
                                        tools.stream()
                                                .map(OpenAPIToolsConfigConverter::convertNacosTool)
                                                .filter(Objects::nonNull)
                                                .collect(Collectors.toList())));

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
        if (CollUtil.isEmpty(toolList)) {
            return config;
        }

        config.setServer(OpenAPIToolsConfig.Server.builder().name(mcpServerName).build());
        config.setTools(
                toolList.stream()
                        .map(OpenAPIToolsConfigConverter::convertMcpSchemaTool)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList()));
        return config;
    }

    private static OpenAPIToolsConfig.OpenAPITool convertNacosTool(McpTool mcpTool) {
        return Optional.ofNullable(mcpTool)
                .filter(tool -> StrUtil.isNotBlank(tool.getName()))
                .map(
                        tool ->
                                OpenAPIToolsConfig.OpenAPITool.builder()
                                        .name(tool.getName())
                                        .description(tool.getDescription())
                                        .args(convertArgs(tool.getInputSchema()))
                                        .build())
                .orElse(null);
    }

    private static OpenAPIToolsConfig.OpenAPITool convertMcpSchemaTool(McpSchema.Tool mcpTool) {
        return Optional.ofNullable(mcpTool)
                .filter(tool -> StrUtil.isNotBlank(tool.name()))
                .map(
                        tool -> {
                            Map<String, Object> inputSchemaMap =
                                    MapUtil.builder(new HashMap<String, Object>())
                                            .put("type", tool.inputSchema().type())
                                            .put("properties", tool.inputSchema().properties())
                                            .put("required", tool.inputSchema().required())
                                            .put(
                                                    "additionalProperties",
                                                    tool.inputSchema().additionalProperties())
                                            .put("definitions", tool.inputSchema().definitions())
                                            .build();
                            List<OpenAPIToolsConfig.Arg> args = convertArgs(inputSchemaMap);

                            return OpenAPIToolsConfig.OpenAPITool.builder()
                                    .name(tool.name())
                                    .description(tool.description())
                                    .args(args)
                                    .build();
                        })
                .orElse(null);
    }

    @SuppressWarnings("unchecked")
    private static List<OpenAPIToolsConfig.Arg> convertArgs(Map<String, Object> inputSchema) {
        Map<String, Object> properties =
                Optional.ofNullable(inputSchema)
                        .map(schema -> MapUtil.get(schema, "properties", Map.class))
                        .filter(props -> !props.isEmpty())
                        .orElse(null);

        if (MapUtil.isEmpty(properties)) {
            return new ArrayList<>();
        }

        List<String> requiredFields =
                Optional.ofNullable(MapUtil.get(inputSchema, "required", List.class))
                        .orElse(new ArrayList<>());

        return properties.entrySet().stream()
                .map(
                        entry -> {
                            Map<String, Object> propertyMap =
                                    (Map<String, Object>) entry.getValue();
                            String name = entry.getKey();

                            OpenAPIToolsConfig.Arg arg =
                                    OpenAPIToolsConfig.Arg.builder()
                                            .name(name)
                                            .required(requiredFields.contains(name))
                                            .build();

                            return BeanUtil.fillBeanWithMap(propertyMap, arg, true);
                        })
                .collect(Collectors.toList());
    }
}
