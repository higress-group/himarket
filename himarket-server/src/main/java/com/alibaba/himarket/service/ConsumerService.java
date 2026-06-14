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
     * Creates a consumer.
     *
     * @param param consumer creation parameters
     * @return consumer result
     */
    ConsumerResult createConsumer(CreateConsumerParam param);

    /**
     * Creates a default consumer for a developer.
     *
     * @param param consumer creation parameters
     * @param developerId developer ID
     */
    void createConsumerInner(CreateConsumerParam param, String developerId);

    /**
     * Lists consumers.
     *
     * @param param consumer query parameters
     * @param pageable pagination parameters
     * @return paged consumer results
     */
    PageResult<ConsumerResult> listConsumers(QueryConsumerParam param, Pageable pageable);

    /**
     * Gets a consumer.
     *
     * @param consumerId consumer ID
     * @return consumer information
     */
    ConsumerResult getConsumer(String consumerId);

    /**
     * Deletes a consumer.
     *
     * @param consumerId consumer ID
     */
    void deleteConsumer(String consumerId);

    /**
     * Adds a credential to a consumer.
     *
     * @param consumerId consumer ID
     * @param param credential creation parameters
     */
    void createCredential(String consumerId, CreateCredentialParam param);

    /**
     * Gets a consumer credential.
     *
     * @param consumerId consumer ID
     * @return consumer credential information
     */
    ConsumerCredentialResult getCredential(String consumerId);

    /**
     * Updates a consumer credential.
     *
     * @param consumerId consumer ID
     * @param param credential update parameters
     */
    void updateCredential(String consumerId, UpdateCredentialParam param);

    /**
     * Deletes a consumer credential.
     *
     * @param consumerId consumer ID
     */
    void deleteCredential(String consumerId);

    /**
     * Subscribes a consumer to a product.
     *
     * @param consumerId consumer ID
     * @param param subscription creation parameters
     * @return created subscription information
     */
    SubscriptionResult subscribeProduct(String consumerId, CreateSubscriptionParam param);

    /**
     * Unsubscribes a consumer from a product.
     *
     * @param consumerId consumer ID
     * @param subscriptionId subscription ID, or product ID for backward compatibility
     */
    void unsubscribeProduct(String consumerId, String subscriptionId);

    /**
     * Lists subscriptions of a consumer.
     *
     * @param consumerId consumer ID
     * @param param subscription query parameters
     * @param pageable pagination parameters
     * @return paged subscription results
     */
    PageResult<SubscriptionResult> listSubscriptions(
            String consumerId, QuerySubscriptionParam param, Pageable pageable);

    /**
     * Lists subscriptions of a consumer.
     *
     * @param consumerId consumer ID
     * @return subscription results
     */
    List<SubscriptionResult> listConsumerSubscriptions(String consumerId);

    /**
     * Approves a subscription.
     *
     * @param consumerId consumer ID
     * @param subscriptionId subscription ID, or product ID for backward compatibility
     * @return approved subscription information
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
     * Sets the primary consumer.
     *
     * @param consumerId consumer ID
     */
    void setPrimaryConsumer(String consumerId);

    /**
     * Gets the primary consumer.
     *
     * @return primary consumer information
     */
    ConsumerResult getPrimaryConsumer();

    /**
     * Gets the primary consumer for the specified developer.
     *
     * @param developerId developer ID
     * @return primary consumer information
     */
    ConsumerResult getPrimaryConsumer(String developerId);
}
