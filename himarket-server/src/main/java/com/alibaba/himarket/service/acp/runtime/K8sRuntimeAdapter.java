package com.alibaba.himarket.service.acp.runtime;

import io.fabric8.kubernetes.api.model.ContainerPortBuilder;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaimBuilder;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient;
import org.springframework.web.reactive.socket.client.WebSocketClient;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

/**
 * K8s Pod 沙箱运行时适配器。
 * <p>
 * 通过 Fabric8 K8s Client 管理 Pod 生命周期，通过 Pod 内 Sidecar 的
 * WebSocket 端点与 CLI 进程通信。每个实例对应一个独立的 K8s Pod，
 * 提供完整的多租户隔离。
 * <p>
 * Requirements: 3.1, 3.2, 3.3, 3.4, 3.5
 */
public class K8sRuntimeAdapter implements RuntimeAdapter {

    private static final Logger logger = LoggerFactory.getLogger(K8sRuntimeAdapter.class);

    static final String DEFAULT_NAMESPACE = "himarket";
    static final String DEFAULT_SANDBOX_IMAGE =
            "registry.cn-shanghai.aliyuncs.com/daofeng/sandbox:latest";
    static final String IMAGE_PULL_SECRET = "daofeng-acr-secret";
    static final int SIDECAR_PORT = 8080;
    static final String LABEL_APP = "sandbox";
    static final Duration DEFAULT_POD_READY_TIMEOUT = Duration.ofSeconds(60);
    static final long DEFAULT_HEALTH_CHECK_INTERVAL_SECONDS = 30;
    static final long DEFAULT_IDLE_TIMEOUT_SECONDS = 600;
    static final int DEFAULT_HEALTH_CHECK_FAILURE_THRESHOLD = 3;
    static final String WORKSPACE_STORAGE_CLASS = "alicloud-disk-efficiency";
    static final String WORKSPACE_STORAGE_SIZE = "20Gi";

    private final KubernetesClient k8sClient;
    private final String namespace;
    private final Duration podReadyTimeout;
    private final long healthCheckIntervalSeconds;
    private final long idleTimeoutSeconds;
    private final int healthCheckFailureThreshold;

    private final Sinks.Many<String> stdoutSink = Sinks.many().multicast().onBackpressureBuffer();
    private final Sinks.Many<String> wsSendSink = Sinks.many().unicast().onBackpressureBuffer();
    private volatile RuntimeStatus status = RuntimeStatus.CREATING;
    private String podName;
    private String podIp;
    private URI sidecarWsUri;
    private final ScheduledExecutorService healthChecker;
    private ScheduledFuture<?> healthCheckFuture;
    private ScheduledFuture<?> idleCheckFuture;
    private PodFileSystemAdapter fileSystem;
    private Disposable wsConnection;
    private final AtomicReference<org.springframework.web.reactive.socket.WebSocketSession>
            wsSessionRef = new AtomicReference<>();

    /** 最后活跃时间，send/receive 时更新 */
    private volatile Instant lastActiveAt;

    /** 连续健康检查失败次数 */
    private final AtomicInteger consecutiveHealthCheckFailures = new AtomicInteger(0);

    /** 异常通知回调，上层可注册以接收运行时异常通知 */
    private Consumer<RuntimeFaultNotification> faultListener;

    /** 复用模式标志：true 时 close() 只断开 WebSocket，不删除 Pod */
    private boolean reuseMode = false;

    /**
     * 使用默认配置创建适配器。
     *
     * @param k8sClient Fabric8 KubernetesClient 实例
     */
    public K8sRuntimeAdapter(KubernetesClient k8sClient) {
        this(
                k8sClient,
                DEFAULT_NAMESPACE,
                DEFAULT_POD_READY_TIMEOUT,
                DEFAULT_HEALTH_CHECK_INTERVAL_SECONDS,
                DEFAULT_IDLE_TIMEOUT_SECONDS,
                DEFAULT_HEALTH_CHECK_FAILURE_THRESHOLD);
    }

    /**
     * 使用自定义配置创建适配器（向后兼容）。
     *
     * @param k8sClient                  Fabric8 KubernetesClient 实例
     * @param namespace                  Pod 所在的 K8s 命名空间
     * @param podReadyTimeout            等待 Pod Ready 的超时时间
     * @param healthCheckIntervalSeconds 健康检查间隔（秒）
     */
    public K8sRuntimeAdapter(
            KubernetesClient k8sClient,
            String namespace,
            Duration podReadyTimeout,
            long healthCheckIntervalSeconds) {
        this(
                k8sClient,
                namespace,
                podReadyTimeout,
                healthCheckIntervalSeconds,
                DEFAULT_IDLE_TIMEOUT_SECONDS,
                DEFAULT_HEALTH_CHECK_FAILURE_THRESHOLD);
    }

    /**
     * 使用完整自定义配置创建适配器。
     *
     * @param k8sClient                    Fabric8 KubernetesClient 实例
     * @param namespace                    Pod 所在的 K8s 命名空间
     * @param podReadyTimeout              等待 Pod Ready 的超时时间
     * @param healthCheckIntervalSeconds   健康检查间隔（秒）
     * @param idleTimeoutSeconds           空闲超时时间（秒）
     * @param healthCheckFailureThreshold  连续健康检查失败阈值
     */
    public K8sRuntimeAdapter(
            KubernetesClient k8sClient,
            String namespace,
            Duration podReadyTimeout,
            long healthCheckIntervalSeconds,
            long idleTimeoutSeconds,
            int healthCheckFailureThreshold) {
        if (k8sClient == null) {
            throw new IllegalArgumentException("k8sClient must not be null");
        }
        this.k8sClient = k8sClient;
        this.namespace = namespace != null ? namespace : DEFAULT_NAMESPACE;
        this.podReadyTimeout =
                podReadyTimeout != null ? podReadyTimeout : DEFAULT_POD_READY_TIMEOUT;
        this.healthCheckIntervalSeconds =
                healthCheckIntervalSeconds > 0
                        ? healthCheckIntervalSeconds
                        : DEFAULT_HEALTH_CHECK_INTERVAL_SECONDS;
        this.idleTimeoutSeconds =
                idleTimeoutSeconds > 0 ? idleTimeoutSeconds : DEFAULT_IDLE_TIMEOUT_SECONDS;
        this.healthCheckFailureThreshold =
                healthCheckFailureThreshold > 0
                        ? healthCheckFailureThreshold
                        : DEFAULT_HEALTH_CHECK_FAILURE_THRESHOLD;
        this.healthChecker =
                Executors.newSingleThreadScheduledExecutor(
                        r -> {
                            Thread t = new Thread(r, "k8s-health-check");
                            t.setDaemon(true);
                            return t;
                        });
    }

    @Override
    public RuntimeType getType() {
        return RuntimeType.K8S;
    }

    @Override
    public String start(RuntimeConfig config) throws RuntimeException {
        if (status != RuntimeStatus.CREATING) {
            throw new RuntimeException("Cannot start: current status is " + status);
        }
        if (config == null) {
            throw new IllegalArgumentException("RuntimeConfig must not be null");
        }

        try {
            // 0. 确保 workspace PVC 存在
            ensurePvcExists(config.getUserId());

            // 1. 构建 Pod Spec
            Pod pod = buildPodSpec(config);

            // 2. 创建 Pod
            Pod created = k8sClient.pods().inNamespace(namespace).resource(pod).create();
            podName = created.getMetadata().getName();
            logger.info("Pod created: namespace={}, name={}", namespace, podName);

            // 3. 等待 Pod Ready
            waitForPodReady(podName, podReadyTimeout);

            // 4. 获取 Pod IP，构建 Sidecar WebSocket URI
            podIp = getPodIp(podName);
            sidecarWsUri = URI.create(String.format("ws://%s:%d/", podIp, SIDECAR_PORT));
            logger.info("Pod ready: name={}, ip={}, sidecarUri={}", podName, podIp, sidecarWsUri);

            // 5. 建立 WebSocket 连接到 Sidecar
            connectToSidecarWebSocket(sidecarWsUri);

            // 6. 启动健康检查
            startHealthCheck();

            // 7. 启动空闲超时检查
            startIdleCheck();

            // 8. 创建文件系统适配器
            fileSystem = new PodFileSystemAdapter(k8sClient, podName, namespace);

            // 9. 初始化最后活跃时间
            lastActiveAt = Instant.now();

            status = RuntimeStatus.RUNNING;
            return podName;
        } catch (KubernetesClientException e) {
            status = RuntimeStatus.ERROR;
            cleanupPod();
            throw new RuntimeException("Failed to create K8s Pod: " + e.getMessage(), e);
        } catch (Exception e) {
            status = RuntimeStatus.ERROR;
            cleanupPod();
            throw new RuntimeException("Failed to start K8s runtime: " + e.getMessage(), e);
        }
    }

    /**
     * 复用模式下的启动方法，跳过 Pod 创建，直接连接到已有的 Running Pod。
     * <p>
     * 用于用户级沙箱场景：PodReuseManager 已找到可复用的 Pod，
     * 本方法直接建立 WebSocket 连接并启动健康检查。
     *
     * @param podInfo 已有 Pod 的信息（podName、podIp、sidecarWsUri）
     * @return Pod 名称
     * @throws RuntimeException 如果连接失败
     */
    public String startWithExistingPod(PodInfo podInfo) {
        if (status != RuntimeStatus.CREATING) {
            throw new RuntimeException("Cannot start: current status is " + status);
        }
        if (podInfo == null) {
            throw new IllegalArgumentException("PodInfo must not be null");
        }

        try {
            // 1. 设置 podName, podIp, sidecarWsUri（跳过 Pod 创建和等待）
            this.podName = podInfo.podName();
            this.podIp = podInfo.podIp();
            this.sidecarWsUri = podInfo.sidecarWsUri();
            logger.info(
                    "Reusing existing pod: name={}, ip={}, sidecarUri={}",
                    podName,
                    podIp,
                    sidecarWsUri);

            // 2. 建立 WebSocket 连接到 Sidecar
            connectToSidecarWebSocket(sidecarWsUri);

            // 3. 启动健康检查
            startHealthCheck();

            // 4. 启动空闲超时检查
            startIdleCheck();

            // 5. 创建文件系统适配器
            fileSystem = new PodFileSystemAdapter(k8sClient, podName, namespace);

            // 6. 初始化最后活跃时间
            lastActiveAt = Instant.now();

            // 7. 状态 → RUNNING
            status = RuntimeStatus.RUNNING;
            return podName;
        } catch (Exception e) {
            status = RuntimeStatus.ERROR;
            throw new RuntimeException(
                    "Failed to connect to existing pod '" + podName + "': " + e.getMessage(), e);
        }
    }

    @Override
    public void send(String jsonLine) throws IOException {
        ensureRunning();
        // 更新最后活跃时间
        lastActiveAt = Instant.now();
        // 通过 WebSocket 将 JSON-RPC 消息转发给 Pod 内 Sidecar
        logger.trace("Sending to pod {}: {}", podName, jsonLine);
        Sinks.EmitResult result = wsSendSink.tryEmitNext(jsonLine);
        if (result.isFailure()) {
            throw new IOException(
                    "Failed to send message to sidecar WebSocket, emit result: " + result);
        }
    }

    @Override
    public Flux<String> stdout() {
        return stdoutSink.asFlux();
    }

    @Override
    public RuntimeStatus getStatus() {
        if (status == RuntimeStatus.RUNNING && podName != null) {
            try {
                Pod pod = k8sClient.pods().inNamespace(namespace).withName(podName).get();
                if (pod == null || !"Running".equals(pod.getStatus().getPhase())) {
                    status = RuntimeStatus.ERROR;
                }
            } catch (Exception e) {
                logger.warn("Failed to check pod status: {}", e.getMessage());
            }
        }
        return status;
    }

    @Override
    public boolean isAlive() {
        if (status != RuntimeStatus.RUNNING || podName == null) {
            return false;
        }
        try {
            Pod pod = k8sClient.pods().inNamespace(namespace).withName(podName).get();
            return pod != null && "Running".equals(pod.getStatus().getPhase());
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public void close() {
        if (status == RuntimeStatus.STOPPED) {
            return;
        }
        logger.info("Closing K8sRuntimeAdapter: pod={}, reuseMode={}", podName, reuseMode);

        stopHealthCheck();
        stopIdleCheck();

        // 关闭 WebSocket 连接
        wsSendSink.tryEmitComplete();
        if (wsConnection != null) {
            wsConnection.dispose();
            wsConnection = null;
        }
        var wsSession = wsSessionRef.getAndSet(null);
        if (wsSession != null) {
            wsSession.close().subscribe();
        }

        stdoutSink.tryEmitComplete();

        if (!reuseMode) {
            cleanupPod(); // 仅非复用模式删除 Pod
            healthChecker.shutdownNow();
        }

        status = RuntimeStatus.STOPPED;
    }

    @Override
    public FileSystemAdapter getFileSystem() {
        return fileSystem;
    }

    // ===== Package-private/Protected 方法，便于测试 =====

    /**
     * 确保用户的 workspace PVC 存在，不存在则通过 StorageClass 自动创建。
     */
    void ensurePvcExists(String userId) {
        String pvcName = "workspace-" + userId;
        PersistentVolumeClaim existing =
                k8sClient.persistentVolumeClaims().inNamespace(namespace).withName(pvcName).get();
        if (existing != null) {
            logger.debug("PVC already exists: {}", pvcName);
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

        k8sClient.persistentVolumeClaims().inNamespace(namespace).resource(pvc).create();
        logger.info(
                "PVC created: namespace={}, name={}, storageClass={}, size={}",
                namespace,
                pvcName,
                WORKSPACE_STORAGE_CLASS,
                WORKSPACE_STORAGE_SIZE);
    }

    Pod buildPodSpec(RuntimeConfig config) {
        // 统一沙箱镜像：所有 CLI 工具和 ACP Sidecar 预装在同一个镜像中
        // 通过环境变量 CLI_COMMAND / CLI_ARGS 告知 entrypoint 启动哪个 CLI
        String sandboxImage =
                config.getContainerImage() != null && !config.getContainerImage().isBlank()
                        ? config.getContainerImage()
                        : DEFAULT_SANDBOX_IMAGE;

        String cliArgs = config.getArgs() != null ? String.join(" ", config.getArgs()) : "";

        PodBuilder builder =
                new PodBuilder()
                        .withNewMetadata()
                        .withGenerateName("sandbox-" + config.getUserId() + "-")
                        .withNamespace(namespace)
                        .addToLabels("app", LABEL_APP)
                        .addToLabels("userId", config.getUserId())
                        .addToLabels("provider", config.getProviderKey())
                        .endMetadata()
                        .withNewSpec()
                        .withRestartPolicy("Never")
                        .addNewImagePullSecret()
                        .withName(IMAGE_PULL_SECRET)
                        .endImagePullSecret()
                        .addNewContainer()
                        .withName("sandbox")
                        .withImage(sandboxImage)
                        .withImagePullPolicy("Always")
                        .addNewEnv()
                        .withName("CLI_COMMAND")
                        .withValue(config.getCommand())
                        .endEnv()
                        .addNewEnv()
                        .withName("CLI_ARGS")
                        .withValue(cliArgs)
                        .endEnv()
                        .addToPorts(
                                new ContainerPortBuilder()
                                        .withContainerPort(SIDECAR_PORT)
                                        .withName("ws")
                                        .build())
                        .withNewResources()
                        .addToLimits("cpu", new Quantity(getResourceLimit(config, "cpu", "2")))
                        .addToLimits(
                                "memory", new Quantity(getResourceLimit(config, "memory", "4Gi")))
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
                        .withClaimName("workspace-" + config.getUserId())
                        .endPersistentVolumeClaim()
                        .endVolume()
                        .endSpec();

        return builder.build();
    }

    void waitForPodReady(String name, Duration timeout) {
        try {
            k8sClient
                    .pods()
                    .inNamespace(namespace)
                    .withName(name)
                    .waitUntilReady(timeout.getSeconds(), TimeUnit.SECONDS);
            logger.info("Pod is ready: {}", name);
        } catch (Exception e) {
            throw new RuntimeException(
                    String.format(
                            "Pod '%s' did not become ready within %ds: %s",
                            name, timeout.getSeconds(), e.getMessage()),
                    e);
        }
    }

    String getPodIp(String name) {
        Pod pod = k8sClient.pods().inNamespace(namespace).withName(name).get();
        if (pod == null) {
            throw new RuntimeException("Pod not found: " + name);
        }
        String ip = pod.getStatus().getPodIP();
        if (ip == null || ip.isBlank()) {
            throw new RuntimeException("Pod IP not available for: " + name);
        }
        return ip;
    }

    void connectToSidecarWebSocket(URI wsUri) {
        logger.info("Connecting to sidecar WebSocket: {}", wsUri);
        WebSocketClient wsClient = new ReactorNettyWebSocketClient();
        CountDownLatch connectedLatch = new CountDownLatch(1);

        wsConnection =
                wsClient.execute(
                                wsUri,
                                session -> {
                                    wsSessionRef.set(session);
                                    connectedLatch.countDown();

                                    // 接收来自 Sidecar 的消息，推送到 stdoutSink
                                    Mono<Void> receive =
                                            session.receive()
                                                    .map(WebSocketMessage::getPayloadAsText)
                                                    .doOnNext(
                                                            msg -> {
                                                                lastActiveAt = Instant.now();
                                                                stdoutSink.tryEmitNext(msg);
                                                            })
                                                    .doOnError(
                                                            err -> {
                                                                logger.warn(
                                                                        "WebSocket receive error"
                                                                            + " from pod {}: {}",
                                                                        podName,
                                                                        err.getMessage());
                                                                status = RuntimeStatus.ERROR;
                                                                notifyFault(
                                                                        RuntimeFaultNotification
                                                                                .FAULT_CONNECTION_LOST,
                                                                        RuntimeFaultNotification
                                                                                .ACTION_RECONNECT);
                                                            })
                                                    .doOnComplete(
                                                            () -> {
                                                                logger.info(
                                                                        "WebSocket connection"
                                                                            + " closed for pod {}",
                                                                        podName);
                                                            })
                                                    .then();

                                    // 发送来自 wsSendSink 的消息到 Sidecar
                                    Mono<Void> send =
                                            session.send(
                                                    wsSendSink.asFlux().map(session::textMessage));

                                    return Mono.zip(receive, send).then();
                                })
                        .subscribe(
                                unused -> {},
                                err -> {
                                    logger.error(
                                            "WebSocket connection failed for pod {}: {}",
                                            podName,
                                            err.getMessage());
                                    connectedLatch.countDown();
                                });

        // 等待连接建立（最多 10 秒）
        try {
            if (!connectedLatch.await(10, TimeUnit.SECONDS)) {
                throw new RuntimeException(
                        "Timeout waiting for WebSocket connection to sidecar at " + wsUri);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while connecting to sidecar WebSocket", e);
        }

        if (wsSessionRef.get() == null) {
            throw new RuntimeException(
                    "Failed to establish WebSocket connection to sidecar at " + wsUri);
        }
        logger.info("WebSocket connected to sidecar: {}", wsUri);
    }

    // ===== 私有辅助方法 =====

    private void startHealthCheck() {
        healthCheckFuture =
                healthChecker.scheduleAtFixedRate(
                        () -> {
                            try {
                                if (podName == null || status != RuntimeStatus.RUNNING) {
                                    return;
                                }
                                Pod pod =
                                        k8sClient
                                                .pods()
                                                .inNamespace(namespace)
                                                .withName(podName)
                                                .get();
                                if (pod == null || !"Running".equals(pod.getStatus().getPhase())) {
                                    int failures = consecutiveHealthCheckFailures.incrementAndGet();
                                    logger.warn(
                                            "Health check failed for pod {} ({}/{})",
                                            podName,
                                            failures,
                                            healthCheckFailureThreshold);
                                    if (failures >= healthCheckFailureThreshold) {
                                        logger.error(
                                                "Pod {} exceeded health check failure threshold"
                                                        + " ({}/{}), force destroying",
                                                podName,
                                                failures,
                                                healthCheckFailureThreshold);
                                        status = RuntimeStatus.ERROR;
                                        notifyFault(
                                                RuntimeFaultNotification.FAULT_HEALTH_CHECK_FAILURE,
                                                RuntimeFaultNotification.ACTION_RECREATE);
                                        stdoutSink.tryEmitComplete();
                                        forceDestroy();
                                    }
                                } else {
                                    consecutiveHealthCheckFailures.set(0);
                                }
                            } catch (Exception e) {
                                int failures = consecutiveHealthCheckFailures.incrementAndGet();
                                logger.warn(
                                        "Health check error for pod {} ({}/{}): {}",
                                        podName,
                                        failures,
                                        healthCheckFailureThreshold,
                                        e.getMessage());
                                if (failures >= healthCheckFailureThreshold) {
                                    logger.error(
                                            "Pod {} exceeded health check failure threshold"
                                                    + " ({}/{}), force destroying",
                                            podName,
                                            failures,
                                            healthCheckFailureThreshold);
                                    status = RuntimeStatus.ERROR;
                                    notifyFault(
                                            RuntimeFaultNotification.FAULT_HEALTH_CHECK_FAILURE,
                                            RuntimeFaultNotification.ACTION_RECREATE);
                                    stdoutSink.tryEmitComplete();
                                    forceDestroy();
                                }
                            }
                        },
                        healthCheckIntervalSeconds,
                        healthCheckIntervalSeconds,
                        TimeUnit.SECONDS);
    }

    private void stopHealthCheck() {
        if (healthCheckFuture != null) {
            healthCheckFuture.cancel(false);
            healthCheckFuture = null;
        }
    }

    private void startIdleCheck() {
        idleCheckFuture =
                healthChecker.scheduleAtFixedRate(
                        () -> {
                            try {
                                if (status != RuntimeStatus.RUNNING || lastActiveAt == null) {
                                    return;
                                }
                                long idleSeconds =
                                        Duration.between(lastActiveAt, Instant.now()).getSeconds();
                                if (idleSeconds >= idleTimeoutSeconds) {
                                    logger.info(
                                            "Pod {} idle for {}s (threshold: {}s), auto-deleting",
                                            podName,
                                            idleSeconds,
                                            idleTimeoutSeconds);
                                    status = RuntimeStatus.STOPPED;
                                    notifyFault(
                                            RuntimeFaultNotification.FAULT_IDLE_TIMEOUT,
                                            RuntimeFaultNotification.ACTION_RECREATE);
                                    stdoutSink.tryEmitComplete();
                                    forceDestroy();
                                }
                            } catch (Exception e) {
                                logger.warn(
                                        "Idle check error for pod {}: {}", podName, e.getMessage());
                            }
                        },
                        healthCheckIntervalSeconds,
                        healthCheckIntervalSeconds,
                        TimeUnit.SECONDS);
    }

    private void stopIdleCheck() {
        if (idleCheckFuture != null) {
            idleCheckFuture.cancel(false);
            idleCheckFuture = null;
        }
    }

    private void forceDestroy() {
        stopHealthCheck();
        stopIdleCheck();
        cleanupPod();
    }

    private void notifyFault(String faultType, String suggestedAction) {
        if (faultListener != null) {
            try {
                faultListener.accept(
                        new RuntimeFaultNotification(faultType, RuntimeType.K8S, suggestedAction));
            } catch (Exception e) {
                logger.warn("Error notifying fault listener: {}", e.getMessage());
            }
        }
    }

    private void cleanupPod() {
        if (podName != null) {
            try {
                k8sClient.pods().inNamespace(namespace).withName(podName).delete();
                logger.info("Pod deleted: {}", podName);
            } catch (Exception e) {
                logger.warn("Failed to delete pod {}: {}", podName, e.getMessage());
            }
        }
    }

    private void ensureRunning() throws IOException {
        if (status != RuntimeStatus.RUNNING || podName == null) {
            throw new IOException("K8s runtime is not running, current status: " + status);
        }
    }

    private String getResourceLimit(RuntimeConfig config, String resource, String defaultValue) {
        if (config.getResourceLimits() == null) {
            return defaultValue;
        }
        return switch (resource) {
            case "cpu" ->
                    config.getResourceLimits().getCpuLimit() != null
                            ? config.getResourceLimits().getCpuLimit()
                            : defaultValue;
            case "memory" ->
                    config.getResourceLimits().getMemoryLimit() != null
                            ? config.getResourceLimits().getMemoryLimit()
                            : defaultValue;
            default -> defaultValue;
        };
    }

    // ===== 用于测试的 Getter =====

    String getPodName() {
        return podName;
    }

    String getNamespace() {
        return namespace;
    }

    URI getSidecarWsUri() {
        return sidecarWsUri;
    }

    // ===== 公共方法：空闲超时和健康检查支持 =====

    public void touchActivity() {
        lastActiveAt = Instant.now();
    }

    public void setReuseMode(boolean reuseMode) {
        this.reuseMode = reuseMode;
    }

    public boolean isReuseMode() {
        return reuseMode;
    }

    public void setFaultListener(Consumer<RuntimeFaultNotification> listener) {
        this.faultListener = listener;
    }

    Instant getLastActiveAt() {
        return lastActiveAt;
    }

    int getConsecutiveHealthCheckFailures() {
        return consecutiveHealthCheckFailures.get();
    }

    long getIdleTimeoutSeconds() {
        return idleTimeoutSeconds;
    }

    int getHealthCheckFailureThreshold() {
        return healthCheckFailureThreshold;
    }

    void setLastActiveAt(Instant instant) {
        this.lastActiveAt = instant;
    }

    /**
     * @deprecated 使用 {@link RuntimeFaultNotification} 替代
     */
    @Deprecated
    public record RuntimeFaultEvent(
            String faultType, RuntimeType runtimeType, String description) {}
}
