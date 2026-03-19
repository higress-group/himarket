package com.alibaba.himarket.service.mcp;

import cn.hutool.core.util.StrUtil;

/**
 * MCP 协议类型标准化工具。
 *
 * <p>标准值只有三种：{@code stdio}、{@code sse}、{@code streamableHttp}。
 * 兼容外部系统传入的各种写法：streamable-http、StreamableHTTP、HTTP、Stdio 等。
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
        String lower = raw.trim().toLowerCase();
        if (lower.equals("stdio")) return "stdio";
        if (lower.equals("sse")) return "sse";
        if (lower.contains("http")) return "streamableHttp";
        return raw.trim();
    }
}
