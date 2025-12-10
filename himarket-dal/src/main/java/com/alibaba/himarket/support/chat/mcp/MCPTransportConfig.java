package com.alibaba.himarket.support.chat.mcp;

import com.alibaba.himarket.support.enums.MCPTransportMode;
import lombok.Builder;
import lombok.Data;

/**
 * @author zh
 */
@Data
@Builder
public class MCPTransportConfig {

    private String mcpServerName;

    private MCPTransportMode transportMode;

    private String url;
}

