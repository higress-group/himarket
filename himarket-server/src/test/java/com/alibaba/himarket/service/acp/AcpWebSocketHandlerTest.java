package com.alibaba.himarket.service.acp;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.alibaba.himarket.config.AcpProperties;
import com.alibaba.himarket.config.AcpProperties.CliProviderConfig;
import com.alibaba.himarket.service.acp.runtime.K8sClusterInfo;
import com.alibaba.himarket.service.acp.runtime.K8sConfigService;
import com.alibaba.himarket.service.acp.runtime.K8sRuntimeAdapter;
import com.alibaba.himarket.service.acp.runtime.PodInfo;
import com.alibaba.himarket.service.acp.runtime.PodReuseManager;
import com.alibaba.himarket.service.acp.runtime.RuntimeAdapter;
import com.alibaba.himarket.service.acp.runtime.RuntimeConfig;
import com.alibaba.himarket.service.acp.runtime.RuntimeFactory;
import com.alibaba.himarket.service.acp.runtime.RuntimeType;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import reactor.core.publisher.Flux;

/**
 * AcpWebSocketHandler 单元测试。
 * 验证改造后通过 RuntimeFactory/RuntimeAdapter 管理运行时的核心逻辑。
 */
class AcpWebSocketHandlerTest {

    @TempDir java.nio.file.Path tempDir;

    private AcpProperties properties;
    private ObjectMapper objectMapper;
    private RuntimeFactory runtimeFactory;
    private RuntimeAdapter mockRuntime;
    private K8sConfigService k8sConfigService;
    private PodReuseManager podReuseManager;
    private AcpWebSocketHandler handler;

    @BeforeEach
    void setUp() {
        properties = new AcpProperties();
        properties.setDefaultProvider("test-cli");
        properties.setWorkspaceRoot(tempDir.toString());

        CliProviderConfig providerConfig = new CliProviderConfig();
        providerConfig.setCommand("echo");
        providerConfig.setArgs("--acp");
        providerConfig.setEnv(new LinkedHashMap<>());

        Map<String, CliProviderConfig> providers = new LinkedHashMap<>();
        providers.put("test-cli", providerConfig);
        properties.setProviders(providers);

        objectMapper = new ObjectMapper();
        runtimeFactory = mock(RuntimeFactory.class);
        mockRuntime = mock(RuntimeAdapter.class);

        when(runtimeFactory.create(any(RuntimeType.class), any(RuntimeConfig.class)))
                .thenReturn(mockRuntime);
        when(mockRuntime.start(any(RuntimeConfig.class))).thenReturn("local-123");
        when(mockRuntime.stdout()).thenReturn(Flux.empty());

        k8sConfigService = mock(K8sConfigService.class);
        podReuseManager = mock(PodReuseManager.class);
        handler =
                new AcpWebSocketHandler(
                        properties,
                        objectMapper,
                        runtimeFactory,
                        k8sConfigService,
                        podReuseManager);
    }

    // ===== resolveRuntimeType =====

    @Test
    void resolveRuntimeType_null_defaultsToLocal() {
        assertEquals(RuntimeType.LOCAL, handler.resolveRuntimeType(null));
    }

    @Test
    void resolveRuntimeType_blank_defaultsToLocal() {
        assertEquals(RuntimeType.LOCAL, handler.resolveRuntimeType(""));
        assertEquals(RuntimeType.LOCAL, handler.resolveRuntimeType("  "));
    }

    @Test
    void resolveRuntimeType_local_returnsLocal() {
        assertEquals(RuntimeType.LOCAL, handler.resolveRuntimeType("local"));
        assertEquals(RuntimeType.LOCAL, handler.resolveRuntimeType("LOCAL"));
        assertEquals(RuntimeType.LOCAL, handler.resolveRuntimeType("Local"));
    }

    @Test
    void resolveRuntimeType_k8s_returnsK8s() {
        assertEquals(RuntimeType.K8S, handler.resolveRuntimeType("k8s"));
        assertEquals(RuntimeType.K8S, handler.resolveRuntimeType("K8S"));
    }

    @Test
    void resolveRuntimeType_unknown_defaultsToLocal() {
        assertEquals(RuntimeType.LOCAL, handler.resolveRuntimeType("docker"));
        assertEquals(RuntimeType.LOCAL, handler.resolveRuntimeType("invalid"));
    }

    // ===== afterConnectionEstablished =====

    @Test
    void afterConnectionEstablished_noUserId_closesSession() throws Exception {
        WebSocketSession session = mockSession(null, null, null);

        handler.afterConnectionEstablished(session);

        verify(session).close(CloseStatus.POLICY_VIOLATION);
        verifyNoInteractions(runtimeFactory);
    }

    @Test
    void afterConnectionEstablished_localRuntime_createsViaFactory() throws Exception {
        WebSocketSession session = mockSession("user1", "test-cli", null);

        handler.afterConnectionEstablished(session);

        verify(runtimeFactory).create(eq(RuntimeType.LOCAL), any(RuntimeConfig.class));
        verify(mockRuntime).start(any(RuntimeConfig.class));
        verify(mockRuntime).stdout();
    }

    @Test
    void afterConnectionEstablished_explicitLocalRuntime_createsViaFactory() throws Exception {
        WebSocketSession session = mockSession("user1", "test-cli", "local");

        handler.afterConnectionEstablished(session);

        verify(runtimeFactory).create(eq(RuntimeType.LOCAL), any(RuntimeConfig.class));
        verify(mockRuntime).start(any(RuntimeConfig.class));
    }

    @Test
    void afterConnectionEstablished_unknownProvider_closesSession() throws Exception {
        WebSocketSession session = mockSession("user1", "unknown-cli", null);

        handler.afterConnectionEstablished(session);

        verify(session).close(CloseStatus.POLICY_VIOLATION);
        verifyNoInteractions(runtimeFactory);
    }

    @Test
    void afterConnectionEstablished_runtimeStartFails_closesSession() throws Exception {
        when(mockRuntime.start(any())).thenThrow(new RuntimeException("start failed"));
        WebSocketSession session = mockSession("user1", "test-cli", null);

        handler.afterConnectionEstablished(session);

        verify(session).close(CloseStatus.SERVER_ERROR);
    }

    @Test
    void afterConnectionEstablished_defaultProvider_usedWhenNoProviderParam() throws Exception {
        WebSocketSession session = mockSession("user1", null, null);

        handler.afterConnectionEstablished(session);

        verify(runtimeFactory).create(eq(RuntimeType.LOCAL), any(RuntimeConfig.class));
    }

    // ===== handleTextMessage =====

    @Test
    void handleTextMessage_delegatesToRuntimeSend() throws Exception {
        WebSocketSession session = mockSession("user1", "test-cli", null);
        handler.afterConnectionEstablished(session);

        handler.handleTextMessage(session, new TextMessage("{\"method\":\"test\"}"));

        verify(mockRuntime).send(anyString());
    }

    @Test
    void handleTextMessage_noRuntime_logsWarning() throws Exception {
        // Session with no runtime registered (e.g., unknown session)
        WebSocketSession session = mockSession("user1", "test-cli", null);
        // Don't call afterConnectionEstablished, so no runtime is registered

        handler.handleTextMessage(session, new TextMessage("{\"method\":\"test\"}"));

        verify(mockRuntime, never()).send(anyString());
    }

    @Test
    void handleTextMessage_blankPayload_ignored() throws Exception {
        WebSocketSession session = mockSession("user1", "test-cli", null);
        handler.afterConnectionEstablished(session);

        handler.handleTextMessage(session, new TextMessage("  "));

        verify(mockRuntime, never()).send(anyString());
    }

    // ===== afterConnectionClosed =====

    @Test
    void afterConnectionClosed_closesRuntime() throws Exception {
        WebSocketSession session = mockSession("user1", "test-cli", null);
        handler.afterConnectionEstablished(session);

        handler.afterConnectionClosed(session, CloseStatus.NORMAL);

        verify(mockRuntime).close();
    }

    // ===== handleTransportError =====

    @Test
    void handleTransportError_closesRuntime() throws Exception {
        WebSocketSession session = mockSession("user1", "test-cli", null);
        handler.afterConnectionEstablished(session);

        handler.handleTransportError(session, new IOException("connection lost"));

        verify(mockRuntime).close();
    }

    // ===== Helper =====

    private WebSocketSession mockSession(String userId, String provider, String runtime) {
        return mockSession(userId, provider, runtime, null);
    }

    private WebSocketSession mockSession(
            String userId, String provider, String runtime, String sandboxMode) {
        WebSocketSession session = mock(WebSocketSession.class);
        Map<String, Object> attrs = new HashMap<>();
        if (userId != null) attrs.put("userId", userId);
        if (provider != null) attrs.put("provider", provider);
        if (runtime != null) attrs.put("runtime", runtime);
        if (sandboxMode != null) attrs.put("sandboxMode", sandboxMode);
        when(session.getAttributes()).thenReturn(attrs);
        when(session.getId()).thenReturn("session-" + System.nanoTime());
        when(session.isOpen()).thenReturn(true);
        return session;
    }

    // ===== 路由逻辑：sandboxMode=user + K8s 走复用路径 (Requirements 2.2) =====

    @Test
    void afterConnectionEstablished_k8sWithSandboxModeUser_usesPodReuseManager() throws Exception {
        // 准备 K8s 集群配置
        K8sClusterInfo cluster =
                new K8sClusterInfo(
                        "cluster-1", "test-cluster", "https://k8s:6443", true, Instant.now());
        when(k8sConfigService.listClusters()).thenReturn(List.of(cluster));

        // 准备 K8sRuntimeAdapter mock
        K8sRuntimeAdapter mockK8sAdapter = mock(K8sRuntimeAdapter.class);
        when(mockK8sAdapter.stdout()).thenReturn(Flux.empty());
        when(runtimeFactory.create(eq(RuntimeType.K8S), any(RuntimeConfig.class)))
                .thenReturn(mockK8sAdapter);

        // 准备 PodReuseManager 返回 PodInfo
        PodInfo podInfo =
                new PodInfo(
                        "sandbox-user1-abc",
                        "10.0.0.1",
                        null,
                        URI.create("ws://10.0.0.1:8080/"),
                        true);
        when(podReuseManager.acquirePod(eq("user1"), any(RuntimeConfig.class))).thenReturn(podInfo);

        WebSocketSession session = mockSession("user1", "test-cli", "k8s", "user");

        handler.afterConnectionEstablished(session);

        // Pod 初始化是异步的，使用 timeout 等待异步线程完成
        verify(podReuseManager, timeout(5000)).acquirePod(eq("user1"), any(RuntimeConfig.class));
        verify(runtimeFactory, timeout(5000)).create(eq(RuntimeType.K8S), any(RuntimeConfig.class));
        verify(mockK8sAdapter, timeout(5000)).setReuseMode(true);
        verify(mockK8sAdapter, timeout(5000)).startWithExistingPod(podInfo);
        // 不应调用普通的 start
        verify(mockK8sAdapter, never()).start(any(RuntimeConfig.class));
    }

    // ===== 路由逻辑：sandboxMode 缺失 + K8s 默认回退到用户级沙箱 (Requirements 2.3) =====

    @Test
    void afterConnectionEstablished_k8sWithoutSandboxMode_defaultsToUserScoped() throws Exception {
        K8sClusterInfo cluster =
                new K8sClusterInfo(
                        "cluster-1", "test-cluster", "https://k8s:6443", true, Instant.now());
        when(k8sConfigService.listClusters()).thenReturn(List.of(cluster));

        K8sRuntimeAdapter mockK8sAdapter = mock(K8sRuntimeAdapter.class);
        when(mockK8sAdapter.stdout()).thenReturn(Flux.empty());
        when(runtimeFactory.create(eq(RuntimeType.K8S), any(RuntimeConfig.class)))
                .thenReturn(mockK8sAdapter);

        PodInfo podInfo =
                new PodInfo(
                        "sandbox-user1-xyz",
                        "10.0.0.2",
                        null,
                        URI.create("ws://10.0.0.2:8080/"),
                        false);
        when(podReuseManager.acquirePod(eq("user1"), any(RuntimeConfig.class))).thenReturn(podInfo);

        // sandboxMode 缺失（不传 sandboxMode 参数）
        WebSocketSession session = mockSession("user1", "test-cli", "k8s", null);

        handler.afterConnectionEstablished(session);

        // POC 阶段：K8s 运行时默认使用用户级沙箱，应走复用路径（异步）
        verify(podReuseManager, timeout(5000)).acquirePod(eq("user1"), any(RuntimeConfig.class));
        verify(mockK8sAdapter, timeout(5000)).setReuseMode(true);
        verify(mockK8sAdapter, timeout(5000)).startWithExistingPod(podInfo);
        verify(mockK8sAdapter, never()).start(any(RuntimeConfig.class));
    }

    // ===== 路由逻辑：非 K8s 运行时走原有逻辑 (Requirements 2.2) =====

    @Test
    void afterConnectionEstablished_localRuntime_doesNotUsePodReuseManager() throws Exception {
        WebSocketSession session = mockSession("user1", "test-cli", "local", "user");

        handler.afterConnectionEstablished(session);

        // 即使 sandboxMode=user，非 K8s 运行时也走原有逻辑
        verify(runtimeFactory).create(eq(RuntimeType.LOCAL), any(RuntimeConfig.class));
        verify(mockRuntime).start(any(RuntimeConfig.class));
        verifyNoInteractions(podReuseManager);
    }

    // ===== cleanup：用户级沙箱模式下调用 releasePod =====

    @Test
    void cleanup_userSandboxMode_callsReleasePod() throws Exception {
        K8sClusterInfo cluster =
                new K8sClusterInfo(
                        "cluster-1", "test-cluster", "https://k8s:6443", true, Instant.now());
        when(k8sConfigService.listClusters()).thenReturn(List.of(cluster));

        K8sRuntimeAdapter mockK8sAdapter = mock(K8sRuntimeAdapter.class);
        when(mockK8sAdapter.stdout()).thenReturn(Flux.empty());
        when(runtimeFactory.create(eq(RuntimeType.K8S), any(RuntimeConfig.class)))
                .thenReturn(mockK8sAdapter);

        PodInfo podInfo =
                new PodInfo(
                        "sandbox-user1-abc",
                        "10.0.0.1",
                        null,
                        URI.create("ws://10.0.0.1:8080/"),
                        true);
        when(podReuseManager.acquirePod(eq("user1"), any(RuntimeConfig.class))).thenReturn(podInfo);

        WebSocketSession session = mockSession("user1", "test-cli", "k8s", "user");
        handler.afterConnectionEstablished(session);

        // 等待异步 Pod 初始化完成
        verify(podReuseManager, timeout(5000)).acquirePod(eq("user1"), any(RuntimeConfig.class));

        // 触发 cleanup（通过关闭连接）
        handler.afterConnectionClosed(session, CloseStatus.NORMAL);

        verify(mockK8sAdapter, timeout(5000)).close();
        verify(podReuseManager, timeout(5000)).releasePod("user1");
    }

    // ===== cleanup：非用户级沙箱模式不调用 releasePod =====

    @Test
    void cleanup_localRuntime_doesNotCallReleasePod() throws Exception {
        WebSocketSession session = mockSession("user1", "test-cli", "local", null);
        handler.afterConnectionEstablished(session);

        handler.afterConnectionClosed(session, CloseStatus.NORMAL);

        verify(mockRuntime).close();
        verify(podReuseManager, never()).releasePod(anyString());
    }
}
