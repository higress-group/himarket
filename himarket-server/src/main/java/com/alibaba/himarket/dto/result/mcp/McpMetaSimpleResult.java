package com.alibaba.himarket.dto.result.mcp;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Simplified MCP Server metadata for Open API list queries.
 *
 * <p>Internal or sensitive fields such as productId and connectionConfig are not exposed.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class McpMetaSimpleResult {

    private String mcpServerId;
    private String mcpName;
    private String displayName;
    private String description;
    private String icon;
    private String protocolType;
    private String origin;
    private String tags;
    private String publishStatus;
    private Boolean sandboxRequired;
    private LocalDateTime createAt;

    public static McpMetaSimpleResult fromFull(McpMetaResult full) {
        return McpMetaSimpleResult.builder()
                .mcpServerId(full.getMcpServerId())
                .mcpName(full.getMcpName())
                .displayName(full.getDisplayName())
                .description(full.getDescription())
                .icon(full.getIcon())
                .protocolType(full.getProtocolType())
                .origin(full.getOrigin())
                .tags(full.getTags())
                .publishStatus(full.getPublishStatus())
                .sandboxRequired(full.getSandboxRequired())
                .createAt(full.getCreateAt())
                .build();
    }
}
