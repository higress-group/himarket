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

package com.alibaba.apiopenplatform.controller;

import com.alibaba.apiopenplatform.core.annotation.AdminAuth;
import com.alibaba.apiopenplatform.core.annotation.DeveloperAuth;
import com.alibaba.apiopenplatform.core.annotation.AdminOrDeveloperAuth;
import com.alibaba.apiopenplatform.dto.params.consumer.CreateCredentialParam;
import com.alibaba.apiopenplatform.dto.params.consumer.QueryConsumerParam;
import com.alibaba.apiopenplatform.dto.params.consumer.CreateConsumerParam;
import com.alibaba.apiopenplatform.dto.params.consumer.UpdateCredentialParam;
import com.alibaba.apiopenplatform.dto.params.consumer.CreateSubscriptionParam;
import com.alibaba.apiopenplatform.dto.params.consumer.QuerySubscriptionParam;
import com.alibaba.apiopenplatform.dto.result.consumer.ConsumerCredentialResult;
import com.alibaba.apiopenplatform.dto.result.consumer.ConsumerResult;
import com.alibaba.apiopenplatform.dto.result.common.PageResult;
import com.alibaba.apiopenplatform.dto.result.product.SubscriptionResult;
import com.alibaba.apiopenplatform.service.ConsumerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

@Tag(name = "Consumer管理", description = "提供Consumer注册、审批、产品订阅等管理功能")
@RestController
@RequestMapping("/consumers")
@RequiredArgsConstructor
@Validated
public class ConsumerController {

    private final ConsumerService consumerService;

    @Operation(summary = "获取Consumer列表")
    @GetMapping
    public PageResult<ConsumerResult> listConsumers(QueryConsumerParam param,
                                                    Pageable pageable) {
        return consumerService.listConsumers(param, pageable);
    }

    @Operation(summary = "获取Consumer")
    @GetMapping("/{consumerId}")
    public ConsumerResult getConsumer(@PathVariable String consumerId) {
        return consumerService.getConsumer(consumerId);
    }

    @Operation(summary = "注册Consumer")
    @PostMapping
    @DeveloperAuth
    public ConsumerResult createConsumer(@RequestBody @Valid CreateConsumerParam param) {
        return consumerService.createConsumer(param);
    }

    @Operation(summary = "删除Consumer")
    @DeleteMapping("/{consumerId}")
    public void deleteDevConsumer(@PathVariable String consumerId) {
        consumerService.deleteConsumer(consumerId);
    }

    @Operation(summary = "生成Consumer凭证")
    @PostMapping("/{consumerId}/credentials")
    @DeveloperAuth
    public void createCredential(@PathVariable String consumerId,
                                 @RequestBody @Valid CreateCredentialParam param) {
        consumerService.createCredential(consumerId, param);
    }

    @Operation(summary = "获取Consumer凭证信息")
    @GetMapping("/{consumerId}/credentials")
    @DeveloperAuth
    public ConsumerCredentialResult getCredential(@PathVariable String consumerId) {
        return consumerService.getCredential(consumerId);
    }

    @Operation(summary = "更新Consumer凭证")
    @PutMapping("/{consumerId}/credentials")
    @DeveloperAuth
    public void updateCredential(@PathVariable String consumerId,
                                 @RequestBody @Valid UpdateCredentialParam param) {
        consumerService.updateCredential(consumerId, param);
    }

    @Operation(summary = "删除Consumer凭证")
    @DeleteMapping("/{consumerId}/credentials")
    @DeveloperAuth
    public void deleteCredential(@PathVariable String consumerId) {
        consumerService.deleteCredential(consumerId);
    }

    @Operation(summary = "订阅API产品")
    @PostMapping("/{consumerId}/subscriptions")
    @DeveloperAuth
    public SubscriptionResult subscribeProduct(@PathVariable String consumerId,
                                               @RequestBody @Valid CreateSubscriptionParam param) {
        return consumerService.subscribeProduct(consumerId, param);
    }

    @Operation(summary = "获取Consumer的订阅列表")
    @GetMapping("/{consumerId}/subscriptions")
    @AdminOrDeveloperAuth
    public PageResult<SubscriptionResult> listSubscriptions(@PathVariable String consumerId,
                                                            QuerySubscriptionParam param,
                                                            Pageable pageable) {
        return consumerService.listSubscriptions(consumerId, param, pageable);
    }

    @Operation(summary = "取消订阅")
    @DeleteMapping("/{consumerId}/subscriptions/{productId}")
    public void deleteSubscription(@PathVariable String consumerId, @PathVariable String productId) {
        consumerService.unsubscribeProduct(consumerId, productId);
    }

    @Operation(summary = "审批订阅申请")
    @PatchMapping("/{consumerId}/subscriptions/{productId}")
    @AdminAuth
    public SubscriptionResult approveSubscription(@PathVariable String consumerId, @PathVariable String productId) {
        return consumerService.approveSubscription(consumerId, productId);
    }

    @Operation(summary = "设置Primary Consumer")
    @PutMapping("/{consumerId}/primary")
    @DeveloperAuth
    public void setPrimaryConsumer(@PathVariable String consumerId) {
        consumerService.setPrimaryConsumer(consumerId);
    }

    @Operation(summary = "获取Primary Consumer")
    @GetMapping("/primary")
    @DeveloperAuth
    public ConsumerResult getPrimaryConsumer() {
        return consumerService.getPrimaryConsumer();
    }
}
