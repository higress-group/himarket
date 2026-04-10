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

package com.alibaba.himarket.entity;

import cn.hutool.core.util.StrUtil;
import jakarta.persistence.*;
import lombok.*;

/**
 * MCP Server 技术配置（冷数据）。
 *
 * <p>只存储 MCP 独有的技术字段（协议、连接、工具等）。
 * 展示信息（名称、描述、图标、文档）统一由关联的 Product 管理。
 */
@Entity
@Table(
        name = "mcp_server_meta",
        uniqueConstraints = {
            @UniqueConstraint(
                    columnNames = {"mcp_server_id"},
                    name = "uk_mcp_server_id"),
            @UniqueConstraint(
                    columnNames = {"product_id", "mcp_name"},
                    name = "uk_product_mcp_name"),
        })
@Data
@EqualsAndHashCode(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class McpServerMeta extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "mcp_server_id", length = 64, nullable = false)
    private String mcpServerId;

    @Column(name = "product_id", length = 64, nullable = false)
    private String productId;

    @Column(name = "mcp_name", length = 128, nullable = false)
    private String mcpName;

    @Column(name = "repo_url", length = 512)
    private String repoUrl;

    @Column(name = "source_type", length = 32)
    private String sourceType;

    @Column(name = "origin", length = 32, nullable = false)
    private String origin;

    @Column(name = "tags", columnDefinition = "json")
    private String tags;

    @Column(name = "protocol_type", length = 32, nullable = false)
    private String protocolType;

    @Column(name = "connection_config", columnDefinition = "json", nullable = false)
    private String connectionConfig;

    @Column(name = "extra_params", columnDefinition = "json")
    private String extraParams;

    @Column(name = "tools_config", columnDefinition = "json")
    private String toolsConfig;

    @Column(name = "sandbox_required")
    private Boolean sandboxRequired;

    @Column(name = "created_by", length = 64)
    private String createdBy;

    /** 持久化前将空字符串的 JSON 列置为 null，避免 MySQL JSON 列写入非法值 */
    @PrePersist
    @PreUpdate
    private void sanitizeJsonFields() {
        if (StrUtil.isBlank(toolsConfig)) toolsConfig = null;
        if (StrUtil.isBlank(tags)) tags = null;
        if (StrUtil.isBlank(extraParams)) extraParams = null;
    }
}
