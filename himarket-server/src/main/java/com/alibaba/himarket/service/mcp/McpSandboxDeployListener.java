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
import com.alibaba.himarket.entity.McpServerMeta;
import com.alibaba.himarket.repository.McpServerEndpointRepository;
import com.alibaba.himarket.repository.McpServerMetaRepository;
import com.alibaba.himarket.service.McpSandboxDeployService;
import com.alibaba.himarket.service.hichat.manager.ToolManager;
import com.alibaba.himarket.support.chat.mcp.MCPTransportConfig;
import com.alibaba.himarket.support.enums.MCPTransportMode;
import com.alibaba.himarket.support.enums.McpEndpointStatus;
import com.alibaba.himarket.support.enums.McpProtocolType;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.tool.mcp.McpClientWrapper;
import io.modelcontextprotocol.spec.McpSchema;
import jakarta.annotation.Resource;
import java.util.List;
import java.util.concurrent.Executor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 监听沙箱部署事件，在事务提交后执行 K8s CRD 部署。
 *
 * <p>确保只有 DB 记录成功写入后才部署 K8s 资源，避免事务回滚导致的资源泄漏。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class McpSandboxDeployListener {

    private final McpSandboxDeployService mcpSandboxDeployService;
    private final McpServerEndpointRepository endpointRepository;
    private final McpServerMetaRepository metaRepository;
    private final ToolManager toolManager;
    private final ObjectMapper objectMapper;

    @Resource(name = "taskExecutor")
    private Executor taskExecutor;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSandboxDeploy(McpSandboxDeployEvent event) {
        taskExecutor.execute(() -> doDeployAsync(event));
    }

    private void doDeployAsync(McpSandboxDeployEvent event) {
        String endpointUrl = null;
        try {
            // Step 1: 部署 CRD 到沙箱
            endpointUrl =
                    mcpSandboxDeployService.deploy(
                            event.getSandboxId(),
                            event.getMcpServerId(),
                            event.getMcpName(),
                            event.getAdminUserId(),
                            event.getTransportType(),
                            event.getMetaProtocolType(),
                            event.getConnectionConfig(),
                            "",
                            event.getAuthType(),
                            event.getParamValues(),
                            event.getExtraParams(),
                            event.getNamespace(),
                            event.getResourceSpec());

            // SSE 类型需要 /sse 后缀
            String finalEndpointUrl = endpointUrl;
            McpProtocolType proto = McpProtocolType.fromString(event.getTransportType());
            if ((proto == null || proto.isSse())
                    && endpointUrl != null
                    && !endpointUrl.endsWith("/sse")) {
                finalEndpointUrl = endpointUrl + "/sse";
            }

            // Step 2: 更新 endpoint URL（事务内已预创建了 endpoint 记录）
            String lambdaUrl = finalEndpointUrl;
            endpointRepository
                    .findByEndpointId(event.getEndpointId())
                    .ifPresent(
                            ep -> {
                                ep.setEndpointUrl(lambdaUrl);
                                ep.setStatus(McpEndpointStatus.ACTIVE.name());
                                endpointRepository.save(ep);
                            });

            log.info(
                    "沙箱 CRD 部署成功: mcpName={}, sandboxId={}, endpoint={}",
                    event.getMcpName(),
                    event.getSandboxId(),
                    finalEndpointUrl);

            // Step 3: 异步获取工具列表
            fetchToolsAsync(
                    event.getMcpServerId(),
                    event.getMcpName(),
                    finalEndpointUrl,
                    event.getTransportType());

        } catch (Exception e) {
            log.error(
                    "沙箱 CRD 部署失败: mcpName={}, sandboxId={}",
                    event.getMcpName(),
                    event.getSandboxId(),
                    e);

            // 回滚：标记 endpoint 为 INACTIVE
            endpointRepository
                    .findByEndpointId(event.getEndpointId())
                    .ifPresent(
                            ep -> {
                                ep.setStatus(McpEndpointStatus.INACTIVE.name());
                                endpointRepository.save(ep);
                            });

            // 回滚：删除已部署的 CRD（如果部署成功了的话）
            if (endpointUrl != null) {
                try {
                    mcpSandboxDeployService.undeploy(
                            event.getSandboxId(),
                            event.getMcpName(),
                            event.getAdminUserId(),
                            StrUtil.blankToDefault(event.getNamespace(), "default"));
                } catch (Exception re) {
                    log.warn("回滚删除 CRD 失败: {}", re.getMessage());
                }
            }
        }
    }

    private void fetchToolsAsync(
            String mcpServerId, String mcpName, String endpointUrl, String transportType) {
        try {
            MCPTransportMode mode = McpProtocolType.resolveTransportMode(transportType);
            MCPTransportConfig config =
                    MCPTransportConfig.builder()
                            .mcpServerName(mcpName)
                            .transportMode(mode)
                            .url(endpointUrl)
                            .build();

            McpClientWrapper client = null;
            for (int i = 0; i < 3; i++) {
                client = toolManager.createClient(config);
                if (client != null) break;
                log.info("MCP 客户端创建失败，等待重试 ({}/3): mcpName={}", i + 1, mcpName);
                Thread.sleep(10000);
            }
            if (client == null) {
                log.warn("创建 MCP 客户端失败（已重试 3 次）: mcpName={}", mcpName);
                return;
            }

            List<McpSchema.Tool> tools = client.listTools().block();
            if (tools != null && !tools.isEmpty()) {
                McpServerMeta meta = metaRepository.findByMcpServerId(mcpServerId).orElse(null);
                if (meta != null) {
                    meta.setToolsConfig(objectMapper.writeValueAsString(tools));
                    metaRepository.save(meta);
                    log.info("自动查询工具列表成功: mcpName={}, toolCount={}", mcpName, tools.size());
                }
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            log.warn("获取工具列表被中断: mcpName={}", mcpName);
        } catch (Exception e) {
            log.warn("沙箱部署后获取工具列表失败（不影响部署）: mcpName={}, error={}", mcpName, e.getMessage());
        }
    }

    /** 监听 {@link McpSandboxUndeployEvent}，事务提交后异步清理旧沙箱 CRD。 */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSandboxUndeploy(McpSandboxUndeployEvent event) {
        taskExecutor.execute(
                () -> {
                    log.info(
                            "事务已提交，开始异步清理旧沙箱 CRD: mcpName={}, sandboxId={}",
                            event.getMcpName(),
                            event.getSandboxId());
                    try {
                        mcpSandboxDeployService.undeploy(
                                event.getSandboxId(),
                                event.getMcpName(),
                                event.getUserId(),
                                StrUtil.blankToDefault(event.getNamespace(), "default"));
                        log.info(
                                "旧沙箱 CRD 清理成功: mcpName={}, sandboxId={}",
                                event.getMcpName(),
                                event.getSandboxId());
                    } catch (Exception e) {
                        log.warn(
                                "旧沙箱 CRD 清理失败（不影响新部署）: mcpName={}, sandboxId={}, error={}",
                                event.getMcpName(),
                                event.getSandboxId(),
                                e.getMessage(),
                                e);
                    }
                });
    }
}
