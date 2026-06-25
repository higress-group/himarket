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

package com.alibaba.himarket.core.utils;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalCause;
import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import java.time.Duration;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class K8sClientUtils {

    private static final Cache<String, KubernetesClient> CLIENT_CACHE =
            Caffeine.newBuilder()
                    .expireAfterAccess(Duration.ofHours(6))
                    .maximumSize(50)
                    .removalListener(
                            (String key, KubernetesClient client, RemovalCause cause) -> {
                                if (client != null) {
                                    log.info(
                                            "Closing cached KubernetesClient, cacheKeyPrefix={},"
                                                    + " cause={}",
                                            key != null ? key.substring(0, 12) : "null",
                                            cause);
                                    client.close();
                                }
                            })
                    .build();

    @Value("${sandbox.ssl-verify:false}")
    private boolean sslVerify;

    private boolean shouldTrustCerts() {
        return !sslVerify;
    }

    /**
     * Gets a KubernetesClient from KubeConfig with a Caffeine cache.
     *
     * <p>The cache expires after six hours without access. If a cached client fails connectivity
     * verification, it is evicted and rebuilt automatically.
     */
    public KubernetesClient getClient(String kubeConfig) {
        String cacheKey = HashUtils.sha256Hex(kubeConfig);
        KubernetesClient client =
                CLIENT_CACHE.get(
                        cacheKey,
                        key -> {
                            log.info(
                                    "Creating KubernetesClient, cacheKeyPrefix={}",
                                    key.substring(0, 12));
                            Config config = Config.fromKubeconfig(kubeConfig);
                            config.setTrustCerts(shouldTrustCerts());
                            return new KubernetesClientBuilder().withConfig(config).build();
                        });
        // Verify connectivity; evict and recreate on failure (e.g. expired OIDC token)
        try {
            client.getApiVersion();
        } catch (Exception e) {
            log.warn(
                    "Rebuilding KubernetesClient after connectivity check failed,"
                            + " cacheKeyPrefix={}, errorType={}, errorMessage={}",
                    cacheKey.substring(0, 12),
                    e.getClass().getSimpleName(),
                    e.getMessage());
            CLIENT_CACHE.invalidate(cacheKey);
            return CLIENT_CACHE.get(
                    cacheKey,
                    key -> {
                        Config config = Config.fromKubeconfig(kubeConfig);
                        config.setTrustCerts(shouldTrustCerts());
                        return new KubernetesClientBuilder().withConfig(config).build();
                    });
        }
        return client;
    }

    /**
     * Evicts a cached client when KubeConfig changes or an instance is deleted.
     */
    public void evictClient(String kubeConfig) {
        String cacheKey = HashUtils.sha256Hex(kubeConfig);
        CLIENT_CACHE.invalidate(cacheKey);
    }

    /**
     * Gets the cluster ID from the kube-system namespace UID.
     */
    public static String getClusterId(KubernetesClient client) {
        Namespace kubeSystem = client.namespaces().withName("kube-system").get();
        if (kubeSystem != null && kubeSystem.getMetadata() != null) {
            return kubeSystem.getMetadata().getUid();
        }
        return null;
    }

    /**
     * Gets the cluster name from the cluster value in the current KubeConfig context.
     */
    public static String getClusterName(KubernetesClient client) {
        Config config = client.getConfiguration();
        if (config.getCurrentContext() != null && config.getCurrentContext().getContext() != null) {
            return config.getCurrentContext().getContext().getCluster();
        }
        return null;
    }

    /**
     * Gets the apiServer address.
     */
    public static String getApiServer(KubernetesClient client) {
        return client.getConfiguration().getMasterUrl();
    }

    /**
     * Lists all namespace names.
     */
    public static List<String> listNamespaces(KubernetesClient client) {
        return client.namespaces().list().getItems().stream()
                .map(ns -> ns.getMetadata().getName())
                .toList();
    }
}
