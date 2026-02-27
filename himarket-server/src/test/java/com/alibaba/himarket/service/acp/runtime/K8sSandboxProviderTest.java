package com.alibaba.himarket.service.acp.runtime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.lang.reflect.Field;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * K8sSandboxProvider 单元测试。
 *
 * <p>使用 mock PodReuseManager、K8sConfigService 和 HttpClient 测试核心逻辑。
 *
 * <p>Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 3.6, 3.7
 */
class K8sSandboxProviderTest {

    private PodReuseManager mockPodReuseManager;
    private K8sConfigService mockK8sConfigService;
    private HttpClient mockHttpClient;
    private K8sSandboxProvider provider;

    @BeforeEach
    void setUp() throws Exception {
        mockPodReuseManager = mock(PodReuseManager.class);
        when(mockPodReuseManager.getNamespace()).thenReturn("himarket");
        mockK8sConfigService = mock(K8sConfigService.class);
        mockHttpClient = mock(HttpClient.class);

        provider = new K8sSandboxProvider(mockPodReuseManager, mockK8sConfigService);

        // 通过反射注入 mock HttpClient
        Field httpClientField = K8sSandboxProvider.class.getDeclaredField("httpClient");
        httpClientField.setAccessible(true);
        httpClientField.set(provider, mockHttpClient);
    }

    // ===== getType =====

    @Test
    void getType_returnsK8S() {
        assertEquals(SandboxType.K8S, provider.getType());
    }

    // ===== acquire: 构建正确的 SandboxInfo (Requirement 3.1) =====

    @Test
    void acquire_withServiceIp_buildsSandboxInfoWithServiceIpAsHost() {
        PodInfo podInfo = new PodInfo("pod-abc", "10.0.0.1", "172.16.0.1", null, false);
        when(mockPodReuseManager.acquirePod(eq("user1"), any(RuntimeConfig.class)))
                .thenReturn(podInfo);

        SandboxConfig config =
                new SandboxConfig(
                        "user1", SandboxType.K8S, "/workspace", Map.of(), "config1", null, null, 0);

        SandboxInfo info = provider.acquire(config);

        assertEquals(SandboxType.K8S, info.type());
        assertEquals("pod-abc", info.sandboxId());
        assertEquals("172.16.0.1", info.host()); // serviceIp 优先
        assertEquals(8080, info.sidecarPort());
        assertEquals("/workspace", info.workspacePath());
        assertFalse(info.reused());
        assertEquals("pod-abc", info.metadata().get("podName"));
        assertEquals("himarket", info.metadata().get("namespace"));
        assertEquals("10.0.0.1", info.metadata().get("podIp"));
    }

    @Test
    void acquire_withoutServiceIp_fallsToPodIp() {
        PodInfo podInfo = new PodInfo("pod-xyz", "10.0.0.2", null, null, true);
        when(mockPodReuseManager.acquirePod(eq("user2"), any(RuntimeConfig.class)))
                .thenReturn(podInfo);

        SandboxConfig config =
                new SandboxConfig(
                        "user2", SandboxType.K8S, "/workspace", Map.of(), "config1", null, null, 0);

        SandboxInfo info = provider.acquire(config);

        assertEquals("10.0.0.2", info.host()); // 回退到 podIp
        assertTrue(info.reused());
    }

    @Test
    void acquire_withBlankServiceIp_fallsToPodIp() {
        PodInfo podInfo = new PodInfo("pod-blank", "10.0.0.3", "  ", null, false);
        when(mockPodReuseManager.acquirePod(eq("user3"), any(RuntimeConfig.class)))
                .thenReturn(podInfo);

        SandboxConfig config =
                new SandboxConfig(
                        "user3", SandboxType.K8S, "/workspace", null, "config1", null, null, 0);

        SandboxInfo info = provider.acquire(config);

        assertEquals("10.0.0.3", info.host()); // 空白 serviceIp 回退到 podIp
    }

    @Test
    void acquire_passesCorrectRuntimeConfigToPodReuseManager() {
        PodInfo podInfo = new PodInfo("pod-1", "10.0.0.1", null, null, false);
        when(mockPodReuseManager.acquirePod(eq("user1"), any(RuntimeConfig.class)))
                .thenReturn(podInfo);

        Map<String, String> env = Map.of("KEY", "VALUE");
        SandboxConfig config =
                new SandboxConfig(
                        "user1", SandboxType.K8S, "/my/workspace", env, "my-config", null, null, 0);

        provider.acquire(config);

        verify(mockPodReuseManager)
                .acquirePod(
                        eq("user1"),
                        argThat(
                                rc ->
                                        "user1".equals(rc.getUserId())
                                                && "my-config".equals(rc.getK8sConfigId())
                                                && "/my/workspace".equals(rc.getCwd())
                                                && env.equals(rc.getEnv())));
    }

    @Test
    void acquire_withNullPodIp_metadataContainsEmptyString() {
        PodInfo podInfo = new PodInfo("pod-null-ip", null, "172.16.0.1", null, false);
        when(mockPodReuseManager.acquirePod(anyString(), any(RuntimeConfig.class)))
                .thenReturn(podInfo);

        SandboxConfig config =
                new SandboxConfig(
                        "user1", SandboxType.K8S, "/workspace", Map.of(), "config1", null, null, 0);

        SandboxInfo info = provider.acquire(config);

        assertEquals("", info.metadata().get("podIp"));
    }

    // ===== release (Requirement 3.6) =====

    @Test
    void release_delegatesToPodReuseManager() {
        SandboxInfo info = k8sSandboxInfo("pod-release", "10.0.0.1");

        provider.release(info);

        verify(mockPodReuseManager).releasePod("pod-release");
    }

    // ===== writeFile: 通过 HTTP API (Requirement 3.2) =====

    @SuppressWarnings("unchecked")
    @Test
    void writeFile_sendsCorrectHttpRequest() throws Exception {
        SandboxInfo info = k8sSandboxInfo("pod-write", "10.0.0.5");

        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(200);
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(mockResponse);

        provider.writeFile(info, "config/settings.json", "{\"key\":\"value\"}");

        verify(mockHttpClient)
                .send(
                        argThat(
                                req ->
                                        req.uri()
                                                        .toString()
                                                        .equals("http://10.0.0.5:8080/files/write")
                                                && "POST".equals(req.method())),
                        any(HttpResponse.BodyHandler.class));
    }

    @SuppressWarnings("unchecked")
    @Test
    void writeFile_nonOkStatus_throwsIOExceptionWithPodId() throws Exception {
        SandboxInfo info = k8sSandboxInfo("pod-fail", "10.0.0.6");

        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(500);
        when(mockResponse.body()).thenReturn("Internal Server Error");
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(mockResponse);

        IOException ex =
                assertThrows(
                        IOException.class, () -> provider.writeFile(info, "test.txt", "content"));

        assertTrue(ex.getMessage().contains("pod-fail"));
    }

    @SuppressWarnings("unchecked")
    @Test
    void writeFile_interrupted_throwsIOExceptionAndRestoresInterrupt() throws Exception {
        SandboxInfo info = k8sSandboxInfo("pod-int", "10.0.0.7");

        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new InterruptedException("interrupted"));

        IOException ex =
                assertThrows(
                        IOException.class, () -> provider.writeFile(info, "test.txt", "content"));

        assertTrue(ex.getMessage().contains("pod-int"));
        assertTrue(Thread.currentThread().isInterrupted());
        // 清除中断标志以免影响其他测试
        Thread.interrupted();
    }

    // ===== readFile: 通过 HTTP API (Requirement 3.3) =====

    @SuppressWarnings("unchecked")
    @Test
    void readFile_returnsContentFromResponse() throws Exception {
        SandboxInfo info = k8sSandboxInfo("pod-read", "10.0.0.8");

        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.body()).thenReturn("{\"content\":\"hello world\"}");
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(mockResponse);

        String content = provider.readFile(info, "readme.txt");

        assertEquals("hello world", content);
        verify(mockHttpClient)
                .send(
                        argThat(
                                req ->
                                        req.uri()
                                                        .toString()
                                                        .equals("http://10.0.0.8:8080/files/read")
                                                && "POST".equals(req.method())),
                        any(HttpResponse.BodyHandler.class));
    }

    @SuppressWarnings("unchecked")
    @Test
    void readFile_nonOkStatus_throwsIOExceptionWithPodId() throws Exception {
        SandboxInfo info = k8sSandboxInfo("pod-read-fail", "10.0.0.9");

        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(404);
        when(mockResponse.body()).thenReturn("File not found");
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(mockResponse);

        IOException ex =
                assertThrows(IOException.class, () -> provider.readFile(info, "missing.txt"));

        assertTrue(ex.getMessage().contains("pod-read-fail"));
    }

    @SuppressWarnings("unchecked")
    @Test
    void readFile_interrupted_throwsIOExceptionAndRestoresInterrupt() throws Exception {
        SandboxInfo info = k8sSandboxInfo("pod-read-int", "10.0.0.10");

        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new InterruptedException("interrupted"));

        IOException ex = assertThrows(IOException.class, () -> provider.readFile(info, "test.txt"));

        assertTrue(ex.getMessage().contains("pod-read-int"));
        assertTrue(Thread.currentThread().isInterrupted());
        Thread.interrupted();
    }

    // ===== healthCheck: 通过 HTTP GET /health (Requirement 3.4) =====

    @SuppressWarnings("unchecked")
    @Test
    void healthCheck_okResponse_returnsTrue() throws Exception {
        SandboxInfo info = k8sSandboxInfo("pod-health", "10.0.0.11");

        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(200);
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(mockResponse);

        assertTrue(provider.healthCheck(info));
    }

    @SuppressWarnings("unchecked")
    @Test
    void healthCheck_nonOkResponse_returnsFalse() throws Exception {
        SandboxInfo info = k8sSandboxInfo("pod-health-fail", "10.0.0.12");

        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(503);
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(mockResponse);

        assertFalse(provider.healthCheck(info));
    }

    @SuppressWarnings("unchecked")
    @Test
    void healthCheck_exception_returnsFalse() throws Exception {
        SandboxInfo info = k8sSandboxInfo("pod-health-ex", "10.0.0.13");

        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new IOException("connection refused"));

        assertFalse(provider.healthCheck(info));
    }

    // ===== HTTP 超时返回 IOException (Requirement 3.7) =====

    @SuppressWarnings("unchecked")
    @Test
    void writeFile_httpTimeout_throwsIOException() throws Exception {
        SandboxInfo info = k8sSandboxInfo("pod-timeout", "10.0.0.14");

        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new HttpTimeoutException("request timed out"));

        IOException ex =
                assertThrows(IOException.class, () -> provider.writeFile(info, "test.txt", "data"));

        // HttpTimeoutException 是 IOException 的子类，直接抛出
        assertTrue(
                ex instanceof HttpTimeoutException
                        || ex.getCause() instanceof HttpTimeoutException);
    }

    @SuppressWarnings("unchecked")
    @Test
    void readFile_httpTimeout_throwsIOException() throws Exception {
        SandboxInfo info = k8sSandboxInfo("pod-timeout-read", "10.0.0.15");

        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new HttpTimeoutException("request timed out"));

        IOException ex = assertThrows(IOException.class, () -> provider.readFile(info, "test.txt"));

        assertTrue(
                ex instanceof HttpTimeoutException
                        || ex.getCause() instanceof HttpTimeoutException);
    }

    @SuppressWarnings("unchecked")
    @Test
    void healthCheck_httpTimeout_returnsFalse() throws Exception {
        SandboxInfo info = k8sSandboxInfo("pod-timeout-health", "10.0.0.16");

        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new HttpTimeoutException("request timed out"));

        assertFalse(provider.healthCheck(info));
    }

    // ===== getSidecarUri =====

    @Test
    void getSidecarUri_delegatesToSandboxInfo() {
        SandboxInfo info = k8sSandboxInfo("pod-uri", "10.0.0.20");

        URI uri = provider.getSidecarUri(info, "qodercli", "--verbose");

        assertTrue(uri.toString().startsWith("ws://10.0.0.20:8080/"));
        assertTrue(uri.toString().contains("command=qodercli"));
        assertTrue(uri.toString().contains("args="));
    }

    // ===== 辅助方法 =====

    private SandboxInfo k8sSandboxInfo(String podName, String host) {
        return new SandboxInfo(
                SandboxType.K8S,
                podName,
                host,
                8080,
                "/workspace",
                false,
                Map.of("podName", podName, "namespace", "himarket", "podIp", host));
    }
}
