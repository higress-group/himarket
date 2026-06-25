package com.alibaba.himarket.dto.params.mcp;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Parameters for saving an MCP Server endpoint.
 */
@Data
public class SaveMcpEndpointParam {

    @NotBlank(message = "MCP Server ID is required")
    private String mcpServerId;

    @NotBlank(message = "Endpoint URL is required")
    private String endpointUrl;

    @NotBlank(message = "Hosting type is required")
    private String hostingType;

    @NotBlank(message = "Connection protocol is required")
    private String protocol;

    /**
     * User ID. The * value means visible to all users.
     */
    private String userId;

    /**
     * Hosting provider instance ID.
     */
    private String hostingInstanceId;

    /**
     * Hosting provider identifier.
     */
    private String hostingIdentifier;
}
