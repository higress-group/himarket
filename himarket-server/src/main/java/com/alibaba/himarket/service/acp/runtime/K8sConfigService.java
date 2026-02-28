package com.alibaba.himarket.service.acp.runtime;

import com.alibaba.himarket.entity.K8sCluster;
import com.alibaba.himarket.repository.K8sClusterRepository;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.fabric8.kubernetes.client.KubernetesClientException;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * K8s 集群配置管理服务。
 *
 * <p>负责 kubeconfig 的注册、验证、存储和检索，支持多集群管理。
 * 使用数据库持久化集群配置，内存缓存 KubernetesClient 实例。
 */
@Service
public class K8sConfigService {

    private static final Logger log = LoggerFactory.getLogger(K8sConfigService.class);

    private final K8sClusterRepository k8sClusterRepository;

    /**
     * 内存缓存：configId -> KubernetesClient
     * 仅缓存客户端实例，配置数据持久化到数据库
     */
    private final Map<String, KubernetesClient> clientCache = new ConcurrentHashMap<>();

    public K8sConfigService(K8sClusterRepository k8sClusterRepository) {
        this.k8sClusterRepository = k8sClusterRepository;
    }

    /**
     * 应用启动时，从数据库加载已有配置并初始化客户端缓存。
     * 如果数据库表尚未创建（Flyway 迁移未完成），则跳过初始化，不阻塞启动。
     */
    @PostConstruct
    public void init() {
        log.info("初始化 K8s 集群客户端缓存...");
        List<K8sCluster> clusters;
        try {
            clusters = k8sClusterRepository.findAll();
        } catch (Exception e) {
            log.warn("K8s 集群表尚不可用，跳过客户端缓存初始化: {}", e.getMessage());
            return;
        }
        for (K8sCluster cluster : clusters) {
            try {
                KubernetesClient client = createClient(cluster.getKubeconfig());
                clientCache.put(cluster.getConfigId(), client);
                log.info(
                        "已加载 K8s 集群: configId={}, cluster={}",
                        cluster.getConfigId(),
                        cluster.getClusterName());
            } catch (Exception e) {
                log.warn(
                        "加载 K8s 集群失败: configId={}, error={}",
                        cluster.getConfigId(),
                        e.getMessage());
            }
        }
        log.info("K8s 集群客户端缓存初始化完成，共加载 {} 个集群", clientCache.size());
    }

    /**
     * 应用关闭时，关闭所有客户端连接。
     */
    @PreDestroy
    public void destroy() {
        log.info("关闭所有 K8s 客户端连接...");
        clientCache
                .values()
                .forEach(
                        client -> {
                            try {
                                client.close();
                            } catch (Exception e) {
                                log.warn("关闭 K8s 客户端时出错: {}", e.getMessage());
                            }
                        });
        clientCache.clear();
    }

    /**
     * 注册 kubeconfig，验证集群可达性后存入数据库。
     *
     * @param kubeconfig kubeconfig 内容（YAML 格式字符串）
     * @return 生成的配置 ID
     * @throws IllegalArgumentException 如果 kubeconfig 格式无效
     * @throws RuntimeException 如果集群不可达或认证失败
     */
    @Transactional
    public String registerConfig(String kubeconfig) {
        return registerConfig(kubeconfig, null);
    }

    /**
     * 注册 kubeconfig，验证集群可达性后存入数据库。
     *
     * @param kubeconfig kubeconfig 内容（YAML 格式字符串）
     * @param description 集群描述（可选）
     * @return 生成的配置 ID
     * @throws IllegalArgumentException 如果 kubeconfig 格式无效
     * @throws RuntimeException 如果集群不可达或认证失败
     */
    @Transactional
    public String registerConfig(String kubeconfig, String description) {
        if (kubeconfig == null || kubeconfig.isBlank()) {
            throw new IllegalArgumentException("kubeconfig 内容不能为空");
        }

        Config config = parseKubeconfig(kubeconfig);
        KubernetesClient client = createAndValidateClient(config);

        String configId = UUID.randomUUID().toString();
        String clusterName = extractClusterName(config);
        String serverUrl = config.getMasterUrl();

        // 持久化到数据库
        K8sCluster cluster =
                K8sCluster.builder()
                        .configId(configId)
                        .clusterName(clusterName)
                        .serverUrl(serverUrl)
                        .kubeconfig(kubeconfig)
                        .description(description)
                        .build();
        k8sClusterRepository.save(cluster);

        // 缓存客户端
        clientCache.put(configId, client);

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

        // 先从缓存获取
        KubernetesClient client = clientCache.get(configId);
        if (client != null) {
            return client;
        }

        // 缓存未命中，从数据库加载
        K8sCluster cluster =
                k8sClusterRepository
                        .findByConfigId(configId)
                        .orElseThrow(() -> new IllegalArgumentException("K8s 配置不存在: " + configId));

        client = createClient(cluster.getKubeconfig());
        clientCache.put(configId, client);
        return client;
    }

    /**
     * 获取默认的 K8s 客户端（POC 阶段使用第一个已注册的集群）。
     * <p>
     * 优先从内存缓存获取，避免每次查数据库。
     * 适用于不关心具体集群的场景（如 Pod 清理、健康检查等）。
     *
     * @return KubernetesClient 实例
     * @throws IllegalStateException 如果没有已注册的集群
     */
    public KubernetesClient getDefaultClient() {
        // 优先从缓存取第一个
        Map.Entry<String, KubernetesClient> first =
                clientCache.entrySet().stream().findFirst().orElse(null);
        if (first != null) {
            return first.getValue();
        }

        // 缓存为空，尝试从数据库加载
        List<K8sCluster> clusters = k8sClusterRepository.findAll();
        if (clusters.isEmpty()) {
            throw new IllegalStateException("没有已注册的 K8s 集群");
        }

        K8sCluster cluster = clusters.get(0);
        KubernetesClient client = createClient(cluster.getKubeconfig());
        clientCache.put(cluster.getConfigId(), client);
        return client;
    }

    /**
     * 获取默认集群的 configId（POC 阶段使用第一个已注册的集群）。
     * <p>
     * 优先从内存缓存获取，避免每次查数据库。
     *
     * @return 默认集群的 configId
     * @throws IllegalStateException 如果没有已注册的集群
     */
    public String getDefaultConfigId() {
        // 优先从缓存取第一个
        String firstKey = clientCache.keySet().stream().findFirst().orElse(null);
        if (firstKey != null) {
            return firstKey;
        }

        // 缓存为空，尝试从数据库加载
        List<K8sCluster> clusters = k8sClusterRepository.findAll();
        if (clusters.isEmpty()) {
            throw new IllegalStateException("没有已注册的 K8s 集群");
        }

        K8sCluster cluster = clusters.get(0);
        KubernetesClient client = createClient(cluster.getKubeconfig());
        clientCache.put(cluster.getConfigId(), client);
        return cluster.getConfigId();
    }

    /**
     * 列出所有已注册的集群信息。
     *
     * @return 集群信息列表
     */
    public List<K8sClusterInfo> listClusters() {
        return k8sClusterRepository.findAll().stream()
                .map(
                        cluster -> {
                            boolean connected = checkConnection(cluster.getConfigId());
                            return new K8sClusterInfo(
                                    cluster.getConfigId(),
                                    cluster.getClusterName(),
                                    cluster.getServerUrl(),
                                    connected,
                                    cluster.getCreateAt() != null
                                            ? cluster.getCreateAt()
                                                    .atZone(java.time.ZoneId.systemDefault())
                                                    .toInstant()
                                            : null);
                        })
                .toList();
    }

    /**
     * 移除已注册的集群配置并关闭客户端连接。
     *
     * @param configId 配置 ID
     * @throws IllegalArgumentException 如果 configId 不存在
     */
    @Transactional
    public void removeConfig(String configId) {
        if (configId == null || configId.isBlank()) {
            throw new IllegalArgumentException("configId 不能为空");
        }

        K8sCluster cluster =
                k8sClusterRepository
                        .findByConfigId(configId)
                        .orElseThrow(() -> new IllegalArgumentException("K8s 配置不存在: " + configId));

        // 关闭并移除缓存的客户端
        KubernetesClient client = clientCache.remove(configId);
        if (client != null) {
            try {
                client.close();
            } catch (Exception e) {
                log.warn("关闭 K8s 客户端时出错: configId={}", configId, e);
            }
        }

        // 从数据库删除
        k8sClusterRepository.delete(cluster);

        log.info("K8s 集群已移除: configId={}, cluster={}", configId, cluster.getClusterName());
    }

    /**
     * 检查是否有任何已注册的集群（用于 RuntimeSelector 判断 K8s 可用性）。
     *
     * @return 如果至少有一个已注册的集群返回 true
     */
    public boolean hasAnyCluster() {
        return k8sClusterRepository.count() > 0;
    }

    /**
     * 解析 kubeconfig 字符串。
     */
    private Config parseKubeconfig(String kubeconfig) {
        try {
            return Config.fromKubeconfig(kubeconfig);
        } catch (Exception e) {
            throw new IllegalArgumentException("kubeconfig 格式无效: " + e.getMessage(), e);
        }
    }

    /**
     * 创建 KubernetesClient 实例。
     */
    private KubernetesClient createClient(String kubeconfig) {
        Config config = parseKubeconfig(kubeconfig);
        try {
            return new KubernetesClientBuilder().withConfig(config).build();
        } catch (Exception e) {
            throw new IllegalArgumentException("无法基于 kubeconfig 创建客户端: " + e.getMessage(), e);
        }
    }

    /**
     * 创建并验证 KubernetesClient。
     */
    private KubernetesClient createAndValidateClient(Config config) {
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

        return client;
    }

    /**
     * 检查集群连接是否正常。
     */
    private boolean checkConnection(String configId) {
        KubernetesClient client = clientCache.get(configId);
        if (client == null) {
            return false;
        }
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
        if (config.getCurrentContext() != null
                && config.getCurrentContext().getContext() != null
                && config.getCurrentContext().getContext().getCluster() != null) {
            return config.getCurrentContext().getContext().getCluster();
        }
        return config.getMasterUrl() != null ? config.getMasterUrl() : "unknown";
    }
}
