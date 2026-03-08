package com.alibaba.himarket.service.acp.runtime;

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
