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

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.alibaba.himarket.core.security.ContextHolder;
import com.alibaba.himarket.core.utils.IdGenerator;
import com.alibaba.himarket.dto.params.mcp.SaveMcpMetaParam;
import com.alibaba.himarket.entity.McpServerEndpoint;
import com.alibaba.himarket.entity.McpServerMeta;
import com.alibaba.himarket.service.McpSandboxDeployService;
import com.alibaba.himarket.service.SandboxService;
import com.alibaba.himarket.support.enums.McpEndpointStatus;
import com.alibaba.himarket.support.enums.McpHostingType;
import java.security.SecureRandom;
import java.util.List;
import java.util.stream.Collectors;
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
@RequiredArgsConstructor
@Slf4j
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
        String transportType = StrUtil.blankToDefault(param.getTransportType(), "sse");
        String authType = StrUtil.blankToDefault(param.getAuthType(), "none");
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
                        .collect(Collectors.toList());
        for (McpServerEndpoint existing : existingPublic) {
            if (StrUtil.isNotBlank(existing.getHostingInstanceId())) {
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
        cn.hutool.json.JSONObject subParams =
                JSONUtil.createObj()
                        .set("sandboxId", sandboxId)
                        .set("sandboxName", sandbox.getSandboxName())
                        .set("transportType", transportType)
                        .set("authType", authType)
                        .set("namespace", StrUtil.blankToDefault(param.getNamespace(), "default"))
                        .set("resourceName", resourceName);
        if ("apikey".equalsIgnoreCase(authType) && StrUtil.isNotBlank(apiKey)) {
            subParams.set("apiKey", apiKey);
        }
        if (StrUtil.isNotBlank(paramValues)) {
            subParams.set("extraParams", JSONUtil.parse(paramValues));
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

        log.info("沙箱部署事件已发布（事务提交后执行）: mcpName={}, sandboxId={}", meta.getMcpName(), sandboxId);
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
                    || StrUtil.isBlank(ep.getHostingInstanceId())) {
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
                        "沙箱 undeploy 成功: mcpName={}, sandboxId={}, namespace={}, resourceName={}",
                        meta.getMcpName(),
                        ep.getHostingInstanceId(),
                        namespace,
                        resourceName);
            } catch (Exception e) {
                log.warn(
                        "沙箱 undeploy 失败（不阻塞删除）: mcpName={}, sandboxId={}, error={}",
                        meta.getMcpName(),
                        ep.getHostingInstanceId(),
                        e.getMessage());
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

    // ==================== SubscribeParams Extraction ====================

    public String extractNamespace(McpServerEndpoint endpoint) {
        if (endpoint == null || StrUtil.isBlank(endpoint.getSubscribeParams())) {
            return "default";
        }
        try {
            cn.hutool.json.JSONObject params = JSONUtil.parseObj(endpoint.getSubscribeParams());
            return StrUtil.blankToDefault(params.getStr("namespace"), "default");
        } catch (Exception e) {
            return "default";
        }
    }

    public String extractResourceName(McpServerEndpoint endpoint) {
        if (endpoint == null || StrUtil.isBlank(endpoint.getSubscribeParams())) {
            return null;
        }
        try {
            cn.hutool.json.JSONObject params = JSONUtil.parseObj(endpoint.getSubscribeParams());
            return params.getStr("resourceName");
        } catch (Exception e) {
            return null;
        }
    }

    public String extractSecretName(McpServerEndpoint endpoint) {
        if (endpoint == null || StrUtil.isBlank(endpoint.getSubscribeParams())) {
            return null;
        }
        try {
            cn.hutool.json.JSONObject params = JSONUtil.parseObj(endpoint.getSubscribeParams());
            return params.getStr("secretName");
        } catch (Exception e) {
            return null;
        }
    }
}
