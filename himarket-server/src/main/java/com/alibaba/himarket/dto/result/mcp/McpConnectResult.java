package com.alibaba.himarket.dto.result.mcp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * MCP 远程连接返回结果。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class McpConnectResult {

    /** 生成的 MCP 连接配置 JSON */
    private String configJson;

    /** 沙箱 endpoint URL */
    private String endpointUrl;

    /** 传输类型：sse / http */
    private String transportType;
}
