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
import com.alibaba.himarket.support.common.Strings;
import com.alibaba.himarket.support.enums.McpHostingType;
import com.alibaba.himarket.utils.JsonUtil;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.fabric8.kubernetes.client.KubernetesClient;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
public class SandboxServiceImpl implements SandboxService {

    private final SandboxInstanceRepository sandboxInstanceRepository;
    private final McpServerEndpointRepository mcpServerEndpointRepository;
    private final ContextHolder contextHolder;
    private final com.alibaba.himarket.service.sandbox.SandboxHealthCheckTask healthCheckTask;
    private final K8sClientUtils k8sClientUtils;

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
                .toList();
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

        // Sandbox names are globally unique.
        sandboxInstanceRepository
                .findBySandboxName(param.getSandboxName())
                .ifPresent(
                        existing -> {
                            throw new BusinessException(
                                    ErrorCode.CONFLICT,
                                    "Sandbox name '" + param.getSandboxName() + "' already exists");
                        });

        // Connect with KubeConfig to resolve cluster metadata.
        try {
            KubernetesClient client = k8sClientUtils.getClient(param.getKubeConfig());
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
            throw new BusinessException(
                    ErrorCode.CONFLICT,
                    "Sandbox instance already exists due to a concurrent conflict. Do not import it"
                            + " again");
        } catch (Exception e) {
            throw new BusinessException(
                    ErrorCode.INVALID_PARAMETER,
                    "Failed to connect to K8s cluster: " + e.getMessage());
        }
    }

    @Override
    @Transactional
    public void updateSandbox(String sandboxId, UpdateSandboxParam param) {
        SandboxInstance sandbox = findSandbox(sandboxId);

        // When the name changes, keep the global uniqueness check.
        if (Strings.isNotBlank(param.getSandboxName())
                && !Strings.equals(sandbox.getSandboxName(), param.getSandboxName())) {
            sandboxInstanceRepository
                    .findBySandboxName(param.getSandboxName())
                    .ifPresent(
                            existing -> {
                                throw new BusinessException(
                                        ErrorCode.CONFLICT,
                                        "Sandbox name '"
                                                + param.getSandboxName()
                                                + "' already exists");
                            });
        }

        // Refresh cluster metadata when KubeConfig changes.
        if (Strings.isNotBlank(param.getKubeConfig())) {
            if (Strings.isNotBlank(sandbox.getKubeConfig())) {
                k8sClientUtils.evictClient(sandbox.getKubeConfig());
            }
            try {
                KubernetesClient client = k8sClientUtils.getClient(param.getKubeConfig());
                sandbox.setApiServer(K8sClientUtils.getApiServer(client));
                sandbox.setClusterAttribute(buildClusterAttribute(client));
            } catch (BusinessException e) {
                throw e;
            } catch (Exception e) {
                throw new BusinessException(
                        ErrorCode.INVALID_PARAMETER,
                        "Failed to connect to K8s cluster: " + e.getMessage());
            }
        }

        param.update(sandbox);
        try {
            sandboxInstanceRepository.saveAndFlush(sandbox);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.CONFLICT, "Sandbox update conflict. Try again");
        }
    }

    @Transactional
    @Override
    public void deleteSandbox(String sandboxId) {
        SandboxInstance sandbox = findSandbox(sandboxId);

        var activeEndpoints =
                mcpServerEndpointRepository.findByHostingTypeAndHostingInstanceId(
                        McpHostingType.SANDBOX.name(), sandboxId);
        if (!activeEndpoints.isEmpty()) {
            throw new BusinessException(
                    ErrorCode.CONFLICT,
                    "This sandbox instance is still used by "
                            + activeEndpoints.size()
                            + " MCP deployments. Cancel the related subscriptions or delete the"
                            + " MCP configs before deleting the sandbox");
        }

        // Evict the K8s client cache when the sandbox is removed.
        if (Strings.isNotBlank(sandbox.getKubeConfig())) {
            k8sClientUtils.evictClient(sandbox.getKubeConfig());
        }
        sandboxInstanceRepository.delete(sandbox);
    }

    @Override
    public SandboxResult healthCheck(String sandboxId) {
        SandboxInstance sandbox = findSandbox(sandboxId);
        healthCheckTask.checkOne(sandbox);
        // Reload the latest status after the health check updates the entity.
        sandbox = findSandbox(sandboxId);
        return new SandboxResult().convertFrom(sandbox);
    }

    @Override
    public ClusterInfoResult fetchClusterInfo(String kubeConfig) {
        try {
            KubernetesClient client = k8sClientUtils.getClient(kubeConfig);
            return ClusterInfoResult.builder()
                    .ok(true)
                    .clusterAttribute(buildClusterAttribute(client))
                    .apiServer(K8sClientUtils.getApiServer(client))
                    .namespaces(K8sClientUtils.listNamespaces(client))
                    .build();
        } catch (Exception e) {
            log.error(
                    "Failed to fetch cluster information, errorType={}, errorMessage={}",
                    e.getClass().getSimpleName(),
                    e.getMessage(),
                    e);
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
        if (Strings.isBlank(sandbox.getKubeConfig())) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST, "Sandbox instance has no KubeConfig");
        }
        try {
            KubernetesClient client = k8sClientUtils.getClient(sandbox.getKubeConfig());
            return K8sClientUtils.listNamespaces(client);
        } catch (Exception e) {
            throw new BusinessException(
                    ErrorCode.INTERNAL_ERROR, "Failed to list namespaces: " + e.getMessage());
        }
    }

    @Override
    public int countActiveDeployments(String sandboxId) {
        return mcpServerEndpointRepository
                .findByHostingTypeAndHostingInstanceId(McpHostingType.SANDBOX.name(), sandboxId)
                .size();
    }

    private String buildClusterAttribute(KubernetesClient client) {
        ObjectNode json = JsonUtil.createObjectNode();
        json.put("clusterId", K8sClientUtils.getClusterId(client));
        json.put("clusterName", K8sClientUtils.getClusterName(client));
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

            if (param != null && Strings.isNotBlank(param.getSandboxType())) {
                predicates.add(cb.equal(root.get("sandboxType"), param.getSandboxType()));
            }

            String adminId = contextHolder.getUser();
            if (Strings.isBlank(adminId)) {
                throw new BusinessException(
                        ErrorCode.UNAUTHORIZED, "Current user information is unavailable");
            }
            predicates.add(cb.equal(root.get("adminId"), adminId));

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
