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
 * MCP Server 公开信息 — 用于匿名用户（未登录）浏览。
 * 只包含展示所需字段，不暴露连接配置、endpoint、部署参数等敏感信息。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class McpMetaPublicResult {

    private String mcpServerId;
    private String productId;
    private String mcpName;
    private String displayName;
    private String description;
    private String icon;
    private String protocolType;
    private String origin;
    private String tags;
    private String serviceIntro;
    private String publishStatus;
    private String toolsConfig;
    private String repoUrl;
    private Boolean sandboxRequired;
    private LocalDateTime createAt;

    public static McpMetaPublicResult fromFull(McpMetaResult full) {
        return McpMetaPublicResult.builder()
                .mcpServerId(full.getMcpServerId())
                .productId(full.getProductId())
                .mcpName(full.getMcpName())
                .displayName(full.getDisplayName())
                .description(full.getDescription())
                .icon(full.getIcon())
                .protocolType(full.getProtocolType())
                .origin(full.getOrigin())
                .tags(full.getTags())
                .serviceIntro(full.getServiceIntro())
                .publishStatus(full.getPublishStatus())
                .toolsConfig(full.getToolsConfig())
                .repoUrl(full.getRepoUrl())
                .sandboxRequired(full.getSandboxRequired())
                .createAt(full.getCreateAt())
                .build();
    }
}
