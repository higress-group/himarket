package com.alibaba.himarket.service.acp;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;

/**
 * AcpHandshakeInterceptor 单元测试。
 * 验证从 WebSocket 握手 URL 中提取查询参数（provider、runtime）并存入 session attributes。
 */
class AcpHandshakeInterceptorTest {

    private AcpHandshakeInterceptor interceptor;
    private ServerHttpResponse response;
    private WebSocketHandler wsHandler;

    @BeforeEach
    void setUp() {
        interceptor = new AcpHandshakeInterceptor(new ObjectMapper());
        response = mock(ServerHttpResponse.class);
        wsHandler = mock(WebSocketHandler.class);
    }

    // ===== runtime 参数提取 =====

    @Test
    void beforeHandshake_runtimeLocal_storedInAttributes() throws Exception {
        Map<String, Object> attributes = new HashMap<>();
        ServerHttpRequest request = mockRequest("ws://localhost/acp?runtime=local");

        interceptor.beforeHandshake(request, response, wsHandler, attributes);

        assertEquals("local", attributes.get("runtime"));
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
                mockRequest("ws://localhost/acp?token=sometoken&provider=qodercli&runtime=local");

        interceptor.beforeHandshake(request, response, wsHandler, attributes);

        assertEquals("qodercli", attributes.get("provider"));
        assertEquals("local", attributes.get("runtime"));
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

    // ===== 匿名访问（POC 模式） =====

    @Test
    void beforeHandshake_noToken_allowsAnonymous() throws Exception {
        Map<String, Object> attributes = new HashMap<>();
        ServerHttpRequest request = mockRequest("ws://localhost/acp?runtime=local");

        boolean result = interceptor.beforeHandshake(request, response, wsHandler, attributes);

        assertTrue(result);
        assertEquals("anonymous", attributes.get("userId"));
    }

    // ===== Helper =====

    private ServerHttpRequest mockRequest(String uriString) throws Exception {
        ServerHttpRequest request = mock(ServerHttpRequest.class);
        when(request.getURI()).thenReturn(new URI(uriString));
        return request;
    }
}
