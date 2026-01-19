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

public interface SubscriptionRepository extends BaseRepository<ProductSubscription, Long> {

    /**
     * Find subscription by subscription ID
     *
     * @param subscriptionId the subscription ID
     * @return the product subscription if found
     */
    Optional<ProductSubscription> findBySubscriptionId(String subscriptionId);

    /**
     * Find subscription by consumer ID and product ID
     *
     * @param consumerId the consumer ID
     * @param productId the product ID
     * @return the product subscription if found
     */
    Optional<ProductSubscription> findByConsumerIdAndProductId(String consumerId, String productId);

    /**
     * Find all subscriptions by consumer ID
     *
     * @param consumerId the consumer ID
     * @return the list of product subscriptions
     */
    List<ProductSubscription> findAllByConsumerId(String consumerId);

    /**
     * Find all subscriptions by product ID
     *
     * @param productId the product ID
     * @return the list of product subscriptions
     */
    List<ProductSubscription> findAllByProductId(String productId);

    /**
     * Delete all subscriptions by consumer ID
     *
     * @param consumerId the consumer ID
     */
    void deleteAllByConsumerId(String consumerId);

    /**
     * Delete all subscriptions by product ID
     *
     * @param productId the product ID
     */
    void deleteAllByProductId(String productId);

    /**
     * Delete subscription by consumer ID and product ID
     *
     * @param consumerId the consumer ID
     * @param productId the product ID
     */
    void deleteByConsumerIdAndProductId(String consumerId, String productId);
}
