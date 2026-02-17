package com.alibaba.himarket.service.acp;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.alibaba.himarket.config.AcpProperties;
import com.alibaba.himarket.config.AcpProperties.CliProviderConfig;
import com.alibaba.himarket.service.acp.runtime.RuntimeAdapter;
import com.alibaba.himarket.service.acp.runtime.RuntimeConfig;
import com.alibaba.himarket.service.acp.runtime.RuntimeFactory;
import com.alibaba.himarket.service.acp.runtime.RuntimeType;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
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

        handler = new AcpWebSocketHandler(properties, objectMapper, runtimeFactory);
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
        WebSocketSession session = mock(WebSocketSession.class);
        Map<String, Object> attrs = new HashMap<>();
        if (userId != null) attrs.put("userId", userId);
        if (provider != null) attrs.put("provider", provider);
        if (runtime != null) attrs.put("runtime", runtime);
        when(session.getAttributes()).thenReturn(attrs);
        when(session.getId()).thenReturn("session-" + System.nanoTime());
        when(session.isOpen()).thenReturn(true);
        return session;
    }
}
