package com.alibaba.himarket.dto.result.mcp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * MCP remote connection result.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class McpConnectResult {

    /**
     * Generated MCP connection config JSON.
     */
    private String configJson;

    /**
     * Sandbox endpoint URL.
     */
    private String endpointUrl;

    /**
     * Transport type, such as sse or http.
     */
    private String transportType;
}
