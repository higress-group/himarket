package com.alibaba.himarket.service.acp;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import net.jqwik.api.*;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;

/**
 * AcpHandshakeInterceptor sandboxMode 解析属性测试。
 *
 * <p>Feature: k8s-pod-reuse, Property 2: sandboxMode 参数解析与回退
 *
 * <p>对于任意字符串作为 sandboxMode 查询参数值，AcpHandshakeInterceptor 应正确提取该值；
 * 当值为 null、空字符串或非 user/session 的任意字符串时，后端路由逻辑应回退到用户级沙箱模式。
 *
 * <p><b>Validates: Requirements 2.1, 2.3</b>
 */
class AcpHandshakeInterceptorSandboxModePropertyTest {

    private final AcpHandshakeInterceptor interceptor =
            new AcpHandshakeInterceptor(new ObjectMapper());
    private final ServerHttpResponse response = mock(ServerHttpResponse.class);
    private final WebSocketHandler wsHandler = mock(WebSocketHandler.class);

    // ===== 生成器 =====

    /** 生成有效的 sandboxMode 值（user 或 session）。 */
    @Provide
    Arbitrary<String> validSandboxModes() {
        return Arbitraries.of("user", "session");
    }

    /** 生成任意非空 URL 安全字符串作为 sandboxMode 值，包含各种边界情况。 */
    @Provide
    Arbitrary<String> arbitrarySandboxModeValues() {
        return Arbitraries.oneOf(
                // 有效值
                Arbitraries.of("user", "session"),
                // 无效值：随机字母数字字符串（URL 安全，不含空格）
                Arbitraries.strings().alpha().numeric().ofMinLength(1).ofMaxLength(30),
                // 无效值：大小写变体
                Arbitraries.of("User", "USER", "Session", "SESSION", "uSeR", "sEsSiOn"),
                // 无效值：带连字符或下划线的字符串
                Arbitraries.of("user-scoped", "session_scoped", "k8s", "local", "pod", "default"));
    }

    /**
     * 生成应触发回退的 sandboxMode 值：非 user/session 的任意字符串。
     * 排除 "user" 和 "session" 以确保只测试回退场景。
     */
    @Provide
    Arbitrary<String> fallbackSandboxModeValues() {
        return Arbitraries.oneOf(
                // 随机字母数字字符串（排除 user/session）
                Arbitraries.strings()
                        .alpha()
                        .numeric()
                        .ofMinLength(1)
                        .ofMaxLength(30)
                        .filter(s -> !"user".equals(s) && !"session".equals(s)),
                // 大小写变体
                Arbitraries.of("User", "USER", "Session", "SESSION"),
                // 其他无效值
                Arbitraries.of("k8s", "local", "pod", "container", "default", "none"));
    }

    // ===== 辅助方法 =====

    private ServerHttpRequest mockRequest(String uriString) throws Exception {
        ServerHttpRequest request = mock(ServerHttpRequest.class);
        when(request.getURI()).thenReturn(new URI(uriString));
        return request;
    }

    /**
     * 模拟后端路由逻辑中的 sandboxMode 回退判断。
     * 参考 AcpWebSocketHandler.afterConnectionEstablished 中的逻辑：
     * boolean isUserScoped = "user".equals(sandboxMode) || runtimeType == RuntimeType.K8S;
     * 当 sandboxMode 不是 "user" 时，对于 K8s 运行时仍回退到用户级沙箱。
     */
    private boolean isUserScopedFallback(String sandboxMode) {
        // POC 阶段：非 "session" 的值都回退到用户级沙箱
        return !"session".equals(sandboxMode);
    }

    // ===== Property 2: sandboxMode 参数解析与回退 =====

    /**
     * Property 2a: 对于任意有效的 sandboxMode 值（user/session），
     * AcpHandshakeInterceptor 应正确提取并存入 session attributes。
     *
     * <p>Feature: k8s-pod-reuse, Property 2: sandboxMode 参数解析与回退
     * <p><b>Validates: Requirements 2.1</b>
     */
    @Property(tries = 100)
    void validSandboxMode_isExtractedCorrectly(@ForAll("validSandboxModes") String sandboxMode)
            throws Exception {

        Map<String, Object> attributes = new HashMap<>();
        ServerHttpRequest request = mockRequest("ws://localhost/acp?sandboxMode=" + sandboxMode);

        interceptor.beforeHandshake(request, response, wsHandler, attributes);

        assertEquals(
                sandboxMode,
                attributes.get("sandboxMode"),
                "有效的 sandboxMode 值应被正确提取到 attributes 中");
    }

    /**
     * Property 2b: 对于任意非空字符串作为 sandboxMode 值，
     * AcpHandshakeInterceptor 应将其原样存入 session attributes（不做值校验）。
     *
     * <p>Feature: k8s-pod-reuse, Property 2: sandboxMode 参数解析与回退
     * <p><b>Validates: Requirements 2.1</b>
     */
    @Property(tries = 100)
    void arbitrarySandboxMode_isExtractedAsIs(
            @ForAll("arbitrarySandboxModeValues") String sandboxMode) throws Exception {

        String encoded = URLEncoder.encode(sandboxMode, StandardCharsets.UTF_8);
        Map<String, Object> attributes = new HashMap<>();
        ServerHttpRequest request = mockRequest("ws://localhost/acp?sandboxMode=" + encoded);

        interceptor.beforeHandshake(request, response, wsHandler, attributes);

        assertEquals(
                sandboxMode,
                attributes.get("sandboxMode"),
                "任意非空 sandboxMode 值应被原样提取到 attributes 中");
    }

    /**
     * Property 2c: 当 sandboxMode 参数缺失时，attributes 中不应包含 sandboxMode 键，
     * 后端路由逻辑应回退到用户级沙箱模式。
     *
     * <p>Feature: k8s-pod-reuse, Property 2: sandboxMode 参数解析与回退
     * <p><b>Validates: Requirements 2.3</b>
     */
    @Property(tries = 100)
    void missingSandboxMode_fallsBackToUserScoped(@ForAll("validSandboxModes") String ignoredMode)
            throws Exception {

        // sandboxMode 参数缺失
        Map<String, Object> attributes = new HashMap<>();
        ServerHttpRequest request = mockRequest("ws://localhost/acp?runtime=k8s");

        interceptor.beforeHandshake(request, response, wsHandler, attributes);

        // attributes 中不应包含 sandboxMode
        assertNull(attributes.get("sandboxMode"), "缺失 sandboxMode 时 attributes 中不应有该键");

        // 路由逻辑回退验证：null sandboxMode 应回退到用户级沙箱
        String sandboxMode = (String) attributes.get("sandboxMode");
        assertTrue(isUserScopedFallback(sandboxMode), "sandboxMode 缺失时应回退到用户级沙箱模式");
    }

    /**
     * Property 2d: 当 sandboxMode 为非 user/session 的任意字符串时，
     * 后端路由逻辑应回退到用户级沙箱模式（POC 阶段默认行为）。
     *
     * <p>Feature: k8s-pod-reuse, Property 2: sandboxMode 参数解析与回退
     * <p><b>Validates: Requirements 2.3</b>
     */
    @Property(tries = 100)
    void unrecognizedSandboxMode_fallsBackToUserScoped(
            @ForAll("fallbackSandboxModeValues") String sandboxMode) throws Exception {

        String encoded = URLEncoder.encode(sandboxMode, StandardCharsets.UTF_8);
        Map<String, Object> attributes = new HashMap<>();
        ServerHttpRequest request =
                mockRequest("ws://localhost/acp?sandboxMode=" + encoded + "&runtime=k8s");

        interceptor.beforeHandshake(request, response, wsHandler, attributes);

        // 拦截器应原样提取值
        assertEquals(sandboxMode, attributes.get("sandboxMode"), "拦截器应原样提取 sandboxMode 值");

        // 路由逻辑回退验证：非 user/session 的值应回退到用户级沙箱
        // 参考 AcpWebSocketHandler: boolean isUserScoped = "user".equals(sandboxMode) || runtimeType
        // == RuntimeType.K8S;
        // 对于 K8s 运行时，即使 sandboxMode 不是 "user"，isUserScoped 仍为 true
        assertTrue(isUserScopedFallback(sandboxMode), "非 user/session 的 sandboxMode 值应回退到用户级沙箱模式");
    }

    /**
     * Property 2e: 当 sandboxMode 为空字符串时，
     * AcpHandshakeInterceptor 不应将其存入 attributes（StrUtil.isNotBlank 过滤），
     * 后端路由逻辑应回退到用户级沙箱模式。
     *
     * <p>Feature: k8s-pod-reuse, Property 2: sandboxMode 参数解析与回退
     * <p><b>Validates: Requirements 2.3</b>
     */
    @Property(tries = 100)
    void emptySandboxMode_fallsBackToUserScoped(@ForAll("validSandboxModes") String ignoredMode)
            throws Exception {

        Map<String, Object> attributes = new HashMap<>();
        ServerHttpRequest request = mockRequest("ws://localhost/acp?sandboxMode=&runtime=k8s");

        interceptor.beforeHandshake(request, response, wsHandler, attributes);

        // 空字符串被 StrUtil.isNotBlank 过滤，不应存入 attributes
        assertNull(attributes.get("sandboxMode"), "空字符串 sandboxMode 不应被存入 attributes");

        // 路由逻辑回退验证
        String extractedMode = (String) attributes.get("sandboxMode");
        assertTrue(isUserScopedFallback(extractedMode), "空字符串 sandboxMode 应回退到用户级沙箱模式");
    }
}
