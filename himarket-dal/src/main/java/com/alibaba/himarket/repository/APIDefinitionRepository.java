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

import com.alibaba.himarket.entity.ApiDefinition;
import com.alibaba.himarket.support.enums.ApiStatus;
import com.alibaba.himarket.support.enums.ApiType;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

@Repository
public interface APIDefinitionRepository extends BaseRepository<ApiDefinition, Long> {

    Optional<ApiDefinition> findByApiDefinitionId(String apiDefinitionId);

    Page<ApiDefinition> findByType(ApiType type, Pageable pageable);

    Page<ApiDefinition> findByStatus(ApiStatus status, Pageable pageable);

    Page<ApiDefinition> findByTypeAndStatus(ApiType type, ApiStatus status, Pageable pageable);

    Page<ApiDefinition> findByNameContaining(String keyword, Pageable pageable);

    Page<ApiDefinition> findByTypeAndNameContaining(
            ApiType type, String keyword, Pageable pageable);

    boolean existsByApiDefinitionId(String apiDefinitionId);
}
