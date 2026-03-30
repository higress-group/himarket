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

import cn.hutool.extra.spring.SpringUtil;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalCause;
import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class K8sClientUtils {

    private static final Cache<String, KubernetesClient> CLIENT_CACHE =
            Caffeine.newBuilder()
                    .expireAfterAccess(Duration.ofHours(6))
                    .maximumSize(50)
                    .removalListener(
                            (String key, KubernetesClient client, RemovalCause cause) -> {
                                if (client != null) {
                                    log.info(
                                            "关闭KubernetesClient缓存, key={}..., 原因={}",
                                            key != null ? key.substring(0, 12) : "null",
                                            cause);
                                    client.close();
                                }
                            })
                    .build();

    private K8sClientUtils() {}

    private static boolean shouldTrustCerts() {
        String sslVerify = SpringUtil.getProperty("sandbox.ssl-verify", "false");
        return !"true".equalsIgnoreCase(sslVerify);
    }

    /**
     * 根据KubeConfig文本获取KubernetesClient（带Caffeine缓存，6小时无访问过期）。
     * 如果缓存的 client 连接失败，自动 evict 并重建。
     */
    public static KubernetesClient getClient(String kubeConfig) {
        String cacheKey = cn.hutool.crypto.digest.DigestUtil.sha256Hex(kubeConfig);
        KubernetesClient client =
                CLIENT_CACHE.get(
                        cacheKey,
                        key -> {
                            log.info("创建新的KubernetesClient, cacheKey={}...", key.substring(0, 12));
                            Config config = Config.fromKubeconfig(kubeConfig);
                            config.setTrustCerts(shouldTrustCerts());
                            return new KubernetesClientBuilder().withConfig(config).build();
                        });
        // Verify connectivity; evict and recreate on failure (e.g. expired OIDC token)
        try {
            client.getApiVersion();
        } catch (Exception e) {
            log.warn(
                    "缓存的KubernetesClient连接失败，重建: cacheKey={}..., error={}",
                    cacheKey.substring(0, 12),
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
     * 移除缓存中的client（kubeConfig变更或实例删除时调用）
     */
    public static void evictClient(String kubeConfig) {
        String cacheKey = cn.hutool.crypto.digest.DigestUtil.sha256Hex(kubeConfig);
        CLIENT_CACHE.invalidate(cacheKey);
    }

    /**
     * 获取集群ID（kube-system namespace的UID）
     */
    public static String getClusterId(KubernetesClient client) {
        Namespace kubeSystem = client.namespaces().withName("kube-system").get();
        if (kubeSystem != null && kubeSystem.getMetadata() != null) {
            return kubeSystem.getMetadata().getUid();
        }
        return null;
    }

    /**
     * 获取集群名称（从KubeConfig的current-context中的cluster名称）
     */
    public static String getClusterName(KubernetesClient client) {
        Config config = client.getConfiguration();
        if (config.getCurrentContext() != null && config.getCurrentContext().getContext() != null) {
            return config.getCurrentContext().getContext().getCluster();
        }
        return null;
    }

    /**
     * 获取apiServer地址
     */
    public static String getApiServer(KubernetesClient client) {
        return client.getConfiguration().getMasterUrl();
    }

    /**
     * 列出所有namespace名称
     */
    public static List<String> listNamespaces(KubernetesClient client) {
        return client.namespaces().list().getItems().stream()
                .map(ns -> ns.getMetadata().getName())
                .collect(Collectors.toList());
    }
}
