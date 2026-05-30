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

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * MCP Server 精简信息 — 用于 Open API 列表查询。
 * 不暴露 productId、connectionConfig 等内部/敏感字段。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class McpMetaSimpleResult {

    private String mcpServerId;
    private String mcpName;
    private String displayName;
    private String description;
    private String icon;
    private String protocolType;
    private String origin;
    private String tags;
    private String publishStatus;
    private Boolean sandboxRequired;
    private LocalDateTime createAt;

    public static McpMetaSimpleResult fromFull(McpMetaResult full) {
        return McpMetaSimpleResult.builder()
                .mcpServerId(full.getMcpServerId())
                .mcpName(full.getMcpName())
                .displayName(full.getDisplayName())
                .description(full.getDescription())
                .icon(full.getIcon())
                .protocolType(full.getProtocolType())
                .origin(full.getOrigin())
                .tags(full.getTags())
                .publishStatus(full.getPublishStatus())
                .sandboxRequired(full.getSandboxRequired())
                .createAt(full.getCreateAt())
                .build();
    }
}
