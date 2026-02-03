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

import com.alibaba.himarket.entity.APIDefinition;
import com.alibaba.himarket.entity.Gateway;
import com.alibaba.himarket.entity.ProductRef;
import com.alibaba.himarket.repository.APIDeploymentRepository;
import com.alibaba.himarket.repository.ProductRefRepository;
import com.alibaba.himarket.service.GatewayService;
import com.alibaba.himarket.service.api.GatewayCapabilityRegistry;
import com.alibaba.himarket.service.api.GatewayPublisher;
import com.alibaba.himarket.support.api.DeploymentConfig;
import com.alibaba.himarket.support.enums.PublishStatus;
import com.alibaba.himarket.support.product.APIGRefConfig;
import com.alibaba.himarket.support.product.GatewayRefConfig;
import com.alibaba.himarket.support.product.HigressRefConfig;
import com.alibaba.himarket.support.product.SofaHigressRefConfig;
import com.alibaba.himarket.utils.JsonUtil;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final APIDeploymentRepository apiPublishRecordRepository;
    private final ProductRefRepository productRefRepository;
    private final GatewayService gatewayService;

    /**
     * Asynchronously execute publish operation
     *
     * @param deploymentId Publish record ID
     * @param gateway Gateway
     * @param apiDefinition API Definition entity
     * @param deploymentConfig Publish configuration
     */
    @Async("taskExecutor")
    public void asyncPublish(
            String deploymentId,
            Gateway gateway,
            APIDefinition apiDefinition,
            DeploymentConfig deploymentConfig) {
        try {
            log.info("Starting async publish for record: {}", deploymentId);

            GatewayPublisher publisher = gatewayCapabilityRegistry.getPublisher(gateway);
            GatewayRefConfig gatewayRefConfig =
                    publisher.publish(gateway, apiDefinition, deploymentConfig);

            // Update to success status
            updatePublishRecordStatus(
                    deploymentId, PublishStatus.ACTIVE, JsonUtil.toJson(gatewayRefConfig), null);

            // update product ref
            updateManagedProductRef(gateway, apiDefinition, gatewayRefConfig);

            log.info("Async publish completed for record: {}", deploymentId);
        } catch (Exception e) {
            log.error("Async publish failed for record: {}", deploymentId, e);
            updatePublishRecordStatus(
                    deploymentId, PublishStatus.PUBLISH_FAILED, null, e.getMessage());
        }
    }

    private void updateManagedProductRef(
            Gateway gateway, APIDefinition apiDefinition, GatewayRefConfig gatewayRefConfig) {
        String apiDefinitionId = apiDefinition.getApiDefinitionId();
        Optional<ProductRef> ref = productRefRepository.findByApiDefinitionId(apiDefinitionId);
        if (ref.isEmpty()) {
            log.error("Product ref not found for api definition: {}", apiDefinitionId);
            throw new RuntimeException(
                    "Product ref not found for api definition" + apiDefinitionId);
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

        // Handle different configurations based on product type
        switch (apiDefinition.getType()) {
            case REST_API:
                productRef.setApiConfig(
                        gatewayService.fetchAPIConfig(gateway.getGatewayId(), gatewayRefConfig));
                break;
            case MCP_SERVER:
                productRef.setMcpConfig(
                        gatewayService.fetchMcpConfig(gateway.getGatewayId(), gatewayRefConfig));
                break;
            case AGENT_API:
                productRef.setAgentConfig(
                        gatewayService.fetchAgentConfig(gateway.getGatewayId(), gatewayRefConfig));
                break;
            case MODEL_API:
                productRef.setModelConfig(
                        gatewayService.fetchModelConfig(gateway.getGatewayId(), gatewayRefConfig));
                break;
        }

        // update product ref
        productRefRepository.save(productRef);
    }

    /**
     * Asynchronously execute unpublish operation
     *
     * @param deploymentId Unpublish record ID
     * @param gateway Gateway
     * @param apiDefinition API Definition entity
     * @param deploymentConfig Publish configuration
     */
    @Async("taskExecutor")
    public void asyncUnpublish(
            String deploymentId,
            Gateway gateway,
            APIDefinition apiDefinition,
            DeploymentConfig deploymentConfig) {
        try {
            log.info("Starting async unpublish for record: {}", deploymentId);

            GatewayPublisher publisher = gatewayCapabilityRegistry.getPublisher(gateway);
            publisher.unpublish(gateway, apiDefinition, deploymentConfig);

            // Update to INACTIVE status
            updatePublishRecordStatus(deploymentId, PublishStatus.INACTIVE, null, null);

            log.info("Async unpublish completed for record: {}", deploymentId);
        } catch (Exception e) {
            log.error("Async unpublish failed for record: {}", deploymentId, e);
            updatePublishRecordStatus(
                    deploymentId, PublishStatus.UNPUBLISH_FAILED, null, e.getMessage());
        }
    }

    /**
     * Update publish record status
     *
     * @param deploymentId Record ID
     * @param status Status
     * @param gatewayResourceConfig Gateway resource config
     * @param errorMessage Error message
     */
    @Transactional
    protected void updatePublishRecordStatus(
            String deploymentId,
            PublishStatus status,
            String gatewayResourceConfig,
            String errorMessage) {
        apiPublishRecordRepository
                .findByDeploymentId(deploymentId)
                .ifPresent(
                        record -> {
                            record.setStatus(status);
                            if (gatewayResourceConfig != null) {
                                record.setGatewayResourceConfig(gatewayResourceConfig);
                            }
                            if (errorMessage != null) {
                                record.setErrorMessage(errorMessage);
                            }
                            apiPublishRecordRepository.save(record);
                            log.info(
                                    "Updated publish record {} status to {}", deploymentId, status);
                        });
    }
}
