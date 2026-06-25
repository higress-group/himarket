package com.alibaba.himarket.dto.result.mcp;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * MCP Server detail result for Open API single-item queries.
 *
 * <p>Sensitive fields such as productId and connectionConfig are not exposed. The result exposes
 * resolvedConfig instead, which uses the standardized mcpServers format and contains only public
 * URLs.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class McpMetaDetailResult {

    private String mcpServerId;
    private String mcpName;
    private String displayName;
    private String description;
    private String repoUrl;
    private String icon;
    private String protocolType;

    /**
     * Standardized mcpServers connection config without internal network addresses or environment
     * variables.
     */
    private String resolvedConfig;

    private String origin;
    private String tags;
    private String serviceIntro;
    private String visibility;
    private String publishStatus;
    private String toolsConfig;
    private String createdBy;
    private Boolean sandboxRequired;
    private LocalDateTime createAt;

    public static McpMetaDetailResult fromFull(McpMetaResult full) {
        return McpMetaDetailResult.builder()
                .mcpServerId(full.getMcpServerId())
                .mcpName(full.getMcpName())
                .displayName(full.getDisplayName())
                .description(full.getDescription())
                .repoUrl(full.getRepoUrl())
                .icon(full.getIcon())
                .protocolType(full.getProtocolType())
                .resolvedConfig(full.getResolvedConfig())
                .origin(full.getOrigin())
                .tags(full.getTags())
                .serviceIntro(full.getServiceIntro())
                .visibility(full.getVisibility())
                .publishStatus(full.getPublishStatus())
                .toolsConfig(full.getToolsConfig())
                .createdBy(full.getCreatedBy())
                .sandboxRequired(full.getSandboxRequired())
                .createAt(full.getCreateAt())
                .build();
    }
}
