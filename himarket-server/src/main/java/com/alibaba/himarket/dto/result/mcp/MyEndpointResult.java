package com.alibaba.himarket.dto.result.mcp;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * "My MCP" list item composed from endpoint and metadata.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MyEndpointResult {

    // Endpoint fields.
    private String endpointId;
    private String mcpServerId;
    private String endpointUrl;
    private String hostingType;
    private String protocol;
    private String hostingInstanceId;
    private String subscribeParams;
    private String status;
    private LocalDateTime endpointCreatedAt;

    // Metadata display fields.
    private String productId;
    private String displayName;
    private String mcpName;
    private String description;
    private String icon;
    private String tags;
    private String protocolType;
    private String origin;
    private String toolsConfig;
}
