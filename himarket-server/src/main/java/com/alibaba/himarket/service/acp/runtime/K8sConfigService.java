package com.alibaba.himarket.service.acp.runtime;

import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.fabric8.kubernetes.client.KubernetesClientException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * K8s 集群配置管理服务。
 *
 * <p>负责 kubeconfig 的注册、验证、存储和检索，支持多集群管理。 使用 Fabric8 KubernetesClient
 * 验证集群可达性，通过内存缓存管理已注册的客户端实例。
 *
 * <p>Requirements: 10.1, 10.2, 10.3, 10.5
 */
@Service
public class K8sConfigService {

    private static final Logger log = LoggerFactory.getLogger(K8sConfigService.class);

    private final Map<String, ManagedCluster> clusterCache = new ConcurrentHashMap<>();

    /**
     * 注册 kubeconfig，验证集群可达性后存入缓存。
     *
     * <p>流程：
     *
     * <ol>
     *   <li>解析 kubeconfig 字符串为 Fabric8 Config
     *   <li>创建 KubernetesClient 实例
     *   <li>通过 namespaces().list() 测试集群连接
     *   <li>生成唯一 configId 并缓存客户端
     * </ol>
     *
     * @param kubeconfig kubeconfig 内容（YAML 格式字符串）
     * @return 生成的配置 ID
     * @throws IllegalArgumentException 如果 kubeconfig 格式无效
     * @throws RuntimeException 如果集群不可达或认证失败
     */
    public String registerConfig(String kubeconfig) {
        if (kubeconfig == null || kubeconfig.isBlank()) {
            throw new IllegalArgumentException("kubeconfig 内容不能为空");
        }

        Config config;
        try {
            config = Config.fromKubeconfig(kubeconfig);
        } catch (Exception e) {
            throw new IllegalArgumentException("kubeconfig 格式无效: " + e.getMessage(), e);
        }

        KubernetesClient client;
        try {
            client = new KubernetesClientBuilder().withConfig(config).build();
        } catch (Exception e) {
            throw new IllegalArgumentException("无法基于 kubeconfig 创建客户端: " + e.getMessage(), e);
        }

        // 测试集群连接
        try {
            client.namespaces().list();
        } catch (KubernetesClientException e) {
            client.close();
            throw new RuntimeException("集群不可达或认证失败: " + e.getMessage(), e);
        } catch (Exception e) {
            client.close();
            throw new RuntimeException("集群连接测试失败: " + e.getMessage(), e);
        }

        String configId = UUID.randomUUID().toString();
        String clusterName = extractClusterName(config);
        String serverUrl = config.getMasterUrl();

        clusterCache.put(
                configId, new ManagedCluster(client, clusterName, serverUrl, Instant.now()));
        log.info("K8s 集群已注册: configId={}, cluster={}, server={}", configId, clusterName, serverUrl);

        return configId;
    }

    /**
     * 获取已注册的 K8s 客户端。
     *
     * @param configId 配置 ID
     * @return KubernetesClient 实例
     * @throws IllegalArgumentException 如果 configId 不存在
     */
    public KubernetesClient getClient(String configId) {
        if (configId == null || configId.isBlank()) {
            throw new IllegalArgumentException("configId 不能为空");
        }
        ManagedCluster managed = clusterCache.get(configId);
        if (managed == null) {
            throw new IllegalArgumentException("K8s 配置不存在: " + configId);
        }
        return managed.client();
    }

    /**
     * 列出所有已注册的集群信息。
     *
     * @return 集群信息列表
     */
    public List<K8sClusterInfo> listClusters() {
        return clusterCache.entrySet().stream()
                .map(
                        entry -> {
                            String configId = entry.getKey();
                            ManagedCluster managed = entry.getValue();
                            boolean connected = checkConnection(managed.client());
                            return new K8sClusterInfo(
                                    configId,
                                    managed.clusterName(),
                                    managed.serverUrl(),
                                    connected,
                                    managed.registeredAt());
                        })
                .toList();
    }

    /**
     * 移除已注册的集群配置并关闭客户端连接。
     *
     * @param configId 配置 ID
     * @throws IllegalArgumentException 如果 configId 不存在
     */
    public void removeConfig(String configId) {
        if (configId == null || configId.isBlank()) {
            throw new IllegalArgumentException("configId 不能为空");
        }
        ManagedCluster removed = clusterCache.remove(configId);
        if (removed == null) {
            throw new IllegalArgumentException("K8s 配置不存在: " + configId);
        }
        try {
            removed.client().close();
        } catch (Exception e) {
            log.warn("关闭 K8s 客户端时出错: configId={}", configId, e);
        }
        log.info("K8s 集群已移除: configId={}, cluster={}", configId, removed.clusterName());
    }

    /**
     * 检查是否有任何已注册的集群（用于 RuntimeSelector 判断 K8s 可用性）。
     *
     * @return 如果至少有一个已注册的集群返回 true
     */
    public boolean hasAnyCluster() {
        return !clusterCache.isEmpty();
    }

    /**
     * 检查集群连接是否正常。
     */
    private boolean checkConnection(KubernetesClient client) {
        try {
            client.namespaces().list();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 从 Fabric8 Config 中提取集群名称。
     */
    private String extractClusterName(Config config) {
        // Fabric8 Config 的 currentContext 中包含集群信息
        if (config.getCurrentContext() != null
                && config.getCurrentContext().getContext() != null
                && config.getCurrentContext().getContext().getCluster() != null) {
            return config.getCurrentContext().getContext().getCluster();
        }
        // 回退：使用 master URL 作为集群名称
        return config.getMasterUrl() != null ? config.getMasterUrl() : "unknown";
    }

    /**
     * 内部记录，封装已注册集群的客户端和元数据。
     */
    private record ManagedCluster(
            KubernetesClient client, String clusterName, String serverUrl, Instant registeredAt) {}
}
