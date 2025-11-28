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
package com.alibaba.apiopenplatform.dto.params.chat;

import lombok.Getter;
import lombok.NoArgsConstructor;
import org.apache.commons.collections.MapUtils;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author shihan
 * @version : ToolContext, v0.1 2025年11月26日 16:22 shihan Exp $
 */
@Getter
@NoArgsConstructor
public class ToolContext {
    private final List<ToolCallback>          toolCallbacks     = new ArrayList<>();
    private final Map<String, ToolDefinition> toolDefinitionMap = new HashMap<>();
    private final Map<String, McpToolMeta>    toolMetaMap       = new HashMap<>();

    public static ToolContext of(Map<McpToolMeta, ToolCallback> toolsMap) {
        ToolContext toolContext = new ToolContext();
        if (MapUtils.isEmpty(toolsMap)) {
            return toolContext;
        }
        toolContext.toolCallbacks.addAll(toolsMap.values());

        toolsMap.forEach((meta, toolCallback) -> {
            ToolDefinition toolDefinition = toolCallback.getToolDefinition();
            toolContext.toolDefinitionMap.put(toolDefinition.name(), toolDefinition);
            toolContext.toolMetaMap.put(toolDefinition.name(), meta);
        });
        return toolContext;
    }

    public McpToolMeta getToolMeta(String name) {
        return toolMetaMap.get(name);
    }

    public ToolDefinition getToolDefinition(String name) {
        return toolDefinitionMap.get(name);
    }
}
