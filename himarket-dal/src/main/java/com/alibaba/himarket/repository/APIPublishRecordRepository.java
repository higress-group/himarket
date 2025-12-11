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

package com.alibaba.apiopenplatform.repository;

import com.alibaba.apiopenplatform.entity.APIPublishRecord;
import com.alibaba.apiopenplatform.support.enums.PublishStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface APIPublishRecordRepository extends BaseRepository<APIPublishRecord, Long> {

    Optional<APIPublishRecord> findByRecordId(String recordId);

    List<APIPublishRecord> findByApiDefinitionId(String apiDefinitionId);

    Page<APIPublishRecord> findByApiDefinitionId(String apiDefinitionId, Pageable pageable);

    List<APIPublishRecord> findByApiDefinitionIdAndStatus(String apiDefinitionId, PublishStatus status);

    Optional<APIPublishRecord> findByApiDefinitionIdAndGatewayId(String apiDefinitionId, String gatewayId);

    List<APIPublishRecord> findByGatewayId(String gatewayId);

    void deleteByApiDefinitionId(String apiDefinitionId);
}
