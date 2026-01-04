package com.alibaba.himarket.service.hichat.support;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ToolMeta {

    /**
     * MCP server name
     */
    private String mcpServerName;

    /**
     * Tool name
     */
    private String toolName;

    // Add other fields as needed
}
