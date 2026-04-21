package com.alibaba.himarket.dto.result.mcp;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * MCP Server 详细信息 — 用于 Open API 单条查询。
 * 不暴露 productId、connectionConfig（含内部网络地址）等敏感字段。
 * 改为暴露 resolvedConfig（标准化后的 mcpServers 格式，仅含公开 URL）。
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

    /** 标准化后的连接配置（mcpServers 格式），不含内部网络地址和环境变量 */
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
