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

import com.alibaba.himarket.core.security.ContextHolder;
import com.alibaba.himarket.core.utils.IdGenerator;
import com.alibaba.himarket.dto.params.mcp.SaveMcpMetaParam;
import com.alibaba.himarket.entity.McpServerEndpoint;
import com.alibaba.himarket.entity.McpServerMeta;
import com.alibaba.himarket.service.McpSandboxDeployService;
import com.alibaba.himarket.service.SandboxService;
import com.alibaba.himarket.support.common.Strings;
import com.alibaba.himarket.support.enums.McpEndpointStatus;
import com.alibaba.himarket.support.enums.McpHostingType;
import com.alibaba.himarket.utils.JsonUtil;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.security.SecureRandom;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Orchestrates sandbox deployment for MCP Servers.
 *
 * <p>Extracted from McpServerServiceImpl to handle:
 * <ul>
 *   <li>Sandbox deploy (DB pre-create + event publish)</li>
 *   <li>Sandbox undeploy (CRD cleanup)</li>
 *   <li>API Key generation</li>
 *   <li>SubscribeParams extraction helpers</li>
 * </ul>
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class McpSandboxOrchestrator {

    private final McpConfigSyncHelper configSyncHelper;
    private final SandboxService sandboxService;
    private final McpSandboxDeployService mcpSandboxDeployService;
    private final ApplicationEventPublisher eventPublisher;
    private final ContextHolder contextHolder;

    /**
     * Pre-deploy sandbox: DB operations only (pre-create endpoint in INACTIVE state).
     * Actual K8s CRD deployment happens after transaction commit via McpSandboxDeployEvent.
     */
    public void doDeploySandbox(McpServerMeta meta, SaveMcpMetaParam param) {
        String sandboxId = param.getSandboxId();
        String transportType = Strings.blankToDefault(param.getTransportType(), "sse");
        String authType = Strings.blankToDefault(param.getAuthType(), "none");
        String paramValues = param.getParamValues();
        String adminUserId = getCreatedByOrDefault();

        String apiKey = "";
        if ("apikey".equalsIgnoreCase(authType)) {
            apiKey = generateApiKey();
        }

        // Clean up old public sandbox endpoints (DB records)
        List<McpServerEndpoint> existingPublic =
                configSyncHelper.findEndpointsByMcpServerId(meta.getMcpServerId()).stream()
                        .filter(ep -> McpEndpointStatus.PUBLIC_USER_ID.equals(ep.getUserId()))
                        .filter(
                                ep ->
                                        McpHostingType.SANDBOX
                                                .name()
                                                .equalsIgnoreCase(ep.getHostingType()))
                        .toList();
        for (McpServerEndpoint existing : existingPublic) {
            if (Strings.isNotBlank(existing.getHostingInstanceId())) {
                eventPublisher.publishEvent(
                        McpSandboxUndeployEvent.builder()
                                .sandboxId(existing.getHostingInstanceId())
                                .mcpName(existing.getMcpName())
                                .userId(adminUserId)
                                .namespace(extractNamespace(existing))
                                .resourceName(extractResourceName(existing))
                                .secretName(extractSecretName(existing))
                                .build());
            }
            configSyncHelper.deleteEndpoint(existing);
        }

        if (!existingPublic.isEmpty()) {
            configSyncHelper.flushEndpoints();
        }

        var sandbox = sandboxService.getSandbox(sandboxId);

        String resourceName =
                AgentRuntimeDeployStrategy.buildResourceNameStatic(meta.getMcpName(), adminUserId);
        ObjectNode subParams = JsonUtil.createObjectNode();
        subParams.put("sandboxId", sandboxId);
        subParams.put("sandboxName", sandbox.getSandboxName());
        subParams.put("transportType", transportType);
        subParams.put("authType", authType);
        subParams.put("namespace", Strings.blankToDefault(param.getNamespace(), "default"));
        subParams.put("resourceName", resourceName);
        if ("apikey".equalsIgnoreCase(authType) && Strings.isNotBlank(apiKey)) {
            subParams.put("apiKey", apiKey);
        }
        if (Strings.isNotBlank(paramValues)) {
            subParams.set("extraParams", JsonUtil.readTree(paramValues));
        }

        McpServerEndpoint pendingEndpoint =
                McpServerEndpoint.builder()
                        .endpointId(IdGenerator.genEndpointId())
                        .mcpServerId(meta.getMcpServerId())
                        .mcpName(meta.getMcpName())
                        .endpointUrl("")
                        .hostingType(McpHostingType.SANDBOX.name())
                        .protocol(transportType)
                        .userId(McpEndpointStatus.PUBLIC_USER_ID)
                        .hostingInstanceId(sandboxId)
                        .hostingIdentifier(sandbox.getSandboxName())
                        .subscribeParams(subParams.toString())
                        .status(McpEndpointStatus.INACTIVE.name())
                        .build();
        configSyncHelper.saveEndpoint(pendingEndpoint);

        eventPublisher.publishEvent(
                McpSandboxDeployEvent.builder()
                        .sandboxId(sandboxId)
                        .mcpServerId(meta.getMcpServerId())
                        .mcpName(meta.getMcpName())
                        .adminUserId(adminUserId)
                        .transportType(transportType)
                        .metaProtocolType(meta.getProtocolType())
                        .connectionConfig(meta.getConnectionConfig())
                        .authType(authType)
                        .paramValues(paramValues)
                        .extraParams(meta.getExtraParams())
                        .namespace(param.getNamespace())
                        .resourceSpec(param.getResourceSpec())
                        .endpointId(pendingEndpoint.getEndpointId())
                        .apiKey(apiKey)
                        .build());

        log.info(
                "Sandbox deployment event published after transaction commit, mcpName={},"
                        + " sandboxId={}",
                meta.getMcpName(),
                sandboxId);
    }

    /**
     * Undeploy all sandbox-hosted endpoints for a given meta.
     * Failures do not block the delete flow.
     */
    public void undeploySandboxEndpoints(McpServerMeta meta) {
        List<McpServerEndpoint> endpoints =
                configSyncHelper.findEndpointsByMcpServerId(meta.getMcpServerId());
        for (McpServerEndpoint ep : endpoints) {
            if (!McpHostingType.SANDBOX.name().equalsIgnoreCase(ep.getHostingType())
                    || Strings.isBlank(ep.getHostingInstanceId())) {
                continue;
            }
            try {
                String namespace = extractNamespace(ep);
                String resourceName = extractResourceName(ep);
                String secretName = extractSecretName(ep);
                mcpSandboxDeployService.undeploy(
                        ep.getHostingInstanceId(),
                        meta.getMcpName(),
                        ep.getUserId(),
                        namespace,
                        resourceName,
                        secretName);
                log.info(
                        "Sandbox undeploy succeeded, mcpName={}, sandboxId={}, namespace={},"
                                + " resourceName={}",
                        meta.getMcpName(),
                        ep.getHostingInstanceId(),
                        namespace,
                        resourceName);
            } catch (Exception e) {
                log.warn(
                        "Sandbox undeploy failed and does not block deletion, mcpName={},"
                                + " sandboxId={}, errorMessage={}",
                        meta.getMcpName(),
                        ep.getHostingInstanceId(),
                        e.getMessage(),
                        e);
            }
        }
    }

    /**
     * Generate a cryptographically secure API Key.
     * Format: sk_ + 32 random alphanumeric characters (total length 35).
     */
    public String generateApiKey() {
        SecureRandom random = new SecureRandom();
        String chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        StringBuilder sb = new StringBuilder("sk_");
        for (int i = 0; i < 32; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }

    public String getCreatedByOrDefault() {
        try {
            return contextHolder.getUser();
        } catch (Exception e) {
            return "open-api";
        }
    }

    // SubscribeParams extraction helpers.

    public String extractNamespace(McpServerEndpoint endpoint) {
        if (endpoint == null || Strings.isBlank(endpoint.getSubscribeParams())) {
            return "default";
        }
        try {
            ObjectNode params = JsonUtil.readObjectNode(endpoint.getSubscribeParams());
            return Strings.blankToDefault(params.path("namespace").asText(), "default");
        } catch (Exception e) {
            return "default";
        }
    }

    public String extractResourceName(McpServerEndpoint endpoint) {
        if (endpoint == null || Strings.isBlank(endpoint.getSubscribeParams())) {
            return null;
        }
        try {
            ObjectNode params = JsonUtil.readObjectNode(endpoint.getSubscribeParams());
            return params.path("resourceName").asText();
        } catch (Exception e) {
            return null;
        }
    }

    public String extractSecretName(McpServerEndpoint endpoint) {
        if (endpoint == null || Strings.isBlank(endpoint.getSubscribeParams())) {
            return null;
        }
        try {
            ObjectNode params = JsonUtil.readObjectNode(endpoint.getSubscribeParams());
            return params.path("secretName").asText();
        } catch (Exception e) {
            return null;
        }
    }
}
