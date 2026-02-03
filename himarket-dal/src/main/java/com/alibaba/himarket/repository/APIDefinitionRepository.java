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

import com.alibaba.himarket.entity.APIDefinition;
import com.alibaba.himarket.support.enums.APIStatus;
import com.alibaba.himarket.support.enums.APIType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface APIDefinitionRepository extends BaseRepository<APIDefinition, Long> {

    Optional<APIDefinition> findByApiDefinitionId(String apiDefinitionId);

    Page<APIDefinition> findByType(APIType type, Pageable pageable);

    Page<APIDefinition> findByStatus(APIStatus status, Pageable pageable);

    Page<APIDefinition> findByTypeAndStatus(APIType type, APIStatus status, Pageable pageable);

    List<APIDefinition> findByStatus(APIStatus status);

    Page<APIDefinition> findByNameContaining(String keyword, Pageable pageable);

    Page<APIDefinition> findByTypeAndNameContaining(
            APIType type, String keyword, Pageable pageable);

    boolean existsByApiDefinitionId(String apiDefinitionId);
}
