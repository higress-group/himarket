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
}
