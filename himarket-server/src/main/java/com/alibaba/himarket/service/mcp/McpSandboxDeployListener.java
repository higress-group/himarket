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

package com.alibaba.himarket.service.mcp;

import com.alibaba.himarket.repository.McpServerEndpointRepository;
import com.alibaba.himarket.service.McpSandboxDeployService;
import com.alibaba.himarket.support.common.Strings;
import com.alibaba.himarket.support.enums.McpEndpointStatus;
import jakarta.annotation.Resource;
import java.util.concurrent.Executor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Handles sandbox deployment events after transaction commit.
 *
 * <p>K8s CRD resources are deployed only after the database records are committed, preventing
 * resource leaks when the transaction rolls back.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class McpSandboxDeployListener {

    private final McpSandboxDeployService mcpSandboxDeployService;
    private final McpServerEndpointRepository endpointRepository;

    @Resource(name = "taskExecutor")
    private Executor taskExecutor;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSandboxDeploy(McpSandboxDeployEvent event) {
        taskExecutor.execute(() -> doDeployAsync(event));
    }

    private void doDeployAsync(McpSandboxDeployEvent event) {
        String endpointUrl = null;
        try {
            String rawResult =
                    mcpSandboxDeployService.deploy(
                            event.getSandboxId(),
                            event.getMcpServerId(),
                            event.getMcpName(),
                            event.getAdminUserId(),
                            event.getTransportType(),
                            event.getMetaProtocolType(),
                            event.getConnectionConfig(),
                            event.getApiKey(),
                            event.getAuthType(),
                            event.getParamValues(),
                            event.getExtraParams(),
                            event.getNamespace(),
                            event.getResourceSpec());

            // deploy() returns either endpointUrl or endpointUrl|SECRET:secretName.
            String secretName = null;
            endpointUrl = rawResult;
            if (rawResult != null && rawResult.contains("|SECRET:")) {
                int idx = rawResult.indexOf("|SECRET:");
                endpointUrl = rawResult.substring(0, idx);
                secretName = rawResult.substring(idx + 8); // "|SECRET:".length() == 8
            }

            // Normalize the URL by trimming trailing slashes and adding /sse for SSE endpoints.
            String finalEndpointUrl =
                    McpProtocolUtils.normalizeEndpointUrl(endpointUrl, event.getTransportType());

            String lambdaUrl = finalEndpointUrl;
            String lambdaSecretName = secretName;
            endpointRepository
                    .findByEndpointId(event.getEndpointId())
                    .ifPresent(
                            ep -> {
                                ep.setEndpointUrl(lambdaUrl);
                                ep.setStatus(McpEndpointStatus.ACTIVE.name());
                                if (Strings.isNotBlank(lambdaSecretName)
                                        && Strings.isNotBlank(ep.getSubscribeParams())) {
                                    try {
                                        com.fasterxml.jackson.databind.node.ObjectNode params =
                                                com.alibaba.himarket.utils.JsonUtil.readObjectNode(
                                                        ep.getSubscribeParams());
                                        params.put("secretName", lambdaSecretName);
                                        ep.setSubscribeParams(params.toString());
                                    } catch (Exception e) {
                                        log.warn(
                                                "Failed to write secretName into subscribeParams,"
                                                        + " errorMessage={}",
                                                e.getMessage(),
                                                e);
                                    }
                                }
                                endpointRepository.save(ep);
                            });

            log.info(
                    "Sandbox CRD deployment succeeded, mcpName={}, sandboxId={}, endpoint={}",
                    event.getMcpName(),
                    event.getSandboxId(),
                    finalEndpointUrl);

        } catch (Exception e) {
            log.error(
                    "Sandbox CRD deployment failed, mcpName={}, sandboxId={}",
                    event.getMcpName(),
                    event.getSandboxId(),
                    e);

            endpointRepository
                    .findByEndpointId(event.getEndpointId())
                    .ifPresent(
                            ep -> {
                                ep.setStatus(McpEndpointStatus.INACTIVE.name());
                                endpointRepository.save(ep);
                            });

            // Roll back the CRD when deployment produced an endpoint before failing.
            if (endpointUrl != null) {
                try {
                    String rollbackResourceName =
                            AgentRuntimeDeployStrategy.buildResourceNameStatic(
                                    event.getMcpName(), event.getAdminUserId());
                    mcpSandboxDeployService.undeploy(
                            event.getSandboxId(),
                            event.getMcpName(),
                            event.getAdminUserId(),
                            Strings.blankToDefault(event.getNamespace(), "default"),
                            rollbackResourceName,
                            null); // Secret already cleaned up in deploy() rollback
                } catch (Exception re) {
                    log.warn(
                            "Failed to roll back CRD deployment, errorMessage={}",
                            re.getMessage(),
                            re);
                }
            }
        }
    }

    /**
     * Handles {@link McpSandboxUndeployEvent} after transaction commit and clears old sandbox CRDs
     * asynchronously.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSandboxUndeploy(McpSandboxUndeployEvent event) {
        taskExecutor.execute(
                () -> {
                    log.info(
                            "Transaction committed, clearing old sandbox CRD asynchronously,"
                                    + " mcpName={}, sandboxId={}",
                            event.getMcpName(),
                            event.getSandboxId());
                    try {
                        mcpSandboxDeployService.undeploy(
                                event.getSandboxId(),
                                event.getMcpName(),
                                event.getUserId(),
                                Strings.blankToDefault(event.getNamespace(), "default"),
                                event.getResourceName(),
                                event.getSecretName());
                        log.info(
                                "Old sandbox CRD cleanup succeeded, mcpName={}, sandboxId={}",
                                event.getMcpName(),
                                event.getSandboxId());
                    } catch (Exception e) {
                        log.warn(
                                "Old sandbox CRD cleanup failed and does not block the new"
                                        + " deployment, mcpName={}, sandboxId={}, errorMessage={}",
                                event.getMcpName(),
                                event.getSandboxId(),
                                e.getMessage(),
                                e);
                    }
                });
    }
}
