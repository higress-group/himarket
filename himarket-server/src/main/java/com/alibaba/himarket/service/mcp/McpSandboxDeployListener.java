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
import com.alibaba.himarket.repository.McpServerEndpointRepository;
import com.alibaba.himarket.service.McpSandboxDeployService;
import com.alibaba.himarket.support.enums.McpEndpointStatus;
import jakarta.annotation.Resource;
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

            // 解析返回值：提取 endpointUrl 和 secretName
            // deploy() 返回格式：endpointUrl 或 endpointUrl|SECRET:secretName
            String secretName = null;
            endpointUrl = rawResult;
            if (rawResult != null && rawResult.contains("|SECRET:")) {
                int idx = rawResult.indexOf("|SECRET:");
                endpointUrl = rawResult.substring(0, idx);
                secretName = rawResult.substring(idx + 8); // "|SECRET:".length() == 8
            }

            // 标准化 URL：SSE 协议追加 /sse 后缀，去掉尾部多余斜杠
            String finalEndpointUrl =
                    McpProtocolUtils.normalizeEndpointUrl(endpointUrl, event.getTransportType());

            // Step 2: 更新 endpoint URL 和状态，回写 secretName 到 subscribeParams
            String lambdaUrl = finalEndpointUrl;
            String lambdaSecretName = secretName;
            endpointRepository
                    .findByEndpointId(event.getEndpointId())
                    .ifPresent(
                            ep -> {
                                ep.setEndpointUrl(lambdaUrl);
                                ep.setStatus(McpEndpointStatus.ACTIVE.name());
                                // 回写 secretName 到 subscribeParams
                                if (StrUtil.isNotBlank(lambdaSecretName)
                                        && StrUtil.isNotBlank(ep.getSubscribeParams())) {
                                    try {
                                        cn.hutool.json.JSONObject params =
                                                cn.hutool.json.JSONUtil.parseObj(
                                                        ep.getSubscribeParams());
                                        params.set("secretName", lambdaSecretName);
                                        ep.setSubscribeParams(params.toString());
                                    } catch (Exception e) {
                                        log.warn(
                                                "回写 secretName 到 subscribeParams 失败: {}",
                                                e.getMessage());
                                    }
                                }
                                endpointRepository.save(ep);
                            });

            log.info(
                    "沙箱 CRD 部署成功: mcpName={}, sandboxId={}, endpoint={}",
                    event.getMcpName(),
                    event.getSandboxId(),
                    finalEndpointUrl);

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
                    String rollbackResourceName =
                            AgentRuntimeDeployStrategy.buildResourceNameStatic(
                                    event.getMcpName(), event.getAdminUserId());
                    mcpSandboxDeployService.undeploy(
                            event.getSandboxId(),
                            event.getMcpName(),
                            event.getAdminUserId(),
                            StrUtil.blankToDefault(event.getNamespace(), "default"),
                            rollbackResourceName,
                            null); // Secret already cleaned up in deploy() rollback
                } catch (Exception re) {
                    log.warn("回滚删除 CRD 失败: {}", re.getMessage());
                }
            }
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
                                StrUtil.blankToDefault(event.getNamespace(), "default"),
                                event.getResourceName(),
                                event.getSecretName());
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
