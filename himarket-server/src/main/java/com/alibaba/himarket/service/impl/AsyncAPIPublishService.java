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

package com.alibaba.himarket.service.impl;

import cn.hutool.json.JSONUtil;
import com.alibaba.himarket.dto.result.api.APIDefinitionVO;
import com.alibaba.himarket.entity.Gateway;
import com.alibaba.himarket.entity.ProductRef;
import com.alibaba.himarket.repository.APIPublishRecordRepository;
import com.alibaba.himarket.repository.ProductRefRepository;
import com.alibaba.himarket.service.api.GatewayCapabilityRegistry;
import com.alibaba.himarket.service.api.GatewayPublisher;
import com.alibaba.himarket.support.api.PublishConfig;
import com.alibaba.himarket.support.enums.PublishStatus;
import com.alibaba.himarket.support.product.APIGRefConfig;
import com.alibaba.himarket.support.product.GatewayRefConfig;
import com.alibaba.himarket.support.product.HigressRefConfig;
import com.alibaba.himarket.support.product.SofaHigressRefConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Async API Publish Service
 *
 * <p>Handles asynchronous publish and unpublish operations for API definitions.
 * This service is separated to ensure @Async annotations work correctly without
 * requiring self-injection patterns.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AsyncAPIPublishService {

    private final GatewayCapabilityRegistry gatewayCapabilityRegistry;
    private final APIPublishRecordRepository apiPublishRecordRepository;
    private final ProductRefRepository productRefRepository;

    /**
     * Asynchronously execute publish operation
     *
     * @param recordId Publish record ID
     * @param gateway Gateway
     * @param apiDefinition API Definition
     * @param publishConfig Publish configuration
     */
    @Async("taskExecutor")
    public void asyncPublish(
            String recordId,
            Gateway gateway,
            APIDefinitionVO apiDefinition,
            PublishConfig publishConfig) {
        try {
            log.info("Starting async publish for record: {}", recordId);

            GatewayPublisher publisher = gatewayCapabilityRegistry.getPublisher(gateway);
            GatewayRefConfig gatewayRefConfig = publisher.publish(gateway, apiDefinition, publishConfig);

            // Update to success status
            updatePublishRecordStatus(recordId, PublishStatus.ACTIVE, JSONUtil.toJsonStr(gatewayRefConfig), null);

            // update product ref
            updateManagedProductRef(gateway, apiDefinition, gatewayRefConfig);

            log.info("Async publish completed for record: {}", recordId);
        } catch (Exception e) {
            log.error("Async publish failed for record: {}", recordId, e);
            updatePublishRecordStatus(recordId, PublishStatus.FAILED, null, e.getMessage());
        }
    }

    private void updateManagedProductRef(Gateway gateway, APIDefinitionVO apiDefinition, GatewayRefConfig gatewayRefConfig) {
        String apiDefinitionId = apiDefinition.getApiDefinitionId();
        Optional<ProductRef> ref = productRefRepository.findByApiDefinitionId(apiDefinitionId);
        if (ref.isEmpty()) {
            log.error("Product ref not found for api definition: {}", apiDefinitionId);
            throw new RuntimeException("Product ref not found for api definition" + apiDefinitionId);
        }
        ProductRef productRef = ref.get();
        // set gatewayId
        productRef.setGatewayId(gateway.getGatewayId());

        // set gateway ref config
        if (gateway.getGatewayType().isAdpAIGateway()) {
            productRef.setAdpAIGatewayRefConfig((APIGRefConfig) gatewayRefConfig);
        } else if (gateway.getGatewayType().isApsaraGateway()) {
            productRef.setApsaraGatewayRefConfig((APIGRefConfig) gatewayRefConfig);
        } else if (gateway.getGatewayType().isAPIG()) {
            productRef.setApigRefConfig((APIGRefConfig) gatewayRefConfig);
        } else if (gateway.getGatewayType().isHigress()) {
            productRef.setHigressRefConfig((HigressRefConfig) gatewayRefConfig);
        } else if (gateway.getGatewayType().isSofaHigress()) {
            productRef.setSofaHigressRefConfig((SofaHigressRefConfig) gatewayRefConfig);
        } else {
            log.error("Unsupported gateway type: {}", gateway.getGatewayType());
            throw new RuntimeException("Unsupported gateway type" + gateway.getGatewayType());
        }

        // update product ref
        productRefRepository.save(productRef);
    }

    /**
     * Asynchronously execute unpublish operation
     *
     * @param recordId Unpublish record ID
     * @param gateway Gateway
     * @param apiDefinition API Definition
     * @param publishConfig Publish configuration
     */
    @Async("taskExecutor")
    public void asyncUnpublish(
            String recordId,
            Gateway gateway,
            APIDefinitionVO apiDefinition,
            PublishConfig publishConfig) {
        try {
            log.info("Starting async unpublish for record: {}", recordId);

            GatewayPublisher publisher = gatewayCapabilityRegistry.getPublisher(gateway);
            publisher.unpublish(gateway, apiDefinition, publishConfig);

            // Update to INACTIVE status
            updatePublishRecordStatus(recordId, PublishStatus.INACTIVE, null, null);

            log.info("Async unpublish completed for record: {}", recordId);
        } catch (Exception e) {
            log.error("Async unpublish failed for record: {}", recordId, e);
            updatePublishRecordStatus(recordId, PublishStatus.FAILED, null, e.getMessage());
        }
    }

    /**
     * Update publish record status
     *
     * @param recordId Record ID
     * @param status Status
     * @param gatewayResourceId Gateway resource ID
     * @param errorMessage Error message
     */
    @Transactional
    protected void updatePublishRecordStatus(
            String recordId, PublishStatus status, String gatewayResourceId, String errorMessage) {
        apiPublishRecordRepository
                .findByRecordId(recordId)
                .ifPresent(
                        record -> {
                            record.setStatus(status);
                            if (gatewayResourceId != null) {
                                record.setGatewayResourceId(gatewayResourceId);
                            }
                            if (errorMessage != null) {
                                record.setErrorMessage(errorMessage);
                            }
                            apiPublishRecordRepository.save(record);
                            log.info("Updated publish record {} status to {}", recordId, status);
                        });
    }
}
