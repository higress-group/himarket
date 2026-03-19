package com.alibaba.himarket.dto.result.mcp;

import com.alibaba.himarket.dto.converter.OutputConverter;
import com.alibaba.himarket.entity.McpServerMeta;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * MCP Server 元信息返回结果。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class McpMetaResult implements OutputConverter<McpMetaResult, McpServerMeta> {

    private String mcpServerId;
    private String productId;
    private String displayName;
    private String mcpName;
    private String description;
    private String repoUrl;
    private String sourceType;
    private String origin;
    private String tags;
    private String icon;
    private String protocolType;
    private String connectionConfig;
    private String extraParams;
    private String serviceIntro;
    private String visibility;
    private String publishStatus;
    private String toolsConfig;
    private String createdBy;
    private Boolean sandboxRequired;
    private LocalDateTime createAt;

    /** 沙箱托管后的 endpoint URL（热数据，来自 mcp_server_endpoint） */
    private String endpointUrl;

    /** endpoint 协议（热数据） */
    private String endpointProtocol;

    /** endpoint 状态 */
    private String endpointStatus;

    /** endpoint 的 subscribeParams（包含 namespace、extraParams 等部署参数） */
    private String subscribeParams;

    /** endpoint 的托管类型（SANDBOX / GATEWAY / NACOS / DIRECT） */
    private String endpointHostingType;

    /**
     * 后端统一解析的连接配置 JSON（标准 mcpServers 格式）。
     * 热数据优先（endpoint URL），冷数据 fallback（connectionConfig 解析）。
     * 前端可直接展示，无需自行拼接。
     */
    private String resolvedConfig;
}
