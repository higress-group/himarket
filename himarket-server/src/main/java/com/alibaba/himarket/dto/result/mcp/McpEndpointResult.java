package com.alibaba.himarket.dto.result.mcp;

import com.alibaba.himarket.dto.converter.OutputConverter;
import com.alibaba.himarket.entity.McpServerEndpoint;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * MCP Server Endpoint 返回结果。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class McpEndpointResult implements OutputConverter<McpEndpointResult, McpServerEndpoint> {

    private String endpointId;
    private String mcpServerId;
    private String mcpName;
    private String endpointUrl;
    private String hostingType;
    private String protocol;
    private String userId;
    private String hostingInstanceId;
    private String hostingIdentifier;
    private String status;
    private LocalDateTime createAt;
}
