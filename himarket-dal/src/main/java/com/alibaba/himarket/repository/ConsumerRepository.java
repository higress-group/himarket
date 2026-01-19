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

import com.alibaba.himarket.entity.Consumer;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ConsumerRepository extends BaseRepository<Consumer, Long> {

    /**
     * Find consumer by consumer ID
     *
     * @param consumerId the consumer ID
     * @return the consumer if found
     */
    Optional<Consumer> findByConsumerId(String consumerId);

    /**
     * Find consumer by developer ID and consumer ID
     *
     * @param developerId the developer ID
     * @param consumerId the consumer ID
     * @return the consumer if found
     */
    Optional<Consumer> findByDeveloperIdAndConsumerId(String developerId, String consumerId);

    /**
     * Find all consumers by developer ID
     *
     * @param developerId the developer ID
     * @return the list of consumers
     */
    List<Consumer> findAllByDeveloperId(String developerId);

    /**
     * Find consumers by consumer IDs
     *
     * @param consumerIds the collection of consumer IDs
     * @return the list of consumers
     */
    List<Consumer> findByConsumerIdIn(Collection<String> consumerIds);

    /**
     * Find first consumer by developer ID
     *
     * @param developerId the developer ID
     * @param sort the sort order
     * @return the first consumer if found
     */
    Optional<Consumer> findFirstByDeveloperId(String developerId, Sort sort);

    /**
     * Find primary consumer by developer ID
     *
     * @param developerId the developer ID
     * @param isPrimary the primary flag
     * @return the primary consumer if found
     */
    Optional<Consumer> findByDeveloperIdAndIsPrimary(String developerId, Boolean isPrimary);

    /**
     * Clear primary flag for all consumers under a developer
     *
     * @param developerId the developer ID
     */
    @Modifying
    @Query("UPDATE Consumer c SET c.isPrimary = NULL WHERE c.developerId = :developerId")
    void clearPrimary(@Param("developerId") String developerId);
}
