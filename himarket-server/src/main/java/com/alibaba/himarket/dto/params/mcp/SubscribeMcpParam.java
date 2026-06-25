package com.alibaba.himarket.dto.params.mcp;

import lombok.Data;

/**
 * MCP subscription request parameters.
 *
 * <p>Direct SSE/HTTP connections do not require parameters.
 *
 * <p>Remote sandbox connections require sandboxId and transportType, with optional params.
 */
@Data
public class SubscribeMcpParam {

    /**
     * Sandbox ID, required for remote scenarios.
     */
    private String sandboxId;

    /**
     * Transport type, such as sse or http, required for remote scenarios.
     */
    private String transportType;

    /**
     * Authentication type for remote scenarios, such as none or bearer.
     */
    private String authType;

    /**
     * Optional user-provided extra parameters as JSON.
     */
    private String params;
}
