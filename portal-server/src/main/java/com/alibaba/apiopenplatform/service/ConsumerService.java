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

package com.alibaba.apiopenplatform.service;

import com.alibaba.apiopenplatform.dto.params.consumer.QueryConsumerParam;
import com.alibaba.apiopenplatform.dto.params.consumer.CreateConsumerParam;
import com.alibaba.apiopenplatform.dto.result.consumer.ConsumerResult;
import com.alibaba.apiopenplatform.dto.result.common.PageResult;
import com.alibaba.apiopenplatform.dto.result.consumer.ConsumerCredentialResult;
import com.alibaba.apiopenplatform.dto.result.consumer.CredentialContext;
import com.alibaba.apiopenplatform.dto.params.consumer.CreateCredentialParam;
import com.alibaba.apiopenplatform.dto.params.consumer.UpdateCredentialParam;
import com.alibaba.apiopenplatform.dto.result.product.SubscriptionResult;
import com.alibaba.apiopenplatform.dto.params.consumer.CreateSubscriptionParam;
import com.alibaba.apiopenplatform.dto.params.consumer.QuerySubscriptionParam;
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
     * @param param       consumer creation parameters
     * @param developerId developer ID
     */
    void createConsumerInner(CreateConsumerParam param, String developerId);

    /**
     * 获取Consumer列表
     *
     * @param param
     * @param pageable
     * @return
     */
    PageResult<ConsumerResult> listConsumers(QueryConsumerParam param, Pageable pageable);

    /**
     * 查询Consumer
     *
     * @param consumerId
     * @return
     */
    ConsumerResult getConsumer(String consumerId);

    /**
     * 删除Consumer
     *
     * @param consumerId
     */
    void deleteConsumer(String consumerId);

    /**
     * 创建Consumer凭证
     *
     * @param consumerId
     * @param param
     */
    void createCredential(String consumerId, CreateCredentialParam param);

    /**
     * 获取Consumer凭证
     *
     * @param consumerId
     * @return
     */
    ConsumerCredentialResult getCredential(String consumerId);

    /**
     * 更新Consumer凭证
     *
     * @param consumerId
     * @param param
     */
    void updateCredential(String consumerId, UpdateCredentialParam param);

    /**
     * 删除Consumer凭证
     *
     * @param consumerId Consumer ID
     */
    void deleteCredential(String consumerId);

    /**
     * 订阅API产品
     *
     * @param consumerId
     * @param param
     * @return
     */
    SubscriptionResult subscribeProduct(String consumerId, CreateSubscriptionParam param);

    /**
     * 取消订阅
     *
     * @param consumerId
     * @param productId
     */
    void unsubscribeProduct(String consumerId, String productId);

    /**
     * 获取Consumer的订阅列表
     *
     * @param consumerId
     * @param param
     * @param pageable
     * @return
     */
    PageResult<SubscriptionResult> listSubscriptions(String consumerId, QuerySubscriptionParam param, Pageable pageable);

    /**
     * 取消订阅API产品
     *
     * @param consumerId
     * @param productId
     */
    void deleteSubscription(String consumerId, String productId);

    /**
     * 审批订阅API产品
     *
     * @param consumerId
     * @param productId
     */
    SubscriptionResult approveSubscription(String consumerId, String productId);

    /**
     * Get default credential authentication info for developer
     * Returns empty maps if consumer or credential not found
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
