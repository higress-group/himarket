package com.alibaba.himarket.service.acp.runtime;

import com.alibaba.himarket.service.acp.AcpProcess;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

/**
 * 本地运行时适配器，封装现有 {@link AcpProcess}，实现 {@link RuntimeAdapter} 接口。
 * <p>
 * 通过 ProcessBuilder 在 Java 服务器本机启动 CLI 子进程，
 * 委托 AcpProcess 完成 start/send/stdout/close 等操作。
 * <p>
 * 支持定期健康检查，检测子进程是否存活，在 5 秒内检测到异常并通知上层。
 * Requirements: 2.1, 2.2, 2.3, 2.4, 8.1, 8.2
 *
 * @deprecated 已被 {@link LocalSandboxProvider} 替代。
 *     本地 CLI 进程现在通过 Sidecar WebSocket 桥接，不再使用 ProcessBuilder 直接启动。
 */
@Deprecated
public class LocalRuntimeAdapter implements RuntimeAdapter {

    private static final Logger logger = LoggerFactory.getLogger(LocalRuntimeAdapter.class);

    /** 默认健康检查间隔（秒），需 ≤5 秒以满足 Req 8.2 的 5 秒检测要求 */
    static final long DEFAULT_HEALTH_CHECK_INTERVAL_SECONDS = 3;

    private AcpProcess process;
    private volatile RuntimeStatus status = RuntimeStatus.CREATING;
    private String cwd;
    private LocalFileSystemAdapter fileSystem;

    private final ScheduledExecutorService healthChecker;
    private ScheduledFuture<?> healthCheckFuture;
    private final long healthCheckIntervalSeconds;

    /** 异常通知回调 */
    private Consumer<RuntimeFaultNotification> faultListener;

    /**
     * 使用默认健康检查间隔创建适配器。
     */
    public LocalRuntimeAdapter() {
        this(DEFAULT_HEALTH_CHECK_INTERVAL_SECONDS);
    }

    /**
     * 使用自定义健康检查间隔创建适配器。
     *
     * @param healthCheckIntervalSeconds 健康检查间隔（秒）
     */
    public LocalRuntimeAdapter(long healthCheckIntervalSeconds) {
        this.healthCheckIntervalSeconds = healthCheckIntervalSeconds;
        this.healthChecker =
                Executors.newSingleThreadScheduledExecutor(
                        r -> {
                            Thread t = new Thread(r, "local-health-checker");
                            t.setDaemon(true);
                            return t;
                        });
    }

    /**
     * 使用自定义健康检查间隔和调度器创建适配器（用于测试）。
     */
    LocalRuntimeAdapter(long healthCheckIntervalSeconds, ScheduledExecutorService healthChecker) {
        this.healthCheckIntervalSeconds = healthCheckIntervalSeconds;
        this.healthChecker = healthChecker;
    }

    @Override
    public SandboxType getType() {
        return SandboxType.LOCAL;
    }

    @Override
    public String start(RuntimeConfig config) throws RuntimeException {
        if (status != RuntimeStatus.CREATING) {
            throw new RuntimeException("Cannot start: current status is " + status);
        }
        try {
            this.cwd = config.getCwd();
            this.fileSystem = new LocalFileSystemAdapter(config.getCwd());

            // Build process env, applying HOME isolation when isolateHome is enabled (Req 2.6).
            // If the caller already set HOME in env, respect it; otherwise use cwd as HOME.
            Map<String, String> processEnv =
                    config.getEnv() != null ? new HashMap<>(config.getEnv()) : new HashMap<>();
            if (config.isIsolateHome() && !processEnv.containsKey("HOME")) {
                processEnv.put("HOME", config.getCwd());
                logger.info(
                        "HOME isolated to cwd for provider '{}': {}",
                        config.getProviderKey(),
                        config.getCwd());
            }

            this.process =
                    new AcpProcess(
                            config.getCommand(), config.getArgs(), config.getCwd(), processEnv);
            process.start();
            status = RuntimeStatus.RUNNING;
            String instanceId = "local-" + process.pid();
            logger.info("LocalRuntimeAdapter started, instanceId={}", instanceId);
            startHealthCheck();
            return instanceId;
        } catch (IOException e) {
            status = RuntimeStatus.ERROR;
            throw new RuntimeException("Failed to start local runtime: " + e.getMessage(), e);
        }
    }

    @Override
    public void send(String jsonLine) throws IOException {
        ensureRunning();
        process.send(jsonLine);
    }

    @Override
    public Flux<String> stdout() {
        if (process == null) {
            return Flux.empty();
        }
        return process.stdout();
    }

    @Override
    public RuntimeStatus getStatus() {
        // Sync status with actual process state
        if (status == RuntimeStatus.RUNNING && process != null && !process.isAlive()) {
            status = RuntimeStatus.ERROR;
        }
        return status;
    }

    @Override
    public boolean isAlive() {
        return process != null && process.isAlive();
    }

    @Override
    public void close() {
        if (status == RuntimeStatus.STOPPED) {
            return;
        }
        logger.info("Closing LocalRuntimeAdapter");
        stopHealthCheck();
        if (process != null) {
            process.close();
        }
        status = RuntimeStatus.STOPPED;
    }

    @Override
    public FileSystemAdapter getFileSystem() {
        return fileSystem;
    }

    /**
     * 注册异常通知监听器。
     *
     * @param listener 异常通知消费者
     */
    public void setFaultListener(Consumer<RuntimeFaultNotification> listener) {
        this.faultListener = listener;
    }

    /**
     * 启动定期健康检查。
     * <p>
     * 以配置的间隔检测子进程是否存活，进程崩溃时标记为 ERROR 并通知上层。
     * 使用 ≤5 秒的检查间隔以满足 Req 8.2 的检测时间要求。
     */
    private void startHealthCheck() {
        healthCheckFuture =
                healthChecker.scheduleAtFixedRate(
                        () -> {
                            try {
                                if (status != RuntimeStatus.RUNNING || process == null) {
                                    return;
                                }
                                if (!process.isAlive()) {
                                    logger.warn("Local CLI process has exited unexpectedly");
                                    status = RuntimeStatus.ERROR;
                                    notifyFault(
                                            RuntimeFaultNotification.FAULT_PROCESS_CRASHED,
                                            RuntimeFaultNotification.ACTION_RESTART);
                                    stopHealthCheck();
                                }
                            } catch (Exception e) {
                                logger.warn("Health check error: {}", e.getMessage());
                            }
                        },
                        healthCheckIntervalSeconds,
                        healthCheckIntervalSeconds,
                        TimeUnit.SECONDS);
    }

    private void stopHealthCheck() {
        if (healthCheckFuture != null && !healthCheckFuture.isCancelled()) {
            healthCheckFuture.cancel(false);
        }
    }

    private void notifyFault(String faultType, String suggestedAction) {
        if (faultListener != null) {
            try {
                faultListener.accept(
                        new RuntimeFaultNotification(
                                faultType, SandboxType.LOCAL, suggestedAction));
            } catch (Exception e) {
                logger.warn("Error notifying fault listener: {}", e.getMessage());
            }
        }
    }

    private void ensureRunning() throws IOException {
        if (status != RuntimeStatus.RUNNING || process == null) {
            throw new IOException("Local runtime is not running, current status: " + status);
        }
    }

    // ===== 测试辅助方法 =====

    long getHealthCheckIntervalSeconds() {
        return healthCheckIntervalSeconds;
    }
}
