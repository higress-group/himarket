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
 * WebSocket connection state and resource manager.
 *
 * <p>Extracts connection-level state management and resource cleanup from
 * HiCodingWebSocketHandler, so the handler no longer owns ConcurrentHashMap fields directly.
 *
 * <p>Managed state includes:
 *
 * <ul>
 *   <li>runtimeMap: session to RuntimeAdapter for the CLI runtime
 *   <li>subscriptionMap: session to Disposable stdout subscription
 *   <li>cwdMap: session to working directory
 *   <li>userIdMap: session to user ID
 *   <li>sandboxModeMap: session to sandbox mode
 *   <li>pendingMessageMap: session to queued messages buffered during initialization
 *   <li>deferredInitMap: session to deferred initialization parameters
 *   <li>detachedSessionMap: userId to detached sessions that can still be reattached
 * </ul>
 */
@Component
public class HiCodingConnectionManager {

    private static final Logger logger = LoggerFactory.getLogger(HiCodingConnectionManager.class);
    private static final long DETACH_TTL_MILLIS = 10 * 60 * 1000L; // 10 minutes.

    private final ConcurrentHashMap<String, RuntimeAdapter> runtimeMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Disposable> subscriptionMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> cwdMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> userIdMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> sandboxModeMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Queue<String>> pendingMessageMap =
            new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, DeferredInitParams> deferredInitMap =
            new ConcurrentHashMap<>();

    /**
     * Detached sessions keyed by userId that can still be reattached.
     */
    private final ConcurrentHashMap<String, DetachedSessionInfo> detachedSessionMap =
            new ConcurrentHashMap<>();

    /**
     * Scheduler that periodically cleans up expired detached sessions.
     */
    private final ScheduledExecutorService cleanupScheduler =
            Executors.newSingleThreadScheduledExecutor(
                    r -> {
                        Thread t = new Thread(r, "detach-cleanup");
                        t.setDaemon(true);
                        return t;
                    });

    /**
     * Deferred initialization context.
     *
     * <p>When the WebSocket handshake URL does not contain cliSessionConfig, the pipeline waits for
     * the frontend to send configuration through the session/config message. This record stores the
     * parameters parsed in afterConnectionEstablished until that message arrives.
     */
    public record DeferredInitParams(
            String userId,
            String providerKey,
            RuntimeConfig config,
            AcpProperties.CliProviderConfig providerConfig,
            SandboxType sandboxType) {}

    /**
     * Detached session information stored in detachedSessionMap.
     *
     * <p>When the frontend WebSocket reconnects, it can be looked up by userId and reattached.
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
                                    "Cleaned up expired detached session, userId={}, age={}s",
                                    userId,
                                    (now - removed.detachedAtMillis()) / 1000);
                        }
                    }
                });
    }

    /**
     * Registers a new connection.
     *
     * <p>Called when the WebSocket connection is established to initialize all state maps for the
     * session.
     *
     * @param sessionId WebSocket session ID
     * @param userId user ID
     * @param cwd working directory
     * @param sandboxMode sandbox mode, nullable
     */
    public void registerConnection(
            String sessionId, String userId, String cwd, String sandboxMode) {
        userIdMap.put(sessionId, userId);
        cwdMap.put(sessionId, cwd);
        sandboxModeMap.put(sessionId, sandboxMode != null ? sandboxMode : "");
        pendingMessageMap.put(sessionId, new ConcurrentLinkedQueue<>());
    }

    /**
     * Registers runtime resources after successful initialization.
     *
     * <p>Called after sandbox initialization completes.
     *
     * @param sessionId WebSocket session ID
     * @param adapter CLI runtime adapter
     * @param subscription stdout subscription handle
     */
    public void registerRuntime(String sessionId, RuntimeAdapter adapter, Disposable subscription) {
        runtimeMap.put(sessionId, adapter);
        subscriptionMap.put(sessionId, subscription);
    }

    /**
     * Cleans up connection resources.
     *
     * <p>Called when the WebSocket connection closes. If the RuntimeAdapter is a
     * RemoteRuntimeAdapter with a sidecarSessionId, it detaches and preserves the Sidecar process
     * instead of closing it directly. The detached session is stored in detachedSessionMap for a
     * future frontend reconnect.
     *
     * <p>Cleanup order: pendingMessages, deferredInit, subscription disposal, runtime detach or
     * close, cwd, userId, sandboxMode.
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

        // Detach RemoteRuntimeAdapter instances with a sidecarSessionId instead of closing them.
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

        // Non-remote runtimes or runtimes without sidecarSessionId are closed directly.
        if (runtime != null) {
            runtime.close();
        }
    }

    /**
     * Atomically gets and removes the detached session for the specified user.
     *
     * @param userId user ID
     * @return DetachedSessionInfo, or null when none exists
     */
    public DetachedSessionInfo takeDetachedSession(String userId) {
        return detachedSessionMap.remove(userId);
    }

    /**
     * Returns the detached session for the specified user without removing it.
     *
     * @param userId user ID
     * @return DetachedSessionInfo, or null when none exists
     */
    public DetachedSessionInfo peekDetachedSession(String userId) {
        return detachedSessionMap.get(userId);
    }

    /**
     * Force-destroys the detached session for the specified user.
     *
     * @param userId user ID
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
     * Returns the RuntimeAdapter for the specified session.
     *
     * @param sessionId WebSocket session ID
     * @return RuntimeAdapter, or null before initialization
     */
    public RuntimeAdapter getRuntime(String sessionId) {
        return runtimeMap.get(sessionId);
    }

    /**
     * Returns the user ID for the specified session.
     *
     * @param sessionId WebSocket session ID
     * @return user ID, or null when unregistered
     */
    public String getUserId(String sessionId) {
        return userIdMap.get(sessionId);
    }

    /**
     * Returns the working directory for the specified session.
     *
     * @param sessionId WebSocket session ID
     * @return working directory path, or null when unregistered
     */
    public String getCwd(String sessionId) {
        return cwdMap.get(sessionId);
    }

    /**
     * Returns queued messages for the specified session.
     *
     * @param sessionId WebSocket session ID
     * @return message queue, or null when unregistered
     */
    public Queue<String> getPendingMessages(String sessionId) {
        return pendingMessageMap.get(sessionId);
    }

    /**
     * Removes the queued message map entry for the specified session.
     *
     * <p>Called after sandbox initialization completes to mark that the session no longer needs
     * message buffering.
     *
     * @param sessionId WebSocket session ID
     */
    public void removePendingMessages(String sessionId) {
        pendingMessageMap.remove(sessionId);
    }

    /**
     * Atomically gets and removes deferred initialization parameters for the specified session.
     *
     * <p>Uses remove() instead of get() to guarantee the parameters are consumed only once.
     *
     * @param sessionId WebSocket session ID
     * @return deferred initialization parameters, or null when none exist
     */
    public DeferredInitParams getDeferredInit(String sessionId) {
        return deferredInitMap.remove(sessionId);
    }

    /**
     * Sets deferred initialization parameters for the specified session.
     *
     * @param sessionId WebSocket session ID
     * @param params deferred initialization parameters
     */
    public void setDeferredInit(String sessionId, DeferredInitParams params) {
        deferredInitMap.put(sessionId, params);
    }
}
