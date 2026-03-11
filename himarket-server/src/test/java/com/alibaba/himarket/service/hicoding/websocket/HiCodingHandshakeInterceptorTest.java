package com.alibaba.himarket.service.hicoding.websocket;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;

/**
 * HiCodingHandshakeInterceptor 单元测试。
 * 验证从 WebSocket 握手 URL 中提取查询参数（provider、runtime）并存入 session attributes。
 */
class HiCodingHandshakeInterceptorTest {

    private HiCodingHandshakeInterceptor interceptor;
    private ServerHttpResponse response;
    private WebSocketHandler wsHandler;

    @BeforeEach
    void setUp() {
        interceptor = new HiCodingHandshakeInterceptor();
        response = mock(ServerHttpResponse.class);
        wsHandler = mock(WebSocketHandler.class);
    }

    // ===== runtime 参数提取 =====

    @Test
    void beforeHandshake_runtimeRemote_storedInAttributes() throws Exception {
        Map<String, Object> attributes = new HashMap<>();
        ServerHttpRequest request = mockRequest("ws://localhost/acp?runtime=remote");

        interceptor.beforeHandshake(request, response, wsHandler, attributes);

        assertEquals("remote", attributes.get("runtime"));
    }

    @Test
    void beforeHandshake_runtimeK8s_storedInAttributes() throws Exception {
        Map<String, Object> attributes = new HashMap<>();
        ServerHttpRequest request = mockRequest("ws://localhost/acp?runtime=k8s");

        interceptor.beforeHandshake(request, response, wsHandler, attributes);

        assertEquals("k8s", attributes.get("runtime"));
    }

    @Test
    void beforeHandshake_noRuntimeParam_attributeNotSet() throws Exception {
        Map<String, Object> attributes = new HashMap<>();
        ServerHttpRequest request = mockRequest("ws://localhost/acp");

        interceptor.beforeHandshake(request, response, wsHandler, attributes);

        assertNull(attributes.get("runtime"));
    }

    @Test
    void beforeHandshake_blankRuntimeParam_attributeNotSet() throws Exception {
        Map<String, Object> attributes = new HashMap<>();
        ServerHttpRequest request = mockRequest("ws://localhost/acp?runtime=");

        interceptor.beforeHandshake(request, response, wsHandler, attributes);

        assertNull(attributes.get("runtime"));
    }

    @Test
    void beforeHandshake_runtimeUpperCase_storedAsIs() throws Exception {
        Map<String, Object> attributes = new HashMap<>();
        ServerHttpRequest request = mockRequest("ws://localhost/acp?runtime=K8S");

        interceptor.beforeHandshake(request, response, wsHandler, attributes);

        assertEquals("K8S", attributes.get("runtime"));
    }

    // ===== runtime + provider 同时提取 =====

    @Test
    void beforeHandshake_runtimeAndProvider_bothStored() throws Exception {
        Map<String, Object> attributes = new HashMap<>();
        ServerHttpRequest request = mockRequest("ws://localhost/acp?provider=kiro-cli&runtime=k8s");

        interceptor.beforeHandshake(request, response, wsHandler, attributes);

        assertEquals("kiro-cli", attributes.get("provider"));
        assertEquals("k8s", attributes.get("runtime"));
    }

    @Test
    void beforeHandshake_allParams_allStored() throws Exception {
        Map<String, Object> attributes = new HashMap<>();
        ServerHttpRequest request =
                mockRequest("ws://localhost/acp?token=sometoken&provider=qodercli&runtime=remote");

        interceptor.beforeHandshake(request, response, wsHandler, attributes);

        assertEquals("qodercli", attributes.get("provider"));
        assertEquals("remote", attributes.get("runtime"));
    }

    // ===== provider 参数提取（回归测试） =====

    @Test
    void beforeHandshake_providerParam_storedInAttributes() throws Exception {
        Map<String, Object> attributes = new HashMap<>();
        ServerHttpRequest request = mockRequest("ws://localhost/acp?provider=kiro-cli");

        interceptor.beforeHandshake(request, response, wsHandler, attributes);

        assertEquals("kiro-cli", attributes.get("provider"));
    }

    @Test
    void beforeHandshake_noProvider_attributeNotSet() throws Exception {
        Map<String, Object> attributes = new HashMap<>();
        ServerHttpRequest request = mockRequest("ws://localhost/acp");

        interceptor.beforeHandshake(request, response, wsHandler, attributes);

        assertNull(attributes.get("provider"));
    }

    // ===== 无 token 连接拒绝 =====

    @Test
    void beforeHandshake_noToken_rejectsConnection() throws Exception {
        Map<String, Object> attributes = new HashMap<>();
        ServerHttpRequest request = mockRequest("ws://localhost/acp?runtime=remote");

        boolean result = interceptor.beforeHandshake(request, response, wsHandler, attributes);

        assertFalse(result);
        assertNull(attributes.get("userId"));
    }

    // ===== Helper =====

    private ServerHttpRequest mockRequest(String uriString) throws Exception {
        ServerHttpRequest request = mock(ServerHttpRequest.class);
        when(request.getURI()).thenReturn(new URI(uriString));
        return request;
    }
}
