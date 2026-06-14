package com.alibaba.himarket.service.mcp;

import cn.hutool.core.util.StrUtil;
import com.alibaba.himarket.support.enums.McpProtocolType;

/**
 * Utility for normalizing MCP protocol types.
 *
 * <p>The standard values are {@code stdio}, {@code sse}, {@code streamableHttp}, and {@code
 * dualHttp}. Inputs from external systems may use variants such as streamable-http,
 * StreamableHTTP, HTTP, Stdio, or dualHttp.
 *
 * <p>Normalization is delegated to {@link McpProtocolType}; this class remains as the static helper
 * entry point.
 */
public final class McpProtocolUtils {

    private McpProtocolUtils() {}

    /**
     * Normalizes a protocol type.
     *
     * @param raw raw protocol type
     * @return normalized protocol type; blank values are returned as-is, unknown values are trimmed
     */
    public static String normalize(String raw) {
        if (StrUtil.isBlank(raw)) return raw;
        return McpProtocolType.normalize(raw);
    }

    /**
     * Parses a protocol type into an enum, returning null when unknown.
     */
    public static McpProtocolType parse(String raw) {
        return McpProtocolType.fromString(raw);
    }

    /**
     * Returns whether the protocol is stdio.
     */
    public static boolean isStdio(String raw) {
        McpProtocolType type = McpProtocolType.fromString(raw);
        return type != null && type.isStdio();
    }

    /**
     * Returns whether the protocol is StreamableHTTP, including DUAL_HTTP.
     */
    public static boolean isStreamableHttp(String raw) {
        McpProtocolType type = McpProtocolType.fromString(raw);
        return type != null && type.isStreamableHttp();
    }

    /**
     * Returns whether the protocol supports both SSE and StreamableHTTP.
     */
    public static boolean isDualHttp(String raw) {
        McpProtocolType type = McpProtocolType.fromString(raw);
        return type != null && type.isDualHttp();
    }

    /**
     * Normalizes an SSE endpoint URL by trimming trailing slashes and appending {@code /sse} for
     * non-StreamableHTTP protocols.
     *
     * <p>Rules:
     * <ul>
     *   <li>StreamableHTTP: return the URL after trimming trailing slashes</li>
     *   <li>SSE or unknown protocols: ensure the URL ends with one {@code /sse}</li>
     *   <li>Blank URL: return as-is</li>
     * </ul>
     *
     * @param url raw endpoint URL
     * @param protocol protocol type string; null is treated as SSE
     * @return normalized URL
     */
    public static String normalizeEndpointUrl(String url, String protocol) {
        if (StrUtil.isBlank(url)) return url;
        String normalized = url.replaceAll("/+$", "");
        McpProtocolType type = McpProtocolType.fromString(protocol);
        if (type != null && type.isStreamableHttp()) {
            return normalized;
        }
        if (!normalized.endsWith("/sse")) {
            normalized = normalized + "/sse";
        }
        return normalized;
    }
}
