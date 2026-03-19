package com.alibaba.himarket.dto.result.mcp;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * MCP Server 详细信息 — 用于 Open API 单条查询。
 * 不暴露 productId 等内部字段。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class McpMetaDetailResult {

    private String mcpServerId;
    private String mcpName;
    private String displayName;
    private String description;
    private String repoUrl;
    private String icon;
    private String protocolType;
    private String connectionConfig;
    private String origin;
    private String tags;
    private String extraParams;
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
                .connectionConfig(full.getConnectionConfig())
                .origin(full.getOrigin())
                .tags(full.getTags())
                .extraParams(full.getExtraParams())
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
