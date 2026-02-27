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
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
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

    static final String DEFAULT_SANDBOX_IMAGE =
            "opensource-registry.cn-hangzhou.cr.aliyuncs.com/higress-group/sandbox:latest";
    static final String IMAGE_PULL_SECRET = "";
    static final int SIDECAR_PORT = 8080;
    static final String LABEL_APP = "sandbox";
    static final Duration DEFAULT_POD_READY_TIMEOUT = Duration.ofSeconds(60);
    static final long DEFAULT_HEALTH_CHECK_INTERVAL_SECONDS = 30;
    static final long DEFAULT_IDLE_TIMEOUT_SECONDS = 600;
    static final int DEFAULT_HEALTH_CHECK_FAILURE_THRESHOLD = 3;
    static final String WORKSPACE_STORAGE_CLASS = "alicloud-disk-efficiency";
    static final String WORKSPACE_STORAGE_SIZE = "20Gi";

    /** WebSocket ping 间隔（秒），需小于 SLB 空闲超时（通常 15s） */
    static final long WS_PING_INTERVAL_SECONDS = 10;

    private final KubernetesClient k8sClient;
    private final String namespace;
    private final Duration podReadyTimeout;
    private final long healthCheckIntervalSeconds;
    private final long idleTimeoutSeconds;
    private final int healthCheckFailureThreshold;

    // autoCancel=false: 防止 CliReadyPhase.blockFirst() 取消订阅后 sink 自动 complete，
    // 导致后续 initSandboxAsync 中的 stdout 订阅立即收到 onComplete 而关闭 WebSocket
    private final Sinks.Many<String> stdoutSink =
            Sinks.many().multicast().onBackpressureBuffer(256, false);
    private final Sinks.Many<String> wsSendSink = Sinks.many().unicast().onBackpressureBuffer();
    private volatile RuntimeStatus status = RuntimeStatus.CREATING;
    private String podName;
    private String podIp;
    private URI sidecarWsUri;
    private final ScheduledExecutorService healthChecker;
    private ScheduledFuture<?> healthCheckFuture;
    private ScheduledFuture<?> idleCheckFuture;
    private SidecarFileSystemAdapter fileSystem;
    private Disposable wsConnection;
    private ScheduledFuture<?> wsPingFuture;
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

    /** Sidecar 允许启动的 CLI 命令白名单，逗号分隔 */
    private String allowedCommands = "qodercli,qwen,npx,kiro-cli,opencode";

    /**
     * 使用默认配置创建适配器。
     *
     * @param k8sClient Fabric8 KubernetesClient 实例
     * @param namespace Pod 所在的 K8s 命名空间
     */
    public K8sRuntimeAdapter(KubernetesClient k8sClient, String namespace) {
        this(
                k8sClient,
                namespace,
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
        this.namespace = namespace != null ? namespace : "himarket";
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

            // 4. 获取 Pod IP，构建带 CLI 参数的 Sidecar WebSocket URI
            podIp = getPodIp(podName);
            String command = config.getCommand();
            String cliArgs = config.getArgs() != null ? String.join(" ", config.getArgs()) : null;
            sidecarWsUri = buildSidecarWsUri(podIp, command, cliArgs);
            logger.info("Pod ready: name={}, ip={}, sidecarUri={}", podName, podIp, sidecarWsUri);

            // 5. 创建文件系统适配器（提前到 WebSocket 连接之前，以便调用方在连接前注入配置文件）
            fileSystem = new SidecarFileSystemAdapter(podIp);

            // 6. 建立 WebSocket 连接到 Sidecar（Sidecar 收到连接后会立即 spawn CLI 进程）
            connectToSidecarWebSocket(sidecarWsUri);

            // 7. 启动健康检查
            startHealthCheck();

            // 8. 启动空闲超时检查
            startIdleCheck();

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
    public String startWithExistingPod(PodInfo podInfo, RuntimeConfig config) {
        prepareForExistingPod(podInfo, config);
        connectAndStart();
        return podName;
    }

    /**
     * 两阶段启动 - 第一阶段：准备 Pod 信息和文件系统适配器，但不建立 WebSocket 连接。
     * 调用方可以在此阶段通过 getFileSystem() 注入配置文件到 Pod 内部，
     * 然后调用 connectAndStart() 建立 WebSocket 连接触发 CLI 启动。
     */
    public void prepareForExistingPod(PodInfo podInfo, RuntimeConfig config) {
        if (status != RuntimeStatus.CREATING) {
            throw new RuntimeException("Cannot start: current status is " + status);
        }
        if (podInfo == null) {
            throw new IllegalArgumentException("PodInfo must not be null");
        }

        // 1. 设置 podName, podIp
        this.podName = podInfo.podName();
        this.podIp = podInfo.podIp();

        // 2. 使用 buildSidecarWsUri 构建带 CLI 参数的 WebSocket URI
        String command = config.getCommand();
        String args = config.getArgs() != null ? String.join(" ", config.getArgs()) : null;
        String accessIp =
                podInfo.serviceIp() != null && !podInfo.serviceIp().isBlank()
                        ? podInfo.serviceIp()
                        : podInfo.podIp();
        this.sidecarWsUri = buildSidecarWsUri(accessIp, command, args);
        logger.info(
                "Prepared existing pod: name={}, ip={}, sidecarUri={}",
                podName,
                podIp,
                sidecarWsUri);

        // 3. 创建文件系统适配器（调用方可在 connectAndStart 之前通过 fileSystem 注入配置）
        fileSystem = new SidecarFileSystemAdapter(accessIp);

        // 状态保持 CREATING，等待 connectAndStart 完成后变为 RUNNING
    }

    /**
     * 两阶段启动 - 第二阶段：建立 WebSocket 连接到 Sidecar，触发 CLI 进程启动。
     * 必须在 prepareForExistingPod 之后调用。
     */
    public void connectAndStart() {
        if (podName == null || sidecarWsUri == null) {
            throw new RuntimeException("Must call prepareForExistingPod before connectAndStart");
        }

        try {
            // 建立 WebSocket 连接到 Sidecar（Sidecar 收到连接后会立即 spawn CLI 进程）
            connectToSidecarWebSocket(sidecarWsUri);

            // 启动健康检查
            startHealthCheck();

            // 启动空闲超时检查
            startIdleCheck();

            // 初始化最后活跃时间
            lastActiveAt = Instant.now();

            // 状态 → RUNNING
            status = RuntimeStatus.RUNNING;
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
        stopWsPing();

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
        // 统一沙箱镜像：所有 CLI 工具和 Sidecar Server 预装在同一个镜像中
        // Sidecar Server 通过 ALLOWED_COMMANDS 环境变量获取允许启动的 CLI 白名单
        // 具体启动哪个 CLI 由后端连接 Sidecar WebSocket 时通过 URL 参数动态指定
        String sandboxImage =
                config.getContainerImage() != null && !config.getContainerImage().isBlank()
                        ? config.getContainerImage()
                        : DEFAULT_SANDBOX_IMAGE;

        PodBuilder builder =
                new PodBuilder()
                        .withNewMetadata()
                        .withGenerateName("sandbox-" + config.getUserId() + "-")
                        .withNamespace(namespace)
                        .addToLabels("app", LABEL_APP)
                        .addToLabels("userId", config.getUserId())
                        .endMetadata()
                        .withNewSpec()
                        .withRestartPolicy("Never")
                        .addNewContainer()
                        .withName("sandbox")
                        .withImage(sandboxImage)
                        .withImagePullPolicy("Always")
                        .addNewEnv()
                        .withName("ALLOWED_COMMANDS")
                        .withValue(allowedCommands)
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

        // 仅在配置了 imagePullSecret 时添加
        if (IMAGE_PULL_SECRET != null && !IMAGE_PULL_SECRET.isBlank()) {
            builder.editSpec()
                    .addNewImagePullSecret()
                    .withName(IMAGE_PULL_SECRET)
                    .endImagePullSecret()
                    .endSpec();
        }

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

    /**
     * 构建带 CLI 参数的 Sidecar WebSocket URI。
     * <p>
     * 根据 Pod IP、CLI 命令和参数构建形如
     * {@code ws://ip:8080/?command=xxx&args=xxx} 的 URI，
     * 对参数进行 URL 编码以确保特殊字符安全传输。
     *
     * @param ip      Pod IP 地址
     * @param command CLI 命令名
     * @param args    CLI 参数（可选，可为 null 或空）
     * @return 编码后的 WebSocket URI
     */
    URI buildSidecarWsUri(String ip, String command, String args) {
        String uri =
                String.format(
                        "ws://%s:%d/?command=%s",
                        ip, SIDECAR_PORT, URLEncoder.encode(command, StandardCharsets.UTF_8));
        if (args != null && !args.isBlank()) {
            uri += "&args=" + URLEncoder.encode(args, StandardCharsets.UTF_8);
        }
        return URI.create(uri);
    }

    void connectToSidecarWebSocket(URI wsUri) {
        logger.info("Connecting to sidecar WebSocket: {}", wsUri);
        // 配置 Reactor Netty WebSocket 客户端：启用协议层 ping/pong，防止 SLB 空闲断连
        ReactorNettyWebSocketClient wsClient =
                new ReactorNettyWebSocketClient(
                        reactor.netty.http.client.HttpClient.create()
                                .responseTimeout(Duration.ofSeconds(30)));
        wsClient.setHandlePing(true);
        wsClient.setMaxFramePayloadLength(1024 * 1024);
        CountDownLatch connectedLatch = new CountDownLatch(1);

        wsConnection =
                wsClient.execute(
                                wsUri,
                                session -> {
                                    wsSessionRef.set(session);
                                    logger.info(
                                            "[WS-Sidecar] Session established: pod={},"
                                                    + " sessionId={}",
                                            podName,
                                            session.getId());

                                    // 接收来自 Sidecar 的消息，推送到 stdoutSink
                                    Mono<Void> receive =
                                            session.receive()
                                                    .doOnSubscribe(
                                                            sub ->
                                                                    logger.info(
                                                                            "[WS-Sidecar] Receive"
                                                                                    + " stream"
                                                                                    + " subscribed:"
                                                                                    + " pod={}",
                                                                            podName))
                                                    .doOnNext(
                                                            msg -> {
                                                                if (msg.getType()
                                                                        == WebSocketMessage.Type
                                                                                .PONG) {
                                                                    logger.debug(
                                                                            "[WS-Sidecar] Pong"
                                                                                + " received from"
                                                                                + " pod {}",
                                                                            podName);
                                                                    return;
                                                                }
                                                                String text =
                                                                        msg.getPayloadAsText();
                                                                logger.info(
                                                                        "[WS-Sidecar] Received from"
                                                                                + " pod {}: {}",
                                                                        podName,
                                                                        text.length() > 300
                                                                                ? text.substring(
                                                                                                0,
                                                                                                300)
                                                                                        + "..."
                                                                                : text);
                                                                lastActiveAt = Instant.now();
                                                                Sinks.EmitResult emitResult =
                                                                        stdoutSink.tryEmitNext(
                                                                                text);
                                                                if (emitResult.isFailure()) {
                                                                    logger.warn(
                                                                            "[WS-Sidecar]"
                                                                                + " stdoutSink emit"
                                                                                + " failed: {}",
                                                                            emitResult);
                                                                }
                                                            })
                                                    .doOnError(
                                                            err -> {
                                                                logger.warn(
                                                                        "[WS-Sidecar] Receive"
                                                                                + " error from pod"
                                                                                + " {}: {}",
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
                                                                logger.warn(
                                                                        "[WS-Sidecar] Receive"
                                                                            + " stream completed"
                                                                            + " (sidecar closed"
                                                                            + " connection):"
                                                                            + " pod={}",
                                                                        podName);
                                                                status = RuntimeStatus.ERROR;
                                                            })
                                                    .doOnCancel(
                                                            () -> {
                                                                logger.warn(
                                                                        "[WS-Sidecar] Receive"
                                                                            + " stream cancelled"
                                                                            + " for pod {}",
                                                                        podName);
                                                            })
                                                    .then();

                                    // 发送来自 wsSendSink 的消息到 Sidecar
                                    Mono<Void> send =
                                            session.send(
                                                    wsSendSink
                                                            .asFlux()
                                                            .doOnSubscribe(
                                                                    sub ->
                                                                            logger.info(
                                                                                    "[WS-Sidecar]"
                                                                                        + " Send"
                                                                                        + " stream"
                                                                                        + " subscribed:"
                                                                                        + " pod={}",
                                                                                    podName))
                                                            .doOnNext(
                                                                    msg ->
                                                                            logger.info(
                                                                                    "[WS-Sidecar]"
                                                                                        + " Sending"
                                                                                        + " to pod"
                                                                                        + " {}: {}",
                                                                                    podName,
                                                                                    msg.length()
                                                                                                    > 300
                                                                                            ? msg
                                                                                                            .substring(
                                                                                                                    0,
                                                                                                                    300)
                                                                                                    + "..."
                                                                                            : msg))
                                                            .doOnCancel(
                                                                    () ->
                                                                            logger.warn(
                                                                                    "[WS-Sidecar]"
                                                                                        + " Send"
                                                                                        + " stream"
                                                                                        + " cancelled"
                                                                                        + " for pod"
                                                                                        + " {}",
                                                                                    podName))
                                                            .map(session::textMessage));

                                    // 释放 latch 后再返回管道，确保 send/receive 已构建
                                    connectedLatch.countDown();
                                    // 使用 Mono.when 而非 Mono.zip：
                                    // zip 对 Mono<Void> 会在任一方空完成时取消另一方，
                                    // when 则等待两个流都完成，适合长期运行的 send/receive 管道
                                    return Mono.when(receive, send);
                                })
                        .subscribe(
                                unused -> {
                                    logger.info(
                                            "[WS-Sidecar] Connection completed normally: pod={}",
                                            podName);
                                },
                                err -> {
                                    logger.error(
                                            "[WS-Sidecar] Connection failed/terminated: pod={},"
                                                    + " error={}",
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

        // 启动应用层 ping 心跳，防止 SLB 空闲超时断连（阿里云 SLB 默认 15s）
        startWsPing();
    }

    /**
     * 定期向 Sidecar WebSocket 发送 Ping 帧，保持连接活跃。
     * 阿里云 SLB 默认空闲超时 15 秒，此处每 10 秒发送一次 ping。
     */
    private void startWsPing() {
        wsPingFuture =
                healthChecker.scheduleAtFixedRate(
                        () -> {
                            try {
                                var session = wsSessionRef.get();
                                if (session == null || !session.isOpen()) {
                                    logger.debug(
                                            "[WS-Ping] Session closed, skipping ping: pod={}",
                                            podName);
                                    return;
                                }
                                // 发送 WebSocket Ping 帧
                                session.send(
                                                Mono.just(
                                                        session.pingMessage(
                                                                factory ->
                                                                        factory.wrap(
                                                                                "ping"
                                                                                        .getBytes(
                                                                                                StandardCharsets
                                                                                                        .UTF_8)))))
                                        .subscribe(
                                                unused -> {},
                                                err ->
                                                        logger.warn(
                                                                "[WS-Ping] Failed to send ping to"
                                                                        + " pod {}: {}",
                                                                podName,
                                                                err.getMessage()));
                                logger.debug("[WS-Ping] Sent ping to pod {}", podName);
                            } catch (Exception e) {
                                logger.warn(
                                        "[WS-Ping] Error sending ping to pod {}: {}",
                                        podName,
                                        e.getMessage());
                            }
                        },
                        WS_PING_INTERVAL_SECONDS,
                        WS_PING_INTERVAL_SECONDS,
                        TimeUnit.SECONDS);
    }

    private void stopWsPing() {
        if (wsPingFuture != null) {
            wsPingFuture.cancel(false);
            wsPingFuture = null;
        }
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
        stopWsPing();
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

    public String getAllowedCommands() {
        return allowedCommands;
    }

    public void setAllowedCommands(String allowedCommands) {
        this.allowedCommands = allowedCommands;
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
}
