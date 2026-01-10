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

package com.alibaba.himarket.service;

import com.alibaba.himarket.dto.params.consumer.CreateConsumerParam;
import com.alibaba.himarket.dto.params.consumer.CreateCredentialParam;
import com.alibaba.himarket.dto.params.consumer.CreateSubscriptionParam;
import com.alibaba.himarket.dto.params.consumer.QueryConsumerParam;
import com.alibaba.himarket.dto.params.consumer.QuerySubscriptionParam;
import com.alibaba.himarket.dto.params.consumer.UpdateCredentialParam;
import com.alibaba.himarket.dto.result.common.PageResult;
import com.alibaba.himarket.dto.result.consumer.ConsumerCredentialResult;
import com.alibaba.himarket.dto.result.consumer.ConsumerResult;
import com.alibaba.himarket.dto.result.consumer.CredentialContext;
import com.alibaba.himarket.dto.result.product.SubscriptionResult;
import java.util.List;
import org.springframework.data.domain.Pageable;

public interface ConsumerService {

    /**
     * Create a consumer
     *
     * @param param consumer creation parameters
     * @return consumer result
     */
    ConsumerResult createConsumer(CreateConsumerParam param);

    /**
     * Create a default consumer for a developer
     *
     * @param param consumer creation parameters
     * @param developerId developer ID
     */
    void createConsumerInner(CreateConsumerParam param, String developerId);

    /**
     * List consumers
     *
     * @param param
     * @param pageable
     * @return
     */
    PageResult<ConsumerResult> listConsumers(QueryConsumerParam param, Pageable pageable);

    /**
     * Get a consumer
     *
     * @param consumerId
     * @return
     */
    ConsumerResult getConsumer(String consumerId);

    /**
     * Delete a consumer
     *
     * @param consumerId
     */
    void deleteConsumer(String consumerId);

    /**
     * Add a credential to a consumer
     *
     * @param consumerId
     * @param param
     */
    void createCredential(String consumerId, CreateCredentialParam param);

    /**
     * Get a consumer's credential
     *
     * @param consumerId
     * @return
     */
    ConsumerCredentialResult getCredential(String consumerId);

    /**
     * Update a consumer's credential
     *
     * @param consumerId
     * @param param
     */
    void updateCredential(String consumerId, UpdateCredentialParam param);

    /**
     * Delete a consumer's credential
     *
     * @param consumerId Consumer ID
     */
    void deleteCredential(String consumerId);

    /**
     * Subscribe a product by a consumer
     *
     * @param consumerId
     * @param param
     * @return
     */
    SubscriptionResult subscribeProduct(String consumerId, CreateSubscriptionParam param);

    /**
     * Unsubscribe a product
     *
     * @param consumerId Consumer ID
     * @param subscriptionId Subscription ID (supports productId for backward compatibility)
     */
    void unsubscribeProduct(String consumerId, String subscriptionId);

    /**
     * List subscriptions of a consumer
     *
     * @param consumerId
     * @param param
     * @param pageable
     * @return
     */
    PageResult<SubscriptionResult> listSubscriptions(
            String consumerId, QuerySubscriptionParam param, Pageable pageable);

    /**
     * List subscriptions of a consumer
     *
     * @param consumerId
     * @return
     */
    List<SubscriptionResult> listConsumerSubscriptions(String consumerId);

    /**
     * Approve a subscription
     *
     * @param consumerId Consumer ID
     * @param subscriptionId Subscription ID (supports productId for backward compatibility)
     */
    SubscriptionResult approveSubscription(String consumerId, String subscriptionId);

    /**
     * Get default credential authentication info for developer Returns empty maps if consumer or
     * credential not found
     *
     * @param developerId developer ID
     * @return credential authentication info (never null, but maps may be empty)
     */
    CredentialContext getDefaultCredential(String developerId);

    /**
     * Set primary consumer
     *
     * @param consumerId Consumer ID
     */
    void setPrimaryConsumer(String consumerId);

    /**
     * Obtain primary consumer
     *
     * @return ConsumerResult
     */
    ConsumerResult getPrimaryConsumer();
}
