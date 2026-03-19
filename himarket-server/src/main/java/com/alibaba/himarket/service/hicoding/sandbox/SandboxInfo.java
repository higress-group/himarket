package com.alibaba.himarket.service.hicoding.sandbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 沙箱实例信息，由 SandboxProvider.acquire() 返回。
 * 包含连接沙箱所需的所有信息。
 */
public record SandboxInfo(
        SandboxType type,
        String sandboxId,
        String host,
        int sidecarPort,
        String workspacePath,
        boolean reused,
        Map<String, String> metadata) {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * 构建 Sidecar WebSocket URI。
     *
     * @param command 要执行的命令
     * @param args    命令参数（可为 null 或空白）
     * @return 完整的 WebSocket URI，args 部分经过 URL 编码
     */
    public URI sidecarWsUri(String command, String args) {
        return sidecarWsUri(command, args, null);
    }

    /**
     * 构建 Sidecar WebSocket URI，支持传递环境变量和工作目录。
     *
     * @param command 要执行的命令
     * @param args    命令参数（可为 null 或空白）
     * @param env     环境变量 Map（可为 null）
     * @param cwd     工作目录路径（可为 null 或空白）
     * @return 完整的 WebSocket URI，args、env 和 cwd 部分经过 URL 编码
     */
    public URI sidecarWsUri(String command, String args, Map<String, String> env, String cwd) {
        return sidecarWsUri(command, args, env, cwd, null);
    }

    /**
     * 构建 Sidecar WebSocket URI，支持传递环境变量、工作目录和 sessionId。
     *
     * <p>当 sessionId 非空时，生成 attach URI（不含 command 等参数），
     * sidecar 将 attach 到已有 session 而非创建新进程。
     *
     * @param command   要执行的命令（新建时必需，attach 时忽略）
     * @param args      命令参数（可为 null 或空白）
     * @param env       环境变量 Map（可为 null）
     * @param cwd       工作目录路径（可为 null 或空白）
     * @param sessionId sidecar session ID（非空则为 attach 模式）
     * @return 完整的 WebSocket URI
     */
    public URI sidecarWsUri(
            String command, String args, Map<String, String> env, String cwd, String sessionId) {
        // Attach 模式：只需 sessionId
        if (sessionId != null && !sessionId.isBlank()) {
            return URI.create(
                    "ws://"
                            + host
                            + ":"
                            + sidecarPort
                            + "/?sessionId="
                            + URLEncoder.encode(sessionId, StandardCharsets.UTF_8));
        }
        // 新建模式：需要 command 等参数
        String query = "command=" + command;
        if (args != null && !args.isBlank()) {
            query += "&args=" + URLEncoder.encode(args, StandardCharsets.UTF_8);
        }
        if (env != null && !env.isEmpty()) {
            try {
                String envJson = OBJECT_MAPPER.writeValueAsString(env);
                query += "&env=" + URLEncoder.encode(envJson, StandardCharsets.UTF_8);
            } catch (JsonProcessingException e) {
                throw new RuntimeException("Failed to serialize env to JSON", e);
            }
        }
        if (cwd != null && !cwd.isBlank()) {
            query += "&cwd=" + URLEncoder.encode(cwd, StandardCharsets.UTF_8);
        }
        return URI.create("ws://" + host + ":" + sidecarPort + "/?" + query);
    }

    /**
     * 构建 Sidecar WebSocket URI，支持传递环境变量。
     *
     * @param command 要执行的命令
     * @param args    命令参数（可为 null 或空白）
     * @param env     环境变量 Map（可为 null）
     * @return 完整的 WebSocket URI，args 和 env 部分经过 URL 编码
     */
    public URI sidecarWsUri(String command, String args, Map<String, String> env) {
        return sidecarWsUri(command, args, env, null);
    }
}
