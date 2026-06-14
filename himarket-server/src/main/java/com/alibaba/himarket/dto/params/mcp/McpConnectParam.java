package com.alibaba.himarket.dto.params.mcp;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * MCP remote connection request parameters.
 */
@Data
public class McpConnectParam {

    @NotBlank(message = "Sandbox ID is required")
    private String sandboxId;

    @NotBlank(message = "Transport type is required")
    private String transportType;

    /**
     * Optional user-provided parameters as JSON.
     */
    private String params;
}
