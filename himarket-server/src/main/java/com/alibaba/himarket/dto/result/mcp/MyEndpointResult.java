package com.alibaba.himarket.dto.result.mcp;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * "我的MCP" 列表项：endpoint + meta 合并展示。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MyEndpointResult {

    // ---- endpoint 字段 ----
    private String endpointId;
    private String mcpServerId;
    private String endpointUrl;
    private String hostingType;
    private String protocol;
    private String hostingInstanceId;
    private String subscribeParams;
    private String status;
    private LocalDateTime endpointCreatedAt;

    // ---- meta 字段（展示用） ----
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
