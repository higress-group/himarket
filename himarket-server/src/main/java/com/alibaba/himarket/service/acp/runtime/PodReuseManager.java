package com.alibaba.himarket.service.acp.runtime;

import com.alibaba.himarket.config.AcpProperties;
import io.fabric8.kubernetes.api.model.ContainerPortBuilder;
import io.fabric8.kubernetes.api.model.ContainerStateWaiting;
import io.fabric8.kubernetes.api.model.ContainerStatus;
import io.fabric8.kubernetes.api.model.IntOrString;
import io.fabric8.kubernetes.api.model.LoadBalancerIngress;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaimBuilder;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.api.model.PodList;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Pod 复用管理器，负责用户级沙箱 Pod 的缓存查找、健康验证和生命周期管理。
 * <p>
 * 核心流程（acquirePod）：
 * 1. 查本地缓存 → 2. 验证 Pod 健康（Phase=Running）→ 3. K8s API 标签查询回退 → 4. 创建新 Pod
 * <p>
 * 线程安全：使用 ConcurrentHashMap.compute 保证同一 userId 的并发请求不会重复创建 Pod。
 * <p>
 * Requirements: 3.1, 3.2, 3.4, 3.5, 3.6, 4.2, 4.4, 4.5
 */
@Component
public class PodReuseManager {

    private static final Logger log = LoggerFactory.getLogger(PodReuseManager.class);

    static final int SIDECAR_PORT = 8080;
    static final String LABEL_APP = "sandbox";
    static final String LABEL_SANDBOX_MODE = "sandboxMode";
    static final String SANDBOX_MODE_USER = "user";
    static final String DEFAULT_SANDBOX_IMAGE =
            "opensource-registry.cn-hangzhou.cr.aliyuncs.com/higress-group/sandbox:latest";
    static final String IMAGE_PULL_SECRET = "";
    static final Duration POD_READY_TIMEOUT = Duration.ofSeconds(60);

    /** 镜像拉取等可恢复场景下的最大等待时间 */
    static final Duration POD_READY_TIMEOUT_EXTENDED = Duration.ofSeconds(300);

    /** 轮询 Pod 状态的间隔 */
    static final long POD_STATUS_POLL_INTERVAL_MS = 5000;

    /** 明确不可恢复的 Pod waiting reason */
    static final Set<String> TERMINAL_WAITING_REASONS =
            Set.of(
                    "ErrImagePull",
                    "ImagePullBackOff",
                    "InvalidImageName",
                    "CreateContainerConfigError",
                    "CrashLoopBackOff");

    static final String WORKSPACE_STORAGE_CLASS = "alicloud-disk-efficiency";
    static final String WORKSPACE_STORAGE_SIZE = "20Gi";

    /** Pod 名称前缀，格式: sandbox-{userId} */
    static final String POD_NAME_PREFIX = "sandbox-";

    /** Service 名称前缀，格式: sandbox-svc-{userId} */
    static final String SERVICE_NAME_PREFIX = "sandbox-svc-";

    /** 等待 LoadBalancer IP 分配的超时时间 */
    static final Duration SERVICE_LB_TIMEOUT = Duration.ofSeconds(120);

    /** 轮询 Service 状态的间隔 */
    static final long SERVICE_STATUS_POLL_INTERVAL_MS = 3000;

    /** 等待 SLB 后端健康检查通过的超时时间 */
    static final Duration SLB_BACKEND_READY_TIMEOUT = Duration.ofSeconds(60);

    /** 探测 SLB 后端就绪的间隔 */
    static final long SLB_PROBE_INTERVAL_MS = 2000;

    private final ConcurrentHashMap<String, PodEntry> podCache = new ConcurrentHashMap<>();
    private final K8sConfigService k8sConfigService;
    private final String namespace;
    private final boolean sandboxAccessViaService;
    private String allowedCommands = "qodercli,qwen,npx,opencode";

    @org.springframework.beans.factory.annotation.Autowired
    public PodReuseManager(K8sConfigService k8sConfigService, AcpProperties acpProperties) {
        this(
                k8sConfigService,
                acpProperties.getK8s().getNamespace(),
                acpProperties.getK8s().isSandboxAccessViaService());
        this.allowedCommands = acpProperties.getK8s().getAllowedCommands();
    }

    public PodReuseManager(K8sConfigService k8sConfigService) {
        this(k8sConfigService, "default", true);
    }

    public PodReuseManager(
            K8sConfigService k8sConfigService, String namespace, boolean sandboxAccessViaService) {
        this.k8sConfigService = k8sConfigService;
        this.namespace = namespace != null ? namespace : "default";
        this.sandboxAccessViaService = sandboxAccessViaService;
    }

    public String getNamespace() {
        return namespace;
    }

    /**
     * 获取或创建用户级沙箱 Pod。
     * <p>
     * 流程：1. 查缓存 → 2. 验证健康 → 3. K8s API 回退 → 4. 创建新 Pod
     * 线程安全：使用 compute 保证同一 userId 不会并发创建。
     *
     * @param userId 用户 ID
     * @param config 运行时配置
     * @return PodInfo 包含 Pod 名称、IP、WebSocket URI 和是否复用标志
     */
    public PodInfo acquirePod(String userId, RuntimeConfig config) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId 不能为空");
        }
        if (config == null) {
            throw new IllegalArgumentException("RuntimeConfig 不能为空");
        }

        KubernetesClient client = k8sConfigService.getClient(config.getK8sConfigId());
        // 用数组包装以便在 lambda 中传递复用标志
        boolean[] reused = {false};

        PodEntry entry =
                podCache.compute(
                        userId,
                        (key, existing) -> {
                            // 1. 缓存命中 → 验证健康和环境变量
                            if (existing != null) {
                                if (isPodHealthy(client, existing)) {
                                    // 检查环境变量是否匹配
                                    if (config.getEnv() != null && !config.getEnv().isEmpty()) {
                                        if (!isPodEnvMatching(
                                                client, existing.getPodName(), config.getEnv())) {
                                            log.warn(
                                                    "缓存中的 Pod 环境变量不匹配，清理并重建: userId={}, pod={}",
                                                    userId,
                                                    existing.getPodName());
                                            cleanupUnhealthyPod(client, existing);
                                            // 继续创建新 Pod
                                            log.info("环境变量不匹配，创建新 Pod: userId={}", userId);
                                            PodEntry created = createNewPod(client, userId, config);
                                            reused[0] = false;
                                            return created;
                                        }
                                    }

                                    log.info(
                                            "缓存命中且 Pod 健康: userId={}, pod={}",
                                            userId,
                                            existing.getPodName());
                                    reused[0] = true;
                                    // 补创建 Service（如果开启了 Service 访问且 serviceIp 缺失）
                                    if (sandboxAccessViaService
                                            && (existing.getServiceIp() == null
                                                    || existing.getServiceIp().isBlank())) {
                                        String svcIp =
                                                ensureServiceForPod(
                                                        client, existing.getPodName(), userId);
                                        existing.setServiceIp(svcIp);
                                    }
                                    return existing;
                                }
                                // Pod 不健康，清理后继续创建新 Pod
                                log.warn(
                                        "缓存中的 Pod 不健康，清理: userId={}, pod={}",
                                        userId,
                                        existing.getPodName());
                                cleanupUnhealthyPod(client, existing);
                            }

                            // 2. 缓存未命中（或不健康已清理）→ K8s API 回退查询
                            PodEntry found = queryPodFromK8sApi(client, userId);
                            if (found != null) {
                                // 检查环境变量是否匹配
                                if (config.getEnv() != null && !config.getEnv().isEmpty()) {
                                    if (!isPodEnvMatching(
                                            client, found.getPodName(), config.getEnv())) {
                                        log.warn(
                                                "K8s API 查询到的 Pod 环境变量不匹配，清理并重建: userId={}, pod={}",
                                                userId,
                                                found.getPodName());
                                        cleanupUnhealthyPod(client, found);
                                        // 继续创建新 Pod
                                        log.info("环境变量不匹配，创建新 Pod: userId={}", userId);
                                        PodEntry created = createNewPod(client, userId, config);
                                        reused[0] = false;
                                        return created;
                                    }
                                }

                                log.info(
                                        "K8s API 回退查询命中: userId={}, pod={}",
                                        userId,
                                        found.getPodName());
                                reused[0] = true;
                                return found;
                            }

                            // 3. 未找到 → 创建新 Pod
                            log.info("未找到可复用 Pod，创建新 Pod: userId={}", userId);
                            PodEntry created = createNewPod(client, userId, config);
                            reused[0] = false;
                            return created;
                        });

        URI sidecarWsUri =
                URI.create(String.format("ws://%s:%d/", resolveAccessIp(entry), SIDECAR_PORT));
        return new PodInfo(
                entry.getPodName(),
                entry.getPodIp(),
                entry.getServiceIp(),
                sidecarWsUri,
                reused[0]);
    }

    /**
     * 查询指定用户的 Pod 缓存条目（用于测试和监控）。
     * <p>
     * Requirements: 4.3
     */
    public PodEntry getPodEntry(String userId) {
        return podCache.get(userId);
    }

    /**
     * 获取用户的健康 Pod 条目（使用默认 K8s 客户端）。
     * <p>
     * 先查缓存，缓存未命中或 Pod 不健康时通过 K8s API 回退查询。
     * 适用于不持有 KubernetesClient 引用的调用方（如 K8sWorkspaceService）。
     *
     * @param userId 用户 ID
     * @return 健康的 PodEntry，如果找不到则返回 null
     */
    public PodEntry getHealthyPodEntryWithDefaultClient(String userId) {
        return getHealthyPodEntry(userId, k8sConfigService.getDefaultClient());
    }

    /**
     * 获取用户的健康 Pod 条目。
     * <p>
     * 与 {@link #getPodEntry(String)} 不同，此方法会验证缓存中的 Pod 是否仍然健康（Running）。
     * 如果缓存中的 Pod 不健康（如 OOMKilled 后被重建），会通过 K8s API 回退查询找到当前
     * 真正运行的 Pod 并更新缓存。
     *
     * @param userId 用户 ID
     * @param client KubernetesClient 实例
     * @return 健康的 PodEntry，如果找不到则返回 null
     */
    public PodEntry getHealthyPodEntry(String userId, KubernetesClient client) {
        if (userId == null || userId.isBlank()) {
            return null;
        }

        return podCache.compute(
                userId,
                (key, existing) -> {
                    // 1. 缓存命中 → 验证健康
                    if (existing != null) {
                        if (isPodHealthy(client, existing)) {
                            return existing;
                        }
                        // Pod 不健康，通过 K8s API 回退查询找到当前运行的 Pod
                        log.warn(
                                "Terminal 连接发现缓存中的 Pod 不健康: userId={}, pod={}",
                                userId,
                                existing.getPodName());
                    }

                    // 2. 缓存未命中或不健康 → K8s API 回退查询
                    PodEntry found = queryPodFromK8sApi(client, userId);
                    if (found != null) {
                        log.info(
                                "K8s API 回退查询找到健康 Pod: userId={}, pod={}",
                                userId,
                                found.getPodName());
                        return found;
                    }

                    log.warn("未找到健康的 Pod: userId={}", userId);
                    return null;
                });
    }

    /**
     * 强制移除指定用户的 Pod 缓存并删除 K8s Pod。
     * <p>
     * 流程：
     * 1. 从缓存中移除 PodEntry
     * 2. 如果条目存在：取消空闲计时器 → 删除 K8s Pod → 记录日志
     * 3. 如果条目不存在：记录 warn 日志
     * <p>
     * 异常处理：捕获所有异常，记录 error 日志，不向外抛出。
     * <p>
     * Requirements: 4.3
     */
    public void evictPod(String userId) {
        PodEntry entry = podCache.remove(userId);
        if (entry == null) {
            log.warn("evictPod: userId={} 不在缓存中，忽略", userId);
            return;
        }

        try {
            KubernetesClient client = k8sConfigService.getDefaultClient();
            client.pods().inNamespace(namespace).withName(entry.getPodName()).delete();
            deleteServiceForPod(client, entry.getPodName());
            log.info("evictPod: 已删除 Pod: userId={}, pod={}", userId, entry.getPodName());
        } catch (Exception e) {
            log.error(
                    "evictPod: 删除 Pod 失败: userId={}, pod={}, error={}",
                    userId,
                    entry.getPodName(),
                    e.getMessage(),
                    e);
        }
    }

    // ---- 内部方法 ----

    /**
     * 检查 Pod 是否健康（Phase 为 Running）。
     */
    boolean isPodHealthy(KubernetesClient client, PodEntry entry) {
        try {
            Pod pod = client.pods().inNamespace(namespace).withName(entry.getPodName()).get();
            if (pod == null || pod.getStatus() == null) {
                return false;
            }
            return "Running".equals(pod.getStatus().getPhase());
        } catch (Exception e) {
            log.warn("Pod 健康检查异常: pod={}, error={}", entry.getPodName(), e.getMessage());
            return false;
        }
    }

    /**
     * 清理不健康的 Pod（尝试删除 K8s Pod）。
     */
    private void cleanupUnhealthyPod(KubernetesClient client, PodEntry entry) {
        try {
            client.pods().inNamespace(namespace).withName(entry.getPodName()).delete();
            deleteServiceForPod(client, entry.getPodName());
            log.info("已删除不健康 Pod: {}", entry.getPodName());
        } catch (Exception e) {
            log.warn("删除不健康 Pod 失败: pod={}, error={}", entry.getPodName(), e.getMessage());
        }
    }

    /**
     * 检查 Pod 的环境变量是否与期望的环境变量匹配。
     * 只检查期望环境变量中的 key 是否存在于 Pod 中，不检查 Pod 中的额外环境变量。
     *
     * @param client K8s 客户端
     * @param podName Pod 名称
     * @param expectedEnv 期望的环境变量 Map
     * @return true 如果所有期望的环境变量都存在且值匹配，否则返回 false
     */
    private boolean isPodEnvMatching(
            KubernetesClient client, String podName, Map<String, String> expectedEnv) {
        try {
            Pod pod = client.pods().inNamespace(namespace).withName(podName).get();
            if (pod == null
                    || pod.getSpec() == null
                    || pod.getSpec().getContainers() == null
                    || pod.getSpec().getContainers().isEmpty()) {
                log.warn("无法获取 Pod 规格: podName={}", podName);
                return false;
            }

            // 获取第一个容器的环境变量
            var container = pod.getSpec().getContainers().get(0);
            if (container.getEnv() == null) {
                log.warn("Pod 容器没有环境变量: podName={}", podName);
                return expectedEnv.isEmpty();
            }

            // 构建 Pod 当前的环境变量 Map
            Map<String, String> podEnv = new java.util.HashMap<>();
            for (var envVar : container.getEnv()) {
                if (envVar.getName() != null && envVar.getValue() != null) {
                    podEnv.put(envVar.getName(), envVar.getValue());
                }
            }

            // 检查所有期望的环境变量是否存在且值匹配
            for (Map.Entry<String, String> entry : expectedEnv.entrySet()) {
                String key = entry.getKey();
                String expectedValue = entry.getValue();
                String actualValue = podEnv.get(key);

                if (actualValue == null || !actualValue.equals(expectedValue)) {
                    log.info(
                            "环境变量不匹配e={}, key={}, expected={}, actual={}",
                            podName,
                            key,
                            expectedValue != null ? "***" : null,
                            actualValue != null ? "***" : null);
                    return false;
                }
            }

            log.debug("Pod 环境变量匹配: podName={}, checkedKeys={}", podName, expectedEnv.keySet());
            return true;
        } catch (Exception e) {
            log.warn("检查 Pod 环境变量失败: podName={}, error={}", podName, e.getMessage());
            return false;
        }
    }

    /**
     * 通过 K8s API 标签选择器查询用户已有的 Pod。
     * 优先返回 Running 状态的 Pod；如果只有 Pending 状态的 Pod，等待其就绪后返回。
     * 标签：app=sandbox, userId={userId}, sandboxMode=user
     */
    PodEntry queryPodFromK8sApi(KubernetesClient client, String userId) {
        try {
            PodList podList =
                    client.pods()
                            .inNamespace(namespace)
                            .withLabels(
                                    Map.of(
                                            "app",
                                            LABEL_APP,
                                            "userId",
                                            userId,
                                            LABEL_SANDBOX_MODE,
                                            SANDBOX_MODE_USER))
                            .list();

            if (podList == null || podList.getItems() == null) {
                return null;
            }

            // 优先找 Running 状态的 Pod
            PodEntry running =
                    podList.getItems().stream()
                            .filter(
                                    pod ->
                                            pod.getStatus() != null
                                                    && "Running".equals(pod.getStatus().getPhase()))
                            .filter(
                                    pod ->
                                            pod.getStatus().getPodIP() != null
                                                    && !pod.getStatus().getPodIP().isBlank())
                            .findFirst()
                            .map(
                                    pod -> {
                                        String svcIp =
                                                sandboxAccessViaService
                                                        ? ensureServiceForPod(
                                                                client,
                                                                pod.getMetadata().getName(),
                                                                userId)
                                                        : null;
                                        return new PodEntry(
                                                pod.getMetadata().getName(),
                                                pod.getStatus().getPodIP(),
                                                svcIp);
                                    })
                            .orElse(null);

            if (running != null) {
                return running;
            }

            // 没有 Running 的，找 Pending 状态的 Pod（可能正在拉镜像）
            Pod pendingPod =
                    podList.getItems().stream()
                            .filter(
                                    pod ->
                                            pod.getStatus() != null
                                                    && "Pending".equals(pod.getStatus().getPhase()))
                            .findFirst()
                            .orElse(null);

            if (pendingPod != null) {
                String podName = pendingPod.getMetadata().getName();
                log.info("发现 Pending 状态的 Pod，等待其就绪: userId={}, pod={}", userId, podName);
                // 复用两段式等待逻辑
                waitForPodReadyWithExtension(client, podName);
                // 等待成功后获取 IP
                Pod readyPod = client.pods().inNamespace(namespace).withName(podName).get();
                if (readyPod != null
                        && readyPod.getStatus() != null
                        && readyPod.getStatus().getPodIP() != null
                        && !readyPod.getStatus().getPodIP().isBlank()) {
                    // Service IP 不在此处同步获取，由 acquirePod 异步补获取
                    String svcIp =
                            sandboxAccessViaService
                                    ? ensureServiceForPod(client, podName, userId)
                                    : null;
                    return new PodEntry(podName, readyPod.getStatus().getPodIP(), svcIp);
                }
            }

            return null;
        } catch (Exception e) {
            log.warn("K8s API 回退查询失败，将创建新 Pod: userId={}, error={}", userId, e.getMessage());
            return null;
        }
    }

    /**
     * 确保用户的 workspace PVC 存在，不存在则通过 StorageClass 自动创建。
     */
    void ensurePvcExists(KubernetesClient client, String userId) {
        String pvcName = "workspace-" + userId;
        PersistentVolumeClaim existing =
                client.persistentVolumeClaims().inNamespace(namespace).withName(pvcName).get();
        if (existing != null) {
            log.debug("PVC 已存在: {}", pvcName);
            return;
        }

        PersistentVolumeClaim pvc =
                new PersistentVolumeClaimBuilder()
                        .withNewMetadata()
                        .withName(pvcName)
                        .withNamespace(namespace)
                        .addToLabels("app", LABEL_APP)
                        .addToLabels("userId", userId)
                        .endMetadata()
                        .withNewSpec()
                        .addToAccessModes("ReadWriteOnce")
                        .withStorageClassName(WORKSPACE_STORAGE_CLASS)
                        .withNewResources()
                        .addToRequests("storage", new Quantity(WORKSPACE_STORAGE_SIZE))
                        .endResources()
                        .endSpec()
                        .build();

        client.persistentVolumeClaims().inNamespace(namespace).resource(pvc).create();
        log.info(
                "PVC 已创建: namespace={}, name={}, storageClass={}, size={}",
                namespace,
                pvcName,
                WORKSPACE_STORAGE_CLASS,
                WORKSPACE_STORAGE_SIZE);
    }

    /**
     * 创建新的用户级沙箱 Pod。
     * 复用 K8sRuntimeAdapter 中的 Pod 创建逻辑，额外添加 sandboxMode=user 标签。
     */
    PodEntry createNewPod(KubernetesClient client, String userId, RuntimeConfig config) {
        // 确保 workspace PVC 存在
        ensurePvcExists(client, userId);

        String sandboxImage =
                config.getContainerImage() != null && !config.getContainerImage().isBlank()
                        ? config.getContainerImage()
                        : DEFAULT_SANDBOX_IMAGE;

        String cpuLimit = "2";
        String memoryLimit = "4Gi";
        if (config.getResourceLimits() != null) {
            if (config.getResourceLimits().getCpuLimit() != null) {
                cpuLimit = config.getResourceLimits().getCpuLimit();
            }
            if (config.getResourceLimits().getMemoryLimit() != null) {
                memoryLimit = config.getResourceLimits().getMemoryLimit();
            }
        }

        String podName = podNameForUser(userId);

        // 构建环境变量列表
        List<io.fabric8.kubernetes.api.model.EnvVar> envVars = new java.util.ArrayList<>();
        envVars.add(
                new io.fabric8.kubernetes.api.model.EnvVarBuilder()
                        .withName("ALLOWED_COMMANDS")
                        .withValue(allowedCommands)
                        .build());

        // 添加 RuntimeConfig 中的环境变量（如 API keys）
        if (config.getEnv() != null && !config.getEnv().isEmpty()) {
            for (Map.Entry<String, String> entry : config.getEnv().entrySet()) {
                envVars.add(
                        new io.fabric8.kubernetes.api.model.EnvVarBuilder()
                                .withName(entry.getKey())
                                .withValue(entry.getValue())
                                .build());
            }
        }

        PodBuilder podBuilder =
                new PodBuilder()
                        .withNewMetadata()
                        .withName(podName)
                        .withNamespace(namespace)
                        .addToLabels("app", LABEL_APP)
                        .addToLabels("userId", userId)
                        .addToLabels(LABEL_SANDBOX_MODE, SANDBOX_MODE_USER)
                        .endMetadata()
                        .withNewSpec()
                        .withRestartPolicy("Never")
                        .addNewContainer()
                        .withName("sandbox")
                        .withImage(sandboxImage)
                        .withImagePullPolicy("Always")
                        .withEnv(envVars)
                        .addToPorts(
                                new ContainerPortBuilder()
                                        .withContainerPort(SIDECAR_PORT)
                                        .withName("ws")
                                        .build())
                        .withNewResources()
                        .addToLimits("cpu", new Quantity(cpuLimit))
                        .addToLimits("memory", new Quantity(memoryLimit))
                        .addToRequests("cpu", new Quantity("0.5"))
                        .addToRequests("memory", new Quantity("512Mi"))
                        .endResources()
                        .addNewVolumeMount()
                        .withName("workspace")
                        .withMountPath("/workspace")
                        .endVolumeMount()
                        .endContainer()
                        .addNewVolume()
                        .withName("workspace")
                        .withNewPersistentVolumeClaim()
                        .withClaimName("workspace-" + userId)
                        .endPersistentVolumeClaim()
                        .endVolume()
                        .endSpec();

        // 仅在配置了 imagePullSecret 时添加
        if (IMAGE_PULL_SECRET != null && !IMAGE_PULL_SECRET.isBlank()) {
            podBuilder
                    .editSpec()
                    .addNewImagePullSecret()
                    .withName(IMAGE_PULL_SECRET)
                    .endImagePullSecret()
                    .endSpec();
        }

        Pod pod = podBuilder.build();

        // 固定名字可能已存在残留的旧 Pod（如 Failed/Succeeded 状态），先删除再创建
        try {
            Pod existingPod = client.pods().inNamespace(namespace).withName(podName).get();
            if (existingPod != null) {
                String phase =
                        existingPod.getStatus() != null
                                ? existingPod.getStatus().getPhase()
                                : "Unknown";
                log.info("发现同名旧 Pod，先删除: name={}, phase={}", podName, phase);
                client.pods().inNamespace(namespace).withName(podName).delete();
                // 等待旧 Pod 完全删除
                client.pods()
                        .inNamespace(namespace)
                        .withName(podName)
                        .waitUntilCondition(p -> p == null, 30, TimeUnit.SECONDS);
            }
        } catch (Exception e) {
            log.warn("删除同名旧 Pod 时异常（继续创建）: name={}, error={}", podName, e.getMessage());
        }

        Pod created = client.pods().inNamespace(namespace).resource(pod).create();
        log.info("新 Pod 已创建: namespace={}, name={}, userId={}", namespace, podName, userId);

        // 等待 Pod Ready（两段式：先快速等 60s，未就绪则检查状态决定是否延长等待）
        waitForPodReadyWithExtension(client, podName);

        // 获取 Pod IP
        Pod readyPod = client.pods().inNamespace(namespace).withName(podName).get();
        if (readyPod == null
                || readyPod.getStatus() == null
                || readyPod.getStatus().getPodIP() == null
                || readyPod.getStatus().getPodIP().isBlank()) {
            throw new RuntimeException("Pod IP 不可用: " + podName);
        }

        String podIp = readyPod.getStatus().getPodIP();
        log.info("Pod 就绪: name={}, ip={}", podName, podIp);

        // 如果开启了 Service 访问模式，创建 LoadBalancer Service
        String serviceIp = null;
        if (sandboxAccessViaService) {
            serviceIp = createServiceForPod(client, podName, userId);
        }

        return new PodEntry(podName, podIp, serviceIp);
    }

    /**
     * 两段式等待 Pod Ready。
     * <p>
     * 第一段：快速等待 {@link #POD_READY_TIMEOUT}（60s），大多数情况下镜像已缓存，Pod 能快速就绪。
     * 第二段：如果 60s 未就绪，检查 Pod 状态：
     * - 如果处于可恢复的等待状态（Pending/ContainerCreating/拉取镜像中），延长等待至 {@link #POD_READY_TIMEOUT_EXTENDED}（5min）
     * - 如果处于不可恢复的终态（ErrImagePull/ImagePullBackOff/CrashLoopBackOff 等），立即删除并报错
     * <p>
     * 用户级 Pod 是唯一的，删了重建大概率还是同样的问题，所以核心策略是"不删 Pod，等它好"。
     */
    void waitForPodReadyWithExtension(KubernetesClient client, String podName) {
        // 第一段：快速等待 60s
        try {
            client.pods()
                    .inNamespace(namespace)
                    .withName(podName)
                    .waitUntilReady(POD_READY_TIMEOUT.getSeconds(), TimeUnit.SECONDS);
            return; // 60s 内就绪，直接返回
        } catch (Exception e) {
            log.info("Pod '{}' 未在 {}s 内就绪，检查状态决定是否延长等待", podName, POD_READY_TIMEOUT.getSeconds());
        }

        // 第二段：轮询检查状态，决定继续等还是放弃
        long deadline = System.currentTimeMillis() + POD_READY_TIMEOUT_EXTENDED.toMillis();
        while (System.currentTimeMillis() < deadline) {
            Pod pod = client.pods().inNamespace(namespace).withName(podName).get();
            if (pod == null) {
                throw new RuntimeException("Pod 已不存在: " + podName);
            }

            String phase = pod.getStatus() != null ? pod.getStatus().getPhase() : "Unknown";

            // 已经 Running 了
            if ("Running".equals(phase)) {
                log.info("Pod '{}' 延长等待后就绪", podName);
                return;
            }

            // 已经终态（Failed/Succeeded），不可恢复
            if ("Failed".equals(phase) || "Succeeded".equals(phase)) {
                deletePodQuietly(client, podName);
                throw new RuntimeException(String.format("Pod '%s' 进入终态 '%s'，已删除", podName, phase));
            }

            // 检查容器状态中是否有不可恢复的错误
            String terminalReason = getTerminalWaitingReason(pod);
            if (terminalReason != null) {
                deletePodQuietly(client, podName);
                throw new RuntimeException(
                        String.format("Pod '%s' 遇到不可恢复错误: %s，已删除", podName, terminalReason));
            }

            // 还在 Pending/ContainerCreating，继续等
            log.info(
                    "Pod '{}' 仍在启动中 (phase={})，继续等待... 剩余 {}s",
                    podName,
                    phase,
                    (deadline - System.currentTimeMillis()) / 1000);

            try {
                Thread.sleep(POD_STATUS_POLL_INTERVAL_MS);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("等待 Pod 就绪时被中断: " + podName, ie);
            }
        }

        // 5 分钟都没好，但 Pod 还在，不删除它（下次 acquirePod 时 K8s API 回退查询还能找到）
        log.warn(
                "Pod '{}' 在 {}s 内仍未就绪，但保留 Pod 不删除（下次请求可继续等待）",
                podName,
                POD_READY_TIMEOUT_EXTENDED.getSeconds());
        throw new RuntimeException(
                String.format(
                        "Pod '%s' 未在 %ds 内就绪，Pod 已保留",
                        podName, POD_READY_TIMEOUT_EXTENDED.getSeconds()));
    }

    /**
     * 检查 Pod 容器状态中是否存在不可恢复的 waiting reason。
     *
     * @return 不可恢复的 reason 字符串，如果没有则返回 null
     */
    private String getTerminalWaitingReason(Pod pod) {
        if (pod.getStatus() == null || pod.getStatus().getContainerStatuses() == null) {
            return null;
        }
        List<ContainerStatus> statuses = pod.getStatus().getContainerStatuses();
        for (ContainerStatus cs : statuses) {
            if (cs.getState() == null || cs.getState().getWaiting() == null) {
                continue;
            }
            ContainerStateWaiting waiting = cs.getState().getWaiting();
            String reason = waiting.getReason();
            if (reason != null && TERMINAL_WAITING_REASONS.contains(reason)) {
                return reason + ": " + waiting.getMessage();
            }
        }
        return null;
    }

    /**
     * 静默删除 Pod，异常只记日志不抛出。
     */
    private void deletePodQuietly(KubernetesClient client, String podName) {
        try {
            client.pods().inNamespace(namespace).withName(podName).delete();
            log.info("已删除 Pod: {}", podName);
        } catch (Exception e) {
            log.warn("删除 Pod 失败: {}, error={}", podName, e.getMessage());
        }
    }

    /**
     * 取消空闲计时器。
     */
    /**
     * 获取缓存（用于测试）。
     */
    ConcurrentHashMap<String, PodEntry> getPodCache() {
        return podCache;
    }

    /**
     * 是否通过 Service 访问沙箱。
     */
    public boolean isSandboxAccessViaService() {
        return sandboxAccessViaService;
    }

    // ---- Service 相关方法 ----

    /**
     * 确保 Pod 对应的 Service 存在。如果已存在则返回其 IP，否则创建新 Service。
     *
     * @return Service 的 External IP，如果创建失败或超时则返回 null
     */
    String ensureServiceForPod(KubernetesClient client, String podName, String userId) {
        // Service 名字基于 userId，是确定性的，直接按名字查找
        String serviceName = serviceNameForUser(userId);
        try {
            Service svc = client.services().inNamespace(namespace).withName(serviceName).get();
            if (svc != null
                    && svc.getStatus() != null
                    && svc.getStatus().getLoadBalancer() != null
                    && svc.getStatus().getLoadBalancer().getIngress() != null
                    && !svc.getStatus().getLoadBalancer().getIngress().isEmpty()) {
                String ip = svc.getStatus().getLoadBalancer().getIngress().get(0).getIp();
                if (ip != null && !ip.isBlank()) {
                    log.info("复用已有 Service: userId={}, service={}, ip={}", userId, serviceName, ip);
                    return ip;
                }
                String hostname =
                        svc.getStatus().getLoadBalancer().getIngress().get(0).getHostname();
                if (hostname != null && !hostname.isBlank()) {
                    log.info(
                            "复用已有 Service: userId={}, service={}, hostname={}",
                            userId,
                            serviceName,
                            hostname);
                    return hostname;
                }
            }
        } catch (Exception e) {
            log.warn("查找已有 Service 失败: userId={}, error={}", userId, e.getMessage());
        }

        // 不存在或未就绪，创建/更新 Service
        log.info("userId '{}' 未找到可用 Service，创建 LoadBalancer Service", userId);
        return createServiceForPod(client, podName, userId);
    }

    /**
     * 根据开关决定使用 Service IP 还是 Pod IP。
     * 如果开启了 Service 访问且 serviceIp 可用，优先使用 serviceIp；否则回退到 podIp。
     */
    String resolveAccessIp(PodEntry entry) {
        if (sandboxAccessViaService
                && entry.getServiceIp() != null
                && !entry.getServiceIp().isBlank()) {
            return entry.getServiceIp();
        }
        return entry.getPodIp();
    }

    /**
     * 为 Pod 创建 LoadBalancer 类型的 Service，并等待 External IP 分配。
     *
     * @return Service 的 External IP，如果超时未分配则返回 null
     */
    String createServiceForPod(KubernetesClient client, String podName, String userId) {
        String serviceName = serviceNameForUser(userId);

        Service svc =
                new ServiceBuilder()
                        .withNewMetadata()
                        .withName(serviceName)
                        .withNamespace(namespace)
                        .addToLabels("app", LABEL_APP)
                        .addToLabels("userId", userId)
                        .endMetadata()
                        .withNewSpec()
                        .withType("LoadBalancer")
                        .addToSelector("app", LABEL_APP)
                        .addToSelector("userId", userId)
                        .addToSelector(LABEL_SANDBOX_MODE, SANDBOX_MODE_USER)
                        .addNewPort()
                        .withName("ws")
                        .withPort(SIDECAR_PORT)
                        .withTargetPort(new IntOrString(SIDECAR_PORT))
                        .withProtocol("TCP")
                        .endPort()
                        .endSpec()
                        .build();

        client.services().inNamespace(namespace).resource(svc).createOrReplace();
        log.info(
                "Service 已创建/更新: namespace={}, name={}, userId={}", namespace, serviceName, userId);

        // 等待 LoadBalancer IP 分配
        String externalIp = waitForServiceExternalIp(client, serviceName);

        // 等待 SLB 后端健康检查通过，确保流量真正可达 Pod
        if (externalIp != null) {
            waitForSlbBackendReady(externalIp, SIDECAR_PORT);
        }

        return externalIp;
    }

    /**
     * 等待 Service 获取到 LoadBalancer External IP。
     *
     * @return External IP，超时返回 null
     */
    String waitForServiceExternalIp(KubernetesClient client, String serviceName) {
        long deadline = System.currentTimeMillis() + SERVICE_LB_TIMEOUT.toMillis();
        while (System.currentTimeMillis() < deadline) {
            Service svc = client.services().inNamespace(namespace).withName(serviceName).get();
            if (svc != null
                    && svc.getStatus() != null
                    && svc.getStatus().getLoadBalancer() != null) {
                List<LoadBalancerIngress> ingresses =
                        svc.getStatus().getLoadBalancer().getIngress();
                if (ingresses != null && !ingresses.isEmpty()) {
                    String ip = ingresses.get(0).getIp();
                    if (ip != null && !ip.isBlank()) {
                        log.info("Service '{}' 获取到 External IP: {}", serviceName, ip);
                        return ip;
                    }
                    // 某些云厂商返回 hostname 而非 IP
                    String hostname = ingresses.get(0).getHostname();
                    if (hostname != null && !hostname.isBlank()) {
                        log.info("Service '{}' 获取到 External Hostname: {}", serviceName, hostname);
                        return hostname;
                    }
                }
            }

            log.debug(
                    "Service '{}' 等待 External IP 分配... 剩余 {}s",
                    serviceName,
                    (deadline - System.currentTimeMillis()) / 1000);
            try {
                Thread.sleep(SERVICE_STATUS_POLL_INTERVAL_MS);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                log.warn("等待 Service External IP 时被中断: {}", serviceName);
                return null;
            }
        }

        log.warn(
                "Service '{}' 在 {}s 内未获取到 External IP",
                serviceName,
                SERVICE_LB_TIMEOUT.getSeconds());
        return null;
    }

    /**
     * 等待 SLB 后端健康检查通过，确保通过 External IP 能真正到达 Pod 的 sidecar。
     * <p>
     * 背景：SLB 分配 External IP 后，后端健康检查可能尚未通过（通常需要 10~30s），
     * 此时 HTTP 请求会收到 ConnectException 或 SLB 的 502/404。
     * 在此处阻塞等待，避免下游 Pipeline 依赖重试来处理这个确定性的延迟。
     */
    void waitForSlbBackendReady(String host, int port) {
        String url = "http://" + host + ":" + port + "/health";
        HttpClient probeClient =
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(5))
                        .version(HttpClient.Version.HTTP_1_1)
                        .build();

        long deadline = System.currentTimeMillis() + SLB_BACKEND_READY_TIMEOUT.toMillis();
        int attempt = 0;

        while (System.currentTimeMillis() < deadline) {
            attempt++;
            try {
                HttpResponse<String> response =
                        probeClient.send(
                                HttpRequest.newBuilder(URI.create(url))
                                        .GET()
                                        .timeout(Duration.ofSeconds(5))
                                        .build(),
                                HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) {
                    log.info("SLB 后端就绪: host={}, port={}, 探测 {} 次", host, port, attempt);
                    return;
                }
                log.debug(
                        "SLB 后端未就绪 (status={}): host={}, port={}, 第 {} 次探测",
                        response.statusCode(),
                        host,
                        port,
                        attempt);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("SLB 后端就绪探测被中断: host={}", host);
                return;
            } catch (Exception e) {
                log.debug(
                        "SLB 后端探测连接失败: host={}, port={}, 第 {} 次, {}",
                        host,
                        port,
                        attempt,
                        e.getClass().getSimpleName());
            }

            try {
                Thread.sleep(SLB_PROBE_INTERVAL_MS);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                log.warn("SLB 后端就绪探测等待被中断: host={}", host);
                return;
            }
        }

        log.warn(
                "SLB 后端在 {}s 内未就绪: host={}, port={}, 共探测 {} 次",
                SLB_BACKEND_READY_TIMEOUT.getSeconds(),
                host,
                port,
                attempt);
    }

    /**
     * 查询已有 Pod 对应的 Service External IP（基于 userId 确定性名称）。
     *
     * @return External IP，不存在或未就绪返回 null
     */
    String getServiceIpForPod(KubernetesClient client, String podName) {
        // 从 podName 提取 userId（podName 格式: sandbox-{userId}）
        String userId =
                podName.startsWith(POD_NAME_PREFIX)
                        ? podName.substring(POD_NAME_PREFIX.length())
                        : podName;
        String serviceName = serviceNameForUser(userId);
        try {
            Service svc = client.services().inNamespace(namespace).withName(serviceName).get();
            if (svc != null
                    && svc.getStatus() != null
                    && svc.getStatus().getLoadBalancer() != null) {
                List<LoadBalancerIngress> ingresses =
                        svc.getStatus().getLoadBalancer().getIngress();
                if (ingresses != null && !ingresses.isEmpty()) {
                    String ip = ingresses.get(0).getIp();
                    if (ip != null && !ip.isBlank()) {
                        return ip;
                    }
                    String hostname = ingresses.get(0).getHostname();
                    if (hostname != null && !hostname.isBlank()) {
                        return hostname;
                    }
                }
            }
        } catch (Exception e) {
            log.warn("查询 Pod '{}' 对应 Service IP 失败: {}", podName, e.getMessage());
        }
        return null;
    }

    /**
     * 删除用户对应的 Service（静默，异常只记日志）。
     */
    void deleteServiceForPod(KubernetesClient client, String podName) {
        // 从 podName 提取 userId（podName 格式: sandbox-{userId}）
        String userId =
                podName.startsWith(POD_NAME_PREFIX)
                        ? podName.substring(POD_NAME_PREFIX.length())
                        : podName;
        String serviceName = serviceNameForUser(userId);
        try {
            Service existing = client.services().inNamespace(namespace).withName(serviceName).get();
            if (existing != null) {
                client.services().inNamespace(namespace).withName(serviceName).delete();
                log.info("已删除 Service: {}", serviceName);
            }
        } catch (Exception e) {
            log.warn("删除 Service 失败: {}, error={}", serviceName, e.getMessage());
        }
    }

    /**
     * 根据 userId 生成确定性的 Pod 名称。
     */
    static String podNameForUser(String userId) {
        return POD_NAME_PREFIX + userId;
    }

    /**
     * 根据 userId 生成确定性的 Service 名称。
     */
    static String serviceNameForUser(String userId) {
        return SERVICE_NAME_PREFIX + userId;
    }
}
