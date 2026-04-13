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

import com.alibaba.himarket.entity.McpServerMeta;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface McpServerMetaRepository extends BaseRepository<McpServerMeta, Long> {

    Optional<McpServerMeta> findByMcpServerId(String mcpServerId);

    Optional<McpServerMeta> findByProductIdAndMcpName(String productId, String mcpName);

    List<McpServerMeta> findByProductIdIn(java.util.Collection<String> productIds);

    List<McpServerMeta> findByProductId(String productId);

    Optional<McpServerMeta> findByMcpName(String mcpName);

    /** 批量按 mcpName 查询（避免 N+1） */
    List<McpServerMeta> findByMcpNameIn(java.util.Collection<String> mcpNames);

    /** 批量按 mcpServerId 查询（避免 N+1） */
    List<McpServerMeta> findByMcpServerIdIn(java.util.Collection<String> mcpServerIds);

    Page<McpServerMeta> findByOrigin(String origin, Pageable pageable);

    /** 分页查询指定产品集合中、指定来源的 meta（Open API 用） */
    Page<McpServerMeta> findByProductIdInAndOrigin(
            java.util.Collection<String> productIds, String origin, Pageable pageable);

    /** 分页查询指定产品集合中的所有 meta（Open API 用） */
    Page<McpServerMeta> findByProductIdIn(
            java.util.Collection<String> productIds, Pageable pageable);
}
