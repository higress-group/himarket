package com.alibaba.himarket.service.hicoding.sandbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 沙箱 Sidecar HTTP 客户端。
 *
 * <p>统一封装对 Sidecar HTTP API 的调用（writeFile / readFile / healthCheck / exec），
 * 消除 RemoteSandboxProvider 和 LocalSandboxProvider 中的代码重复。
 *
 * <h3>可复用性说明</h3>
 * <p>本组件设计为跨 Provider 可复用。OpenSandbox 的 execd 组件提供与 HiMarket Sidecar
 * 兼容的 /files/* HTTP API（/files/write、/files/read、/files/extract），
 * 因此未来 {@code OpenSandboxProvider} 可直接注入本客户端完成文件操作，
 * 无需重复实现 HTTP 调用逻辑。
 *
 * <p>唯一差异在于 baseUrl 的构造方式：
 * <ul>
 *   <li>HiMarket Sidecar：{@code http://<pod-ip>:<sidecar-port>}</li>
 *   <li>OpenSandbox execd：{@code http://<sandbox-host>:<execd-port>}
 *       （端口默认 8080，可通过 OpenSandbox Server API 获取）</li>
 * </ul>
 *
 * @see RemoteSandboxProvider
 * @see LocalSandboxProvider
 * @see SandboxProvider OpenSandbox 对接说明
 */
@Component
public class SandboxHttpClient {

    private static final Logger logger = LoggerFactory.getLogger(SandboxHttpClient.class);
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration HEALTH_CHECK_TIMEOUT = Duration.ofSeconds(5);
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public SandboxHttpClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient =
                HttpClient.newBuilder()
                        .connectTimeout(DEFAULT_TIMEOUT)
                        .version(HttpClient.Version.HTTP_1_1)
                        .build();
    }

    /**
     * 写入文件到沙箱。
     *
     * <p>调用 Sidecar POST /files/write，请求体为 {@code {"path": relativePath, "content": content}}。
     *
     * @param baseUrl      Sidecar 基础 URL（如 http://host:port）
     * @param sandboxId    沙箱标识（用于异常信息）
     * @param relativePath 文件相对路径
     * @param content      文件内容
     * @throws IOException 当 HTTP 响应非 200 或请求失败时
     */
    public void writeFile(String baseUrl, String sandboxId, String relativePath, String content)
            throws IOException {
        String url = baseUrl + "/files/write";
        String body =
                objectMapper.writeValueAsString(Map.of("path", relativePath, "content", content));
        HttpResponse<String> response = doPost(url, body, DEFAULT_TIMEOUT);
        if (response.statusCode() != 200) {
            throw new IOException(
                    "Sidecar writeFile 失败 (sandbox: " + sandboxId + "): " + response.body());
        }
    }

    /**
     * 从沙箱读取文件。
     *
     * <p>调用 Sidecar POST /files/read，请求体为 {@code {"path": relativePath}}，
     * 从响应 JSON 中解析 {@code content} 字段。
     *
     * @param baseUrl      Sidecar 基础 URL
     * @param sandboxId    沙箱标识（用于异常信息）
     * @param relativePath 文件相对路径
     * @return 文件内容
     * @throws IOException 当 HTTP 响应非 200 或请求失败时
     */
    public String readFile(String baseUrl, String sandboxId, String relativePath)
            throws IOException {
        String url = baseUrl + "/files/read";
        String body = objectMapper.writeValueAsString(Map.of("path", relativePath));
        HttpResponse<String> response = doPost(url, body, DEFAULT_TIMEOUT);
        if (response.statusCode() != 200) {
            throw new IOException(
                    "Sidecar readFile 失败 (sandbox: " + sandboxId + "): " + response.body());
        }
        return objectMapper.readTree(response.body()).get("content").asText();
    }

    /**
     * 健康检查。
     *
     * <p>调用 Sidecar GET /health，超时 5 秒。任何异常均返回 false。
     *
     * @param baseUrl Sidecar 基础 URL
     * @return true 表示健康，false 表示不可达或异常
     */
    public boolean healthCheck(String baseUrl) {
        try {
            HttpResponse<String> response = doGet(baseUrl + "/health", HEALTH_CHECK_TIMEOUT);
            return response.statusCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 带日志的健康检查。
     *
     * <p>与 {@link #healthCheck(String)} 相同逻辑，但在失败时记录 warn 日志，
     * 包含 sandboxId、host、status、body 等诊断信息。
     *
     * @param baseUrl   Sidecar 基础 URL
     * @param sandboxId 沙箱标识（用于日志）
     * @return true 表示健康，false 表示不可达或异常
     */
    public boolean healthCheckWithLog(String baseUrl, String sandboxId) {
        String url = baseUrl + "/health";
        try {
            HttpResponse<String> response = doGet(url, HEALTH_CHECK_TIMEOUT);
            if (response.statusCode() == 200) {
                return true;
            }
            logger.warn(
                    "[SandboxHttpClient] healthCheck HTTP 非 200 (sandbox: {}, host: {}, status: {},"
                            + " body: {})",
                    sandboxId,
                    URI.create(baseUrl).getHost(),
                    response.statusCode(),
                    response.body());
            return false;
        } catch (Exception e) {
            logger.warn(
                    "[SandboxHttpClient] healthCheck HTTP 请求失败 (sandbox: {}, host: {}): {} - {}",
                    sandboxId,
                    URI.create(baseUrl).getHost(),
                    e.getClass().getSimpleName(),
                    e.getMessage() != null ? e.getMessage() : e.toString());
            return false;
        }
    }

    /**
     * 在沙箱内执行命令。
     *
     * <p>调用 Sidecar POST /exec，请求体为 {"command": command, "args": args}。
     * 使用调用方传入的 timeout 作为 HTTP 请求超时时间。
     *
     * @param baseUrl   Sidecar 基础 URL
     * @param sandboxId 沙箱标识（用于异常信息）
     * @param command   要执行的命令
     * @param args      命令参数列表
     * @param timeout   HTTP 请求超时时间
     * @return 命令执行结果
     * @throws IOException 当 HTTP 响应非 200 或请求失败时
     */
    public ExecResult exec(
            String baseUrl, String sandboxId, String command, List<String> args, Duration timeout)
            throws IOException {
        String url = baseUrl + "/exec";
        String body = objectMapper.writeValueAsString(Map.of("command", command, "args", args));
        HttpResponse<String> response = doPost(url, body, timeout);
        if (response.statusCode() != 200) {
            throw new IOException(
                    "Sidecar exec 失败 (sandbox: "
                            + sandboxId
                            + ", status: "
                            + response.statusCode()
                            + "): "
                            + response.body());
        }
        var tree = objectMapper.readTree(response.body());
        return new ExecResult(
                tree.get("exitCode").asInt(),
                tree.get("stdout").asText(),
                tree.get("stderr").asText());
    }

    // ===== 内部 HTTP 调用方法 =====

    /**
     * 发送 POST 请求（JSON body）。
     * 统一处理 InterruptedException：恢复中断标志 + 包装为 IOException。
     */
    private HttpResponse<String> doPost(String url, String body, Duration timeout)
            throws IOException {
        try {
            return httpClient.send(
                    HttpRequest.newBuilder(URI.create(url))
                            .POST(HttpRequest.BodyPublishers.ofString(body))
                            .header("Content-Type", "application/json")
                            .timeout(timeout)
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("HTTP 请求被中断: " + url, e);
        }
    }

    /**
     * 发送 GET 请求。
     * 统一处理 InterruptedException：恢复中断标志 + 包装为 IOException。
     */
    private HttpResponse<String> doGet(String url, Duration timeout) throws IOException {
        try {
            return httpClient.send(
                    HttpRequest.newBuilder(URI.create(url)).GET().timeout(timeout).build(),
                    HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("HTTP 请求被中断: " + url, e);
        }
    }
}
