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

package com.alibaba.himarket.repository;

import com.alibaba.himarket.entity.McpServerEndpoint;
import java.util.List;
import java.util.Optional;

public interface McpServerEndpointRepository extends BaseRepository<McpServerEndpoint, Long> {

    Optional<McpServerEndpoint> findByEndpointId(String endpointId);

    List<McpServerEndpoint> findByMcpServerId(String mcpServerId);

    List<McpServerEndpoint> findByMcpServerIdAndStatus(String mcpServerId, String status);

    /**
     * Queries endpoints visible to a user, including endpoints owned by the user or by *.
     */
    List<McpServerEndpoint> findByMcpServerIdAndUserIdInAndStatus(
            String mcpServerId, List<String> userIds, String status);

    /**
     * Batch queries public endpoints for multiple mcpServerIds.
     */
    List<McpServerEndpoint> findByMcpServerIdInAndUserIdInAndStatus(
            java.util.Collection<String> mcpServerIds, List<String> userIds, String status);

    List<McpServerEndpoint> findByHostingTypeAndHostingInstanceId(
            String hostingType, String hostingInstanceId);

    /**
     * Queries all endpoints owned by the user or by *.
     */
    List<McpServerEndpoint> findByUserIdIn(List<String> userIds);

    /**
     * Finds an endpoint by mcpServerId, userId, and hostingInstanceId for upsert.
     */
    Optional<McpServerEndpoint> findByMcpServerIdAndUserIdAndHostingInstanceId(
            String mcpServerId, String userId, String hostingInstanceId);

    void deleteByMcpServerId(String mcpServerId);
}
