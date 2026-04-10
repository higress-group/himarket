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

import jakarta.persistence.*;
import lombok.*;

/**
 * MCP Server 运行时连接信息（热数据）。
 * 存储 MCP 的 endpoint、托管方式等运行时信息，查询频率高。
 */
@Entity
@Table(
        name = "mcp_server_endpoint",
        uniqueConstraints = {
            @UniqueConstraint(
                    columnNames = {"endpoint_id"},
                    name = "uk_endpoint_id"),
            @UniqueConstraint(
                    columnNames = {"mcp_server_id", "user_id", "hosting_instance_id"},
                    name = "uk_server_user_hosting"),
        })
@Data
@EqualsAndHashCode(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class McpServerEndpoint extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "endpoint_id", length = 64, nullable = false)
    private String endpointId;

    @Column(name = "mcp_server_id", length = 64, nullable = false)
    private String mcpServerId;

    @Column(name = "mcp_name", length = 128, nullable = false)
    private String mcpName;

    @Column(name = "endpoint_url", length = 512, nullable = false)
    private String endpointUrl;

    @Column(name = "hosting_type", length = 32, nullable = false)
    private String hostingType;

    @Column(name = "protocol", length = 32, nullable = false)
    private String protocol;

    @Column(name = "user_id", length = 64, nullable = false)
    private String userId;

    @Column(name = "hosting_instance_id", length = 64)
    private String hostingInstanceId;

    @Column(name = "hosting_identifier", length = 128)
    private String hostingIdentifier;

    @Column(name = "subscribe_params", columnDefinition = "json")
    private String subscribeParams;

    @Column(name = "status", length = 32, nullable = false)
    private String status;
}
