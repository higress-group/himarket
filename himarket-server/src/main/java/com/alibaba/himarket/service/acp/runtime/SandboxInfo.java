package com.alibaba.himarket.service.acp.runtime;

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

    /**
     * 构建 Sidecar WebSocket URI。
     *
     * @param command 要执行的命令
     * @param args    命令参数（可为 null 或空白）
     * @return 完整的 WebSocket URI，args 部分经过 URL 编码
     */
    public URI sidecarWsUri(String command, String args) {
        String query = "command=" + command;
        if (args != null && !args.isBlank()) {
            query += "&args=" + URLEncoder.encode(args, StandardCharsets.UTF_8);
        }
        return URI.create("ws://" + host + ":" + sidecarPort + "/?" + query);
    }
}
