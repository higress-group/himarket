package com.alibaba.himarket.service.hicoding.websocket;

import com.alibaba.himarket.config.AcpProperties;
import com.alibaba.himarket.service.hicoding.runtime.RemoteRuntimeAdapter;
import com.alibaba.himarket.service.hicoding.runtime.RuntimeAdapter;
import com.alibaba.himarket.service.hicoding.runtime.RuntimeConfig;
import com.alibaba.himarket.service.hicoding.sandbox.SandboxType;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;

/**
 * WebSocket 连接状态和资源管理器。
 *
 * <p>从 HiCodingWebSocketHandler 中提取连接级别的状态管理和资源清理逻辑， 使 Handler 不再直接持有
 * ConcurrentHashMap 字段。
 *
 * <p>管理的状态包括：
 *
 * <ul>
 *   <li>runtimeMap — session → RuntimeAdapter（CLI 运行时）
 *   <li>subscriptionMap — session → Disposable（stdout 订阅）
 *   <li>cwdMap — session → 工作目录
 *   <li>userIdMap — session → 用户 ID
 *   <li>sandboxModeMap — session → 沙箱模式
 *   <li>pendingMessageMap — session → 待转发消息队列（初始化期间缓存）
 *   <li>deferredInitMap — session → 延迟初始化参数
 *   <li>detachedSessionMap — userId → DetachedSessionInfo（已 detach 但仍可 reattach 的会话）
 * </ul>
 */
@Component
public class HiCodingConnectionManager {

    private static final Logger logger = LoggerFactory.getLogger(HiCodingConnectionManager.class);
    private static final long DETACH_TTL_MILLIS = 10 * 60 * 1000L; // 10 分钟

    private final ConcurrentHashMap<String, RuntimeAdapter> runtimeMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Disposable> subscriptionMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> cwdMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> userIdMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> sandboxModeMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Queue<String>> pendingMessageMap =
            new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, DeferredInitParams> deferredInitMap =
            new ConcurrentHashMap<>();

    /** userId → 已 detach 但可 reattach 的会话信息。 */
    private final ConcurrentHashMap<String, DetachedSessionInfo> detachedSessionMap =
            new ConcurrentHashMap<>();

    /** 定期清理过期 detached 会话的调度器。 */
    private final ScheduledExecutorService cleanupScheduler =
            Executors.newSingleThreadScheduledExecutor(
                    r -> {
                        Thread t = new Thread(r, "detach-cleanup");
                        t.setDaemon(true);
                        return t;
                    });

    /**
     * 延迟初始化上下文：当 WebSocket 握手时 URL 中没有 cliSessionConfig， 等待前端通过 session/config
     * 消息发送配置后再启动 pipeline。 存储 afterConnectionEstablished 中解析好的参数，供 session/config
     * 消息到达时使用。
     */
    public record DeferredInitParams(
            String userId,
            String providerKey,
            RuntimeConfig config,
            AcpProperties.CliProviderConfig providerConfig,
            SandboxType sandboxType) {}

    /**
     * 已 detach 的会话信息，存储在 detachedSessionMap 中。 当前端 WebSocket 重连时，可通过 userId
     * 查找并 reattach。
     */
    public record DetachedSessionInfo(
            String sidecarSessionId,
            RuntimeAdapter adapter,
            String cwd,
            String sandboxMode,
            long detachedAtMillis) {}

    @PostConstruct
    void startCleanup() {
        cleanupScheduler.scheduleAtFixedRate(
                this::cleanupExpiredDetachedSessions, 1, 1, TimeUnit.MINUTES);
    }

    @PreDestroy
    void shutdown() {
        cleanupScheduler.shutdownNow();
    }

    private void cleanupExpiredDetachedSessions() {
        long now = System.currentTimeMillis();
        detachedSessionMap.forEach(
                (userId, info) -> {
                    if (now - info.detachedAtMillis() > DETACH_TTL_MILLIS) {
                        DetachedSessionInfo removed = detachedSessionMap.remove(userId);
                        if (removed != null && removed.adapter() != null) {
                            removed.adapter().close();
                            logger.info(
                                    "Cleaned up expired detached session: userId={}, age={}s",
                                    userId,
                                    (now - removed.detachedAtMillis()) / 1000);
                        }
                    }
                });
    }

    /**
     * 注册新连接。在 WebSocket 连接建立时调用，初始化该 session 的所有状态映射。
     *
     * @param sessionId WebSocket session ID
     * @param userId 用户 ID
     * @param cwd 工作目录
     * @param sandboxMode 沙箱模式（可为 null）
     */
    public void registerConnection(
            String sessionId, String userId, String cwd, String sandboxMode) {
        userIdMap.put(sessionId, userId);
        cwdMap.put(sessionId, cwd);
        sandboxModeMap.put(sessionId, sandboxMode != null ? sandboxMode : "");
        pendingMessageMap.put(sessionId, new ConcurrentLinkedQueue<>());
    }

    /**
     * 注册初始化成功后的运行时资源。在沙箱初始化完成后调用。
     *
     * @param sessionId WebSocket session ID
     * @param adapter CLI 运行时适配器
     * @param subscription stdout 订阅句柄
     */
    public void registerRuntime(String sessionId, RuntimeAdapter adapter, Disposable subscription) {
        runtimeMap.put(sessionId, adapter);
        subscriptionMap.put(sessionId, subscription);
    }

    /**
     * 清理连接资源。在 WebSocket 连接关闭时调用。
     *
     * <p>如果 RuntimeAdapter 是 RemoteRuntimeAdapter 且已获取到 sidecarSessionId，
     * 则执行 detach 操作（保留 sidecar 进程），而非直接 close。 detach 后的会话存储在 detachedSessionMap
     * 中，等待前端重连。
     *
     * <p>清理顺序：pendingMessages → deferredInit → subscription（dispose） → runtime（detach 或 close）
     * → cwd → userId → sandboxMode
     *
     * @param sessionId WebSocket session ID
     */
    public void cleanup(String sessionId) {
        pendingMessageMap.remove(sessionId);
        deferredInitMap.remove(sessionId);

        Disposable subscription = subscriptionMap.remove(sessionId);
        if (subscription != null && !subscription.isDisposed()) {
            subscription.dispose();
        }

        RuntimeAdapter runtime = runtimeMap.remove(sessionId);
        String userId = userIdMap.remove(sessionId);
        String cwd = cwdMap.remove(sessionId);
        String sandboxMode = sandboxModeMap.remove(sessionId);

        // 如果是 RemoteRuntimeAdapter 且有 sidecarSessionId，执行 detach 而非 close
        if (runtime instanceof RemoteRuntimeAdapter remoteAdapter
                && remoteAdapter.getSidecarSessionId() != null
                && userId != null) {
            remoteAdapter.detach();
            detachedSessionMap.put(
                    userId,
                    new DetachedSessionInfo(
                            remoteAdapter.getSidecarSessionId(),
                            runtime,
                            cwd,
                            sandboxMode != null ? sandboxMode : "",
                            System.currentTimeMillis()));
            logger.info(
                    "Session detached for userId={}, sidecarSessionId={}",
                    userId,
                    remoteAdapter.getSidecarSessionId());
            return;
        }

        // 非 Remote 或无 sidecarSessionId，直接 close
        if (runtime != null) {
            runtime.close();
        }
    }

    /**
     * 原子地获取并移除指定用户的 detached 会话。 如果存在则返回 DetachedSessionInfo，否则返回 null。
     *
     * @param userId 用户 ID
     * @return DetachedSessionInfo，不存在时返回 null
     */
    public DetachedSessionInfo takeDetachedSession(String userId) {
        return detachedSessionMap.remove(userId);
    }

    /**
     * 查看指定用户是否有 detached 会话（不移除）。
     *
     * @param userId 用户 ID
     * @return DetachedSessionInfo，不存在时返回 null
     */
    public DetachedSessionInfo peekDetachedSession(String userId) {
        return detachedSessionMap.get(userId);
    }

    /**
     * 强制销毁指定用户的 detached 会话。
     *
     * @param userId 用户 ID
     */
    public void destroyDetachedSession(String userId) {
        DetachedSessionInfo detached = detachedSessionMap.remove(userId);
        if (detached != null && detached.adapter() != null) {
            detached.adapter().close();
            logger.info(
                    "Destroyed detached session for userId={}, sidecarSessionId={}",
                    userId,
                    detached.sidecarSessionId());
        }
    }

    /**
     * 获取指定 session 的 RuntimeAdapter。
     *
     * @param sessionId WebSocket session ID
     * @return RuntimeAdapter，未初始化时返回 null
     */
    public RuntimeAdapter getRuntime(String sessionId) {
        return runtimeMap.get(sessionId);
    }

    /**
     * 获取指定 session 的用户 ID。
     *
     * @param sessionId WebSocket session ID
     * @return 用户 ID，未注册时返回 null
     */
    public String getUserId(String sessionId) {
        return userIdMap.get(sessionId);
    }

    /**
     * 获取指定 session 的工作目录。
     *
     * @param sessionId WebSocket session ID
     * @return 工作目录路径，未注册时返回 null
     */
    public String getCwd(String sessionId) {
        return cwdMap.get(sessionId);
    }

    /**
     * 获取指定 session 的待转发消息队列。
     *
     * @param sessionId WebSocket session ID
     * @return 消息队列，未注册时返回 null
     */
    public Queue<String> getPendingMessages(String sessionId) {
        return pendingMessageMap.get(sessionId);
    }

    /**
     * 移除指定 session 的待转发消息队列。 在沙箱初始化完成后调用，标记该 session 不再需要缓存消息。
     *
     * @param sessionId WebSocket session ID
     */
    public void removePendingMessages(String sessionId) {
        pendingMessageMap.remove(sessionId);
    }

    /**
     * 原子地获取并移除指定 session 的延迟初始化参数。 使用 remove() 而非 get()，确保参数只被消费一次。
     *
     * @param sessionId WebSocket session ID
     * @return 延迟初始化参数，不存在时返回 null
     */
    public DeferredInitParams getDeferredInit(String sessionId) {
        return deferredInitMap.remove(sessionId);
    }

    /**
     * 设置指定 session 的延迟初始化参数。
     *
     * @param sessionId WebSocket session ID
     * @param params 延迟初始化参数
     */
    public void setDeferredInit(String sessionId, DeferredInitParams params) {
        deferredInitMap.put(sessionId, params);
    }
}
