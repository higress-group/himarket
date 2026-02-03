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

package com.alibaba.himarket.dto.result.mcp;

import cn.hutool.core.annotation.Alias;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.map.MapUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.nacos.api.ai.model.mcp.McpServerDetailInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpTool;
import com.alibaba.nacos.api.ai.model.mcp.McpToolSpecification;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.*;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Data
@Slf4j
public class OpenAPIMCPConfig {

    private Server server;

    private List<Tool> tools;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Server {
        private String name;
        @Builder.Default private Map<String, Object> config = new HashMap<>();
        @Builder.Default private List<String> allowTools = new ArrayList<>();

        private String type;
        private String transport;
        private String mcpServerURL;
        private Integer timeout;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    private static class Tool {
        private String name;
        private String description;
        @Builder.Default private List<Arg> args = new ArrayList<>();
        private RequestTemplate requestTemplate;
        private ResponseTemplate responseTemplate;
        private String errorResponseTemplate;
        private Map<String, Object> outputSchema;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    private static class Arg {
        private String name;
        private String description;
        private String type;
        private boolean required;

        @JsonProperty("default")
        @Alias("default")
        private String defaultValue;

        @JsonProperty("enum")
        @Alias("enum")
        private List<String> enumValues;

        private String position;
        private Map<String, Object> items;
        private Map<String, Object> properties;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RequestTemplate {
        private String url;
        private String method;
        @Builder.Default private List<Header> headers = new ArrayList<>();
    }

    @Data
    public static class ResponseTemplate {
        private String body;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Header {
        private String key;
        private String value;
    }

    // region convert from nacos config

    public static OpenAPIMCPConfig convertFromNacos(McpServerDetailInfo mcpServerDetailInfo) {
        OpenAPIMCPConfig config = new OpenAPIMCPConfig();

        config.setServer(
                Server.builder()
                        .name(mcpServerDetailInfo.getName())
                        .allowTools(Collections.singletonList(mcpServerDetailInfo.getName()))
                        .build());

        // Convert toolSpec to openapi tools
        Optional.ofNullable(mcpServerDetailInfo.getToolSpec())
                .map(McpToolSpecification::getTools)
                .filter(CollUtil::isNotEmpty)
                .ifPresent(
                        tools ->
                                config.setTools(
                                        tools.stream()
                                                .map(OpenAPIMCPConfig::convertTool)
                                                .filter(Objects::nonNull)
                                                .collect(Collectors.toList())));

        return config;
    }

    private static Tool convertTool(McpTool mcpTool) {
        return Optional.ofNullable(mcpTool)
                .filter(tool -> StrUtil.isNotBlank(tool.getName()))
                .map(
                        tool ->
                                Tool.builder()
                                        .name(tool.getName())
                                        .description(tool.getDescription())
                                        .args(convertArgs(tool.getInputSchema()))
                                        .build())
                .orElse(null);
    }

    @SuppressWarnings("unchecked")
    private static List<Arg> convertArgs(Map<String, Object> inputSchema) {

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

                            Arg arg =
                                    Arg.builder()
                                            .name(name)
                                            .required(requiredFields.contains(name))
                                            .build();

                            return BeanUtil.fillBeanWithMap(propertyMap, arg, true);
                        })
                .collect(Collectors.toList());
    }

    // endregion

    // region convert from tool list
    public static OpenAPIMCPConfig convertFromToolList(
            String mcpServerName, List<McpSchema.Tool> toolList) {
        OpenAPIMCPConfig config = new OpenAPIMCPConfig();
        if (CollUtil.isEmpty(toolList)) {
            return config;
        }

        config.setServer(Server.builder().name(mcpServerName).build());
        config.setTools(
                toolList.stream()
                        .map(OpenAPIMCPConfig::convertTool)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList()));
        return config;
    }

    private static Tool convertTool(McpSchema.Tool mcpTool) {
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
                            List<Arg> args = convertArgs(inputSchemaMap);

                            return Tool.builder()
                                    .name(tool.name())
                                    .description(tool.description())
                                    .args(args)
                                    .build();
                        })
                .orElse(null);
    }

    // endregion
}
