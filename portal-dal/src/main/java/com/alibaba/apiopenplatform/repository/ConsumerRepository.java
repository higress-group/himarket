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

import com.alibaba.apiopenplatform.entity.Consumer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ConsumerRepository extends BaseRepository<Consumer, Long> {

    Optional<Consumer> findByConsumerId(String consumerId);

    Optional<Consumer> findByDeveloperIdAndConsumerId(String developerId, String consumerId);

    Optional<Consumer> findByDeveloperIdAndName(String developerId, String name);

    Page<Consumer> findByDeveloperId(String developerId, Pageable pageable);

    Page<Consumer> findByPortalId(String portalId, Pageable pageable);

    List<Consumer> findAllByDeveloperId(String developerId);

    void deleteAllByDeveloperId(String developerId);

    List<Consumer> findByConsumerIdIn(Collection<String> consumerIds);

    Optional<Consumer> findFirstByDeveloperId(String developerId, Sort sort);

    Optional<Consumer> findByDeveloperIdAndIsPrimary(String developerId, Boolean isPrimary);

    /**
     * Clear primary flag for all consumers under a developer
     *
     * @param developerId
     */
    @Modifying
    @Query("UPDATE Consumer c SET c.isPrimary = NULL WHERE c.developerId = :developerId")
    void clearPrimary(@Param("developerId") String developerId);
}
