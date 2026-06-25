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
 * Sandbox Sidecar HTTP client.
 *
 * <p>Wraps Sidecar HTTP API calls for writeFile, readFile, healthCheck, and exec, removing
 * duplicated code from provider implementations.
 *
 * <h3>Reusability Notes</h3>
 *
 * <p>This component is designed to be reused across providers. OpenSandbox execd exposes
 * {@code /files/*} HTTP APIs compatible with the HiMarket Sidecar, including
 * {@code /files/write}, {@code /files/read}, and {@code /files/extract}. A future
 * {@code OpenSandboxProvider} can inject this client directly for file operations.
 *
 * <p>The only difference is how baseUrl is constructed:
 *
 * <ul>
 *   <li>HiMarket Sidecar: {@code http://<pod-ip>:<sidecar-port>}</li>
 *   <li>OpenSandbox execd: {@code http://<sandbox-host>:<execd-port>}
 *       (default port 8080, discoverable through the OpenSandbox Server API)</li>
 * </ul>
 *
 * @see RemoteSandboxProvider
 * @see LocalSandboxProvider
 * @see SandboxProvider OpenSandbox integration notes
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
     * Writes a file to the sandbox.
     *
     * <p>Calls Sidecar {@code POST /files/write} with request body
     * {@code {"path": relativePath, "content": content}}.
     *
     * @param baseUrl Sidecar base URL, such as http://host:port
     * @param sandboxId sandbox identifier for error messages
     * @param relativePath relative file path
     * @param content file content
     * @throws IOException if the HTTP response is not 200 or the request fails
     */
    public void writeFile(String baseUrl, String sandboxId, String relativePath, String content)
            throws IOException {
        String url = baseUrl + "/files/write";
        String body =
                objectMapper.writeValueAsString(Map.of("path", relativePath, "content", content));
        HttpResponse<String> response = doPost(url, body, DEFAULT_TIMEOUT);
        if (response.statusCode() != 200) {
            throw new IOException(
                    "Sidecar writeFile failed (sandbox: " + sandboxId + "): " + response.body());
        }
    }

    /**
     * Reads a file from the sandbox.
     *
     * <p>Calls Sidecar {@code POST /files/read} with request body {@code {"path": relativePath}}
     * and parses the {@code content} field from the JSON response.
     *
     * @param baseUrl Sidecar base URL
     * @param sandboxId sandbox identifier for error messages
     * @param relativePath relative file path
     * @return file content
     * @throws IOException if the HTTP response is not 200 or the request fails
     */
    public String readFile(String baseUrl, String sandboxId, String relativePath)
            throws IOException {
        String url = baseUrl + "/files/read";
        String body = objectMapper.writeValueAsString(Map.of("path", relativePath));
        HttpResponse<String> response = doPost(url, body, DEFAULT_TIMEOUT);
        if (response.statusCode() != 200) {
            throw new IOException(
                    "Sidecar readFile failed (sandbox: " + sandboxId + "): " + response.body());
        }
        return objectMapper.readTree(response.body()).get("content").asText();
    }

    /**
     * Performs a health check.
     *
     * <p>Calls Sidecar {@code GET /health} with a 5-second timeout. Any exception returns false.
     *
     * @param baseUrl Sidecar base URL
     * @return true if healthy, false if unreachable or failed
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
     * Performs a health check with diagnostic logging.
     *
     * <p>Uses the same logic as {@link #healthCheck(String)}, but logs sandboxId, host, status,
     * body, and other diagnostics on failure.
     *
     * @param baseUrl Sidecar base URL
     * @param sandboxId sandbox identifier for logs
     * @return true if healthy, false if unreachable or failed
     */
    public boolean healthCheckWithLog(String baseUrl, String sandboxId) {
        String url = baseUrl + "/health";
        try {
            HttpResponse<String> response = doGet(url, HEALTH_CHECK_TIMEOUT);
            if (response.statusCode() == 200) {
                return true;
            }
            logger.warn(
                    "Sandbox health check returned non-OK status, sandboxId={}, host={},"
                            + " status={}, body={}",
                    sandboxId,
                    URI.create(baseUrl).getHost(),
                    response.statusCode(),
                    response.body());
            return false;
        } catch (Exception e) {
            logger.warn(
                    "Sandbox health check request failed, sandboxId={}, host={}, errorType={},"
                            + " errorMessage={}",
                    sandboxId,
                    URI.create(baseUrl).getHost(),
                    e.getClass().getSimpleName(),
                    e.getMessage());
            return false;
        }
    }

    /**
     * Executes a command inside the sandbox.
     *
     * <p>Calls Sidecar {@code POST /exec} with request body
     * {@code {"command": command, "args": args}}. The caller-provided timeout is used as the HTTP
     * request timeout.
     *
     * @param baseUrl Sidecar base URL
     * @param sandboxId sandbox identifier for error messages
     * @param command command to execute
     * @param args command argument list
     * @param timeout HTTP request timeout
     * @return command execution result
     * @throws IOException if the HTTP response is not 200 or the request fails
     */
    public ExecResult exec(
            String baseUrl, String sandboxId, String command, List<String> args, Duration timeout)
            throws IOException {
        String url = baseUrl + "/exec";
        String body = objectMapper.writeValueAsString(Map.of("command", command, "args", args));
        HttpResponse<String> response = doPost(url, body, timeout);
        if (response.statusCode() != 200) {
            throw new IOException(
                    "Sidecar exec failed (sandbox: "
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

    /**
     * Checks whether the specified session exists in the Sidecar.
     *
     * <p>Calls Sidecar {@code GET /sessions/{sessionId}}. Any exception, including connection
     * timeouts and network errors, is treated as a missing session.
     *
     * @param baseUrl Sidecar base URL
     * @param sessionId Sidecar session ID
     * @return true if the session exists, false if missing or the request fails
     */
    public boolean sessionExists(String baseUrl, String sessionId) {
        try {
            String url = baseUrl + "/sessions/" + sessionId;
            HttpResponse<String> response = doGet(url, HEALTH_CHECK_TIMEOUT);
            return response.statusCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Sends a POST request with a JSON body.
     *
     * <p>Handles {@link InterruptedException} by restoring the interrupt flag and wrapping it in
     * {@link IOException}.
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
            throw new IOException("HTTP POST request interrupted: " + url, e);
        }
    }

    /**
     * Sends a GET request.
     *
     * <p>Handles {@link InterruptedException} by restoring the interrupt flag and wrapping it in
     * {@link IOException}.
     */
    private HttpResponse<String> doGet(String url, Duration timeout) throws IOException {
        try {
            return httpClient.send(
                    HttpRequest.newBuilder(URI.create(url)).GET().timeout(timeout).build(),
                    HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("HTTP GET request interrupted: " + url, e);
        }
    }
}
