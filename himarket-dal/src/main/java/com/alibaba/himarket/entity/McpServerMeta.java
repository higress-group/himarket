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

import com.alibaba.himarket.support.common.Strings;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * MCP Server technical configuration.
 *
 * <p>This entity stores MCP-specific technical fields such as protocol, connection, and tools.
 * Display fields such as name, description, icon, and documentation are managed by the related
 * Product.
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

    /**
     * Converts blank JSON columns to null before persistence to avoid invalid MySQL JSON values.
     */
    @PrePersist
    @PreUpdate
    private void sanitizeJsonFields() {
        if (Strings.isBlank(toolsConfig)) {
            toolsConfig = null;
        }
        if (Strings.isBlank(tags)) {
            tags = null;
        }
        if (Strings.isBlank(extraParams)) {
            extraParams = null;
        }
    }
}
