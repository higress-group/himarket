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
 *
 * <p>包含两类数据：
 * <ul>
 *   <li>冷数据（Cold）：来自 McpServerMeta 表 + Product 表，变更频率低</li>
 *   <li>热数据（Hot）：来自 McpServerEndpoint 表，随部署/订阅动态变化</li>
 * </ul>
 *
 * <p>热数据字段仅在特定查询场景下填充（如 listMetaByProduct、listMetaByProductIds），
 * 简单查询（如 getMeta）不会填充热数据。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class McpMetaResult implements OutputConverter<McpMetaResult, McpServerMeta> {

    // ==================== 冷数据：McpServerMeta ====================

    private String mcpServerId;
    private String productId;
    private String mcpName;
    private String sourceType;
    private String origin;
    private String protocolType;
    private String connectionConfig;
    private String extraParams;
    private String toolsConfig;
    private String tags;
    private String repoUrl;
    private String createdBy;
    private Boolean sandboxRequired;
    private LocalDateTime createAt;

    // ==================== 冷数据：Product（展示字段） ====================

    /** 展示名称（来自 Product.name） */
    private String displayName;

    /** 描述（来自 Product.description） */
    private String description;

    /** 图标 JSON（来自 Product.icon） */
    private String icon;

    /** 服务介绍（来自 Product.document） */
    private String serviceIntro;

    /** 发布状态：DRAFT / READY / PUBLISHED（来自 Product.status 映射） */
    private String publishStatus;

    /** 可见性：PUBLIC（来自 Product.status 映射） */
    private String visibility;

    // ==================== 热数据：McpServerEndpoint ====================
    // 以下字段仅在需要 endpoint 信息的查询中填充

    /** 沙箱托管后的 endpoint URL */
    private String endpointUrl;

    /** endpoint 协议（sse / streamableHttp） */
    private String endpointProtocol;

    /** endpoint 状态（ACTIVE / INACTIVE） */
    private String endpointStatus;

    /** endpoint 的 subscribeParams JSON（包含 namespace、extraParams 等部署参数） */
    private String subscribeParams;

    /** endpoint 的托管类型（SANDBOX / GATEWAY / NACOS / DIRECT） */
    private String endpointHostingType;

    // ==================== 计算字段 ====================

    /**
     * 后端统一解析的连接配置 JSON（标准 mcpServers 格式）。
     * 热数据优先（endpoint URL），冷数据 fallback（connectionConfig 解析）。
     * 前端可直接展示，无需自行拼接。
     */
    private String resolvedConfig;

    /**
     * 清除敏感字段，用于非管理员用户的响应脱敏。
     * 隐藏部署参数（含 namespace 等基础设施信息）。
     */
    public McpMetaResult sanitize() {
        this.subscribeParams = null;
        return this;
    }
}
