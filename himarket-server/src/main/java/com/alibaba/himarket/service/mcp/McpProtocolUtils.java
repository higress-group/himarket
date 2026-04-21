package com.alibaba.himarket.service.mcp;

import cn.hutool.core.util.StrUtil;
import com.alibaba.himarket.support.enums.McpProtocolType;

/**
 * MCP 协议类型标准化工具。
 *
 * <p>标准值只有三种：{@code stdio}、{@code sse}、{@code streamableHttp}。
 * 兼容外部系统传入的各种写法：streamable-http、StreamableHTTP、HTTP、Stdio 等。
 *
 * <p>委托给 {@link McpProtocolType} 枚举实现，本类保留作为静态工具入口。
 */
public final class McpProtocolUtils {

    private McpProtocolUtils() {}

    /**
     * 标准化协议类型。
     *
     * @param raw 原始协议类型字符串
     * @return 标准化后的协议类型，空值原样返回，无法识别时 trim 后返回
     */
    public static String normalize(String raw) {
        if (StrUtil.isBlank(raw)) return raw;
        return McpProtocolType.normalize(raw);
    }

    /**
     * 解析为枚举，无法识别时返回 null。
     */
    public static McpProtocolType parse(String raw) {
        return McpProtocolType.fromString(raw);
    }

    /**
     * 判断是否为 stdio 协议。
     */
    public static boolean isStdio(String raw) {
        McpProtocolType type = McpProtocolType.fromString(raw);
        return type != null && type.isStdio();
    }

    /**
     * 判断是否为 StreamableHTTP 协议。
     */
    public static boolean isStreamableHttp(String raw) {
        McpProtocolType type = McpProtocolType.fromString(raw);
        return type != null && type.isStreamableHttp();
    }

    /**
     * 标准化 SSE endpoint URL：去掉尾部多余斜杠，非 StreamableHTTP 协议自动追加 {@code /sse} 后缀。
     *
     * <p>规则：
     * <ul>
     *   <li>StreamableHTTP 协议：原样返回（去掉尾部斜杠）</li>
     *   <li>SSE 或未知协议：确保以 {@code /sse} 结尾且不重复</li>
     *   <li>URL 为空时原样返回</li>
     * </ul>
     *
     * @param url      原始 endpoint URL
     * @param protocol 协议类型字符串（可为 null，null 视为 SSE）
     * @return 标准化后的 URL
     */
    public static String normalizeEndpointUrl(String url, String protocol) {
        if (StrUtil.isBlank(url)) return url;
        // 统一去掉尾部斜杠
        String normalized = url.replaceAll("/+$", "");
        McpProtocolType type = McpProtocolType.fromString(protocol);
        if (type != null && type.isStreamableHttp()) {
            return normalized;
        }
        // SSE 或未知协议：确保以 /sse 结尾
        if (!normalized.endsWith("/sse")) {
            normalized = normalized + "/sse";
        }
        return normalized;
    }
}
