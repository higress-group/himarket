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

package com.alibaba.himarket.service.sandbox;

import com.alibaba.himarket.core.utils.K8sClientUtils;
import com.alibaba.himarket.entity.SandboxInstance;
import com.alibaba.himarket.repository.SandboxInstanceRepository;
import io.fabric8.kubernetes.client.KubernetesClient;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled sandbox cluster health check task.
 *
 * <p>Every 10 minutes, checks K8s connectivity for all sandbox instances and updates their status.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class SandboxHealthCheckTask {

    private final SandboxInstanceRepository sandboxInstanceRepository;

    @Scheduled(fixedDelay = 10 * 60 * 1000, initialDelay = 30 * 1000)
    public void checkAll() {
        List<SandboxInstance> sandboxes = sandboxInstanceRepository.findAll();
        if (sandboxes.isEmpty()) {
            return;
        }
        log.info("Checking sandbox instances, count={}", sandboxes.size());
        for (SandboxInstance sandbox : sandboxes) {
            checkOne(sandbox);
        }
        log.info("Completed sandbox health check, count={}", sandboxes.size());
    }

    /**
     * Checks one sandbox instance for cluster connectivity and updates its status.
     */
    public void checkOne(SandboxInstance sandbox) {
        String kubeConfig = sandbox.getKubeConfig();
        if (kubeConfig == null || kubeConfig.isBlank()) {
            updateStatus(sandbox, "ERROR", "KubeConfig is empty");
            return;
        }
        try {
            KubernetesClient client = K8sClientUtils.getClient(kubeConfig);
            // List namespaces to verify connectivity.
            client.namespaces().list();

            updateStatus(sandbox, "RUNNING", null);
        } catch (Exception e) {
            String msg = e.getMessage();
            if (msg == null || msg.isBlank()) {
                msg = e.getClass().getSimpleName();
            }
            // Keep the status message short enough for storage and display.
            if (msg.length() > 500) {
                msg = msg.substring(0, 500);
            }
            // Evict failed clients so the next check creates a fresh connection.
            K8sClientUtils.evictClient(kubeConfig);
            updateStatus(sandbox, "ERROR", msg);
        }
    }

    private void updateStatus(SandboxInstance sandbox, String status, String message) {
        String oldStatus = sandbox.getStatus();
        sandbox.setStatus(status);
        sandbox.setStatusMessage(message);
        sandbox.setLastCheckedAt(LocalDateTime.now());
        sandboxInstanceRepository.save(sandbox);
        if (!status.equals(oldStatus)) {
            log.warn(
                    "Sandbox health status changed, sandboxName={}, oldStatus={}, newStatus={},"
                            + " message={}",
                    sandbox.getSandboxName(),
                    oldStatus,
                    status,
                    message);
        }
    }
}
