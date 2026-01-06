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

import com.alibaba.himarket.entity.ProductSubscription;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SubscriptionRepository extends BaseRepository<ProductSubscription, Long> {

    Optional<ProductSubscription> findBySubscriptionId(String subscriptionId);

    Optional<ProductSubscription> findByConsumerIdAndProductId(String consumerId, String productId);

    List<ProductSubscription> findAllByConsumerId(String consumerId);

    List<ProductSubscription> findAllByProductId(String productId);

    void deleteAllByConsumerId(String consumerId);

    void deleteAllByProductId(String productId);

    void deleteByConsumerIdAndProductId(String consumerId, String productId);

    @Query(
            "SELECT s.productId, COUNT(s) FROM ProductSubscription s WHERE s.status = 'APPROVED'"
                    + " GROUP BY s.productId")
    List<Object[]> countApprovedSubscriptionsGroupedByProductId();

    @Query(
            "SELECT COUNT(s) FROM ProductSubscription s WHERE s.status = 'APPROVED'"
                    + " AND s.productId = :productId")
    Long countApprovedSubscriptionsByProductId(@Param("productId") String productId);
}
