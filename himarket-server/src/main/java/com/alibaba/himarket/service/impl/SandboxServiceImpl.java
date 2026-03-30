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

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.alibaba.himarket.core.constant.Resources;
import com.alibaba.himarket.core.exception.BusinessException;
import com.alibaba.himarket.core.exception.ErrorCode;
import com.alibaba.himarket.core.security.ContextHolder;
import com.alibaba.himarket.core.utils.IdGenerator;
import com.alibaba.himarket.core.utils.K8sClientUtils;
import com.alibaba.himarket.dto.params.sandbox.ImportSandboxParam;
import com.alibaba.himarket.dto.params.sandbox.QuerySandboxParam;
import com.alibaba.himarket.dto.params.sandbox.UpdateSandboxParam;
import com.alibaba.himarket.dto.result.common.PageResult;
import com.alibaba.himarket.dto.result.sandbox.ClusterInfoResult;
import com.alibaba.himarket.dto.result.sandbox.SandboxResult;
import com.alibaba.himarket.dto.result.sandbox.SandboxSimpleResult;
import com.alibaba.himarket.entity.SandboxInstance;
import com.alibaba.himarket.repository.McpServerEndpointRepository;
import com.alibaba.himarket.repository.SandboxInstanceRepository;
import com.alibaba.himarket.service.SandboxService;
import com.alibaba.himarket.support.enums.McpHostingType;
import io.fabric8.kubernetes.client.KubernetesClient;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class SandboxServiceImpl implements SandboxService {

    private final SandboxInstanceRepository sandboxInstanceRepository;
    private final McpServerEndpointRepository mcpServerEndpointRepository;
    private final ContextHolder contextHolder;
    private final com.alibaba.himarket.service.sandbox.SandboxHealthCheckTask healthCheckTask;

    @Override
    public List<SandboxSimpleResult> listMcpCapableSandboxes() {
        return listActiveSandboxes();
    }

    @Override
    public List<SandboxSimpleResult> listActiveSandboxes() {
        return sandboxInstanceRepository.findByStatus("RUNNING").stream()
                .map(
                        s ->
                                SandboxSimpleResult.builder()
                                        .sandboxId(s.getSandboxId())
                                        .sandboxName(s.getSandboxName())
                                        .build())
                .collect(Collectors.toList());
    }

    @Override
    public PageResult<SandboxResult> listSandboxes(QuerySandboxParam param, Pageable pageable) {
        Page<SandboxInstance> sandboxes =
                sandboxInstanceRepository.findAll(buildSandboxSpec(param), pageable);

        return new PageResult<SandboxResult>()
                .convertFrom(sandboxes, sandbox -> new SandboxResult().convertFrom(sandbox));
    }

    @Override
    public SandboxResult getSandbox(String sandboxId) {
        return new SandboxResult().convertFrom(findSandbox(sandboxId));
    }

    @Override
    @Transactional
    public void importSandbox(ImportSandboxParam param) {
        String adminId = contextHolder.getUser();

        // 检查同名实例（全局唯一）
        sandboxInstanceRepository
                .findBySandboxName(param.getSandboxName())
                .ifPresent(
                        existing -> {
                            throw new BusinessException(
                                    ErrorCode.CONFLICT,
                                    StrUtil.format("实例名称'{}'已存在", param.getSandboxName()));
                        });

        // 从KubeConfig连接集群获取信息
        try {
            KubernetesClient client = K8sClientUtils.getClient(param.getKubeConfig());
            String apiServer = K8sClientUtils.getApiServer(client);
            String clusterAttribute = buildClusterAttribute(client);

            SandboxInstance sandbox = param.convertTo();
            sandbox.setSandboxId(IdGenerator.genSandboxId());
            sandbox.setAdminId(adminId);
            sandbox.setApiServer(apiServer);
            sandbox.setClusterAttribute(clusterAttribute);
            sandbox.setStatus("RUNNING");

            sandboxInstanceRepository.save(sandbox);
        } catch (BusinessException e) {
            throw e;
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.CONFLICT, "沙箱实例已存在（并发冲突），请勿重复导入");
        } catch (Exception e) {
            throw new BusinessException(
                    ErrorCode.INVALID_PARAMETER, "无法连接K8s集群: " + e.getMessage());
        }
    }

    @Override
    @Transactional
    public void updateSandbox(String sandboxId, UpdateSandboxParam param) {
        SandboxInstance sandbox = findSandbox(sandboxId);

        // 如果修改了名称，检查是否重复（全局唯一）
        if (StrUtil.isNotBlank(param.getSandboxName())
                && !StrUtil.equals(sandbox.getSandboxName(), param.getSandboxName())) {
            sandboxInstanceRepository
                    .findBySandboxName(param.getSandboxName())
                    .ifPresent(
                            existing -> {
                                throw new BusinessException(
                                        ErrorCode.CONFLICT,
                                        StrUtil.format("实例名称'{}'已存在", param.getSandboxName()));
                            });
        }

        // 如果更新了KubeConfig，重新获取集群信息
        if (StrUtil.isNotBlank(param.getKubeConfig())) {
            if (StrUtil.isNotBlank(sandbox.getKubeConfig())) {
                K8sClientUtils.evictClient(sandbox.getKubeConfig());
            }
            try {
                KubernetesClient client = K8sClientUtils.getClient(param.getKubeConfig());
                sandbox.setApiServer(K8sClientUtils.getApiServer(client));
                sandbox.setClusterAttribute(buildClusterAttribute(client));
            } catch (BusinessException e) {
                throw e;
            } catch (Exception e) {
                throw new BusinessException(
                        ErrorCode.INVALID_PARAMETER, "无法连接K8s集群: " + e.getMessage());
            }
        }

        param.update(sandbox);
        try {
            sandboxInstanceRepository.saveAndFlush(sandbox);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.CONFLICT, "沙箱实例更新冲突，请重试");
        }
    }

    @Transactional
    @Override
    public void deleteSandbox(String sandboxId) {
        SandboxInstance sandbox = findSandbox(sandboxId);

        // 检查是否有 MCP endpoint 正在使用该沙箱
        var activeEndpoints =
                mcpServerEndpointRepository.findByHostingTypeAndHostingInstanceId(
                        McpHostingType.SANDBOX.name(), sandboxId);
        if (!activeEndpoints.isEmpty()) {
            throw new BusinessException(
                    ErrorCode.CONFLICT,
                    StrUtil.format(
                            "该沙箱实例仍有 {} 个 MCP 部署在使用，请先取消相关订阅或删除 MCP 配置后再删除沙箱",
                            activeEndpoints.size()));
        }

        // 清除K8s client缓存
        if (StrUtil.isNotBlank(sandbox.getKubeConfig())) {
            K8sClientUtils.evictClient(sandbox.getKubeConfig());
        }
        sandboxInstanceRepository.delete(sandbox);
    }

    @Override
    public SandboxResult healthCheck(String sandboxId) {
        SandboxInstance sandbox = findSandbox(sandboxId);
        healthCheckTask.checkOne(sandbox);
        // 重新读取更新后的状态
        sandbox = findSandbox(sandboxId);
        return new SandboxResult().convertFrom(sandbox);
    }

    @Override
    public ClusterInfoResult fetchClusterInfo(String kubeConfig) {
        try {
            KubernetesClient client = K8sClientUtils.getClient(kubeConfig);
            return ClusterInfoResult.builder()
                    .ok(true)
                    .clusterAttribute(buildClusterAttribute(client))
                    .apiServer(K8sClientUtils.getApiServer(client))
                    .namespaces(K8sClientUtils.listNamespaces(client))
                    .build();
        } catch (Exception e) {
            log.error("获取集群信息失败", e);
            String errMsg = e.getMessage();
            if (errMsg == null || errMsg.isBlank()) {
                errMsg = e.getClass().getSimpleName();
            }
            return ClusterInfoResult.builder()
                    .ok(false)
                    .message(errMsg)
                    .namespaces(List.of())
                    .build();
        }
    }

    @Override
    public List<String> listNamespaces(String sandboxId) {
        SandboxInstance sandbox = findSandbox(sandboxId);
        if (StrUtil.isBlank(sandbox.getKubeConfig())) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "沙箱实例未配置 KubeConfig");
        }
        try {
            KubernetesClient client = K8sClientUtils.getClient(sandbox.getKubeConfig());
            return K8sClientUtils.listNamespaces(client);
        } catch (Exception e) {
            throw new BusinessException(
                    ErrorCode.INTERNAL_ERROR, "获取 Namespace 列表失败: " + e.getMessage());
        }
    }

    @Override
    public int countActiveDeployments(String sandboxId) {
        return mcpServerEndpointRepository
                .findByHostingTypeAndHostingInstanceId(McpHostingType.SANDBOX.name(), sandboxId)
                .size();
    }

    private String buildClusterAttribute(KubernetesClient client) {
        cn.hutool.json.JSONObject json = JSONUtil.createObj();
        json.set("clusterId", K8sClientUtils.getClusterId(client));
        json.set("clusterName", K8sClientUtils.getClusterName(client));
        return json.toString();
    }

    private SandboxInstance findSandbox(String sandboxId) {
        return sandboxInstanceRepository
                .findBySandboxId(sandboxId)
                .orElseThrow(
                        () ->
                                new BusinessException(
                                        ErrorCode.NOT_FOUND,
                                        Resources.SANDBOX_INSTANCE,
                                        sandboxId));
    }

    private Specification<SandboxInstance> buildSandboxSpec(QuerySandboxParam param) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (param != null && StrUtil.isNotBlank(param.getSandboxType())) {
                predicates.add(cb.equal(root.get("sandboxType"), param.getSandboxType()));
            }

            String adminId = contextHolder.getUser();
            if (StrUtil.isBlank(adminId)) {
                throw new BusinessException(ErrorCode.UNAUTHORIZED, "未获取到当前用户信息");
            }
            predicates.add(cb.equal(root.get("adminId"), adminId));

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
