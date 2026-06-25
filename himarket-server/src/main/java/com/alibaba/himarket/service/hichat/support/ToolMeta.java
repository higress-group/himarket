package com.alibaba.himarket.service.hichat.support;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
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
