package com.alibaba.himarket.service.acp;

import com.alibaba.himarket.config.AcpProperties;
import com.alibaba.himarket.service.acp.runtime.RuntimeAdapter;
import com.alibaba.himarket.service.acp.runtime.RuntimeConfig;
import com.alibaba.himarket.service.acp.runtime.SandboxType;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;

/**
 * WebSocket 连接状态和资源管理器。
 *
 * <p>从 AcpWebSocketHandler 中提取连接级别的状态管理和资源清理逻辑， 使 Handler 不再直接持有
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
 *   <li>userSandboxHostMap — userId → 沙箱 host 地址（DevProxy 反向代理路由）
 * </ul>
 */
@Component
public class AcpConnectionManager {

    private static final Logger logger = LoggerFactory.getLogger(AcpConnectionManager.class);

    private final ConcurrentHashMap<String, RuntimeAdapter> runtimeMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Disposable> subscriptionMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> cwdMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> userIdMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> sandboxModeMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Queue<String>> pendingMessageMap =
            new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, DeferredInitParams> deferredInitMap =
            new ConcurrentHashMap<>();

    /** userId → sandboxHost（Remote 模式下沙箱地址，用于 DevProxy 反向代理路由） */
    private final ConcurrentHashMap<String, String> userSandboxHostMap = new ConcurrentHashMap<>();

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
     * 清理连接资源。在 WebSocket 连接关闭时调用，释放该 session 关联的所有资源。
     *
     * <p>清理顺序：pendingMessages → deferredInit → subscription（dispose） → runtime（close） → cwd
     * → sandboxHost（通过 userId 反查） → userId → sandboxMode
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
        if (runtime != null) {
            runtime.close();
        }

        cwdMap.remove(sessionId);

        // 根据 sessionId 反查 userId，清理对应的 sandboxHost 映射
        String userId = userIdMap.remove(sessionId);
        if (userId != null) {
            userSandboxHostMap.remove(userId);
        }

        sandboxModeMap.remove(sessionId);
    }

    /**
     * 设置用户对应的沙箱 host 地址。在沙箱初始化成功后调用，用于 DevProxy 反向代理路由。
     *
     * @param userId 用户 ID
     * @param host 沙箱 host 地址
     */
    public void setSandboxHost(String userId, String host) {
        userSandboxHostMap.put(userId, host);
    }

    /**
     * 获取用户对应的沙箱 host 地址。
     *
     * @param userId 用户 ID
     * @return 沙箱 host 地址，未设置时返回 null
     */
    public String getSandboxHost(String userId) {
        return userSandboxHostMap.get(userId);
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
     * 移除指定 session 的待转发消息队列。
     * 在沙箱初始化完成后调用，标记该 session 不再需要缓存消息。
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
