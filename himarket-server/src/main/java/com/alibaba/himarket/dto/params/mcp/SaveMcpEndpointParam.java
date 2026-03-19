package com.alibaba.himarket.dto.params.mcp;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 保存 MCP Server Endpoint 参数。
 */
@Data
public class SaveMcpEndpointParam {

    @NotBlank(message = "MCP Server ID 不能为空")
    private String mcpServerId;

    @NotBlank(message = "Endpoint URL 不能为空")
    private String endpointUrl;

    @NotBlank(message = "托管类型不能为空")
    private String hostingType;

    @NotBlank(message = "连接协议不能为空")
    private String protocol;

    /** 用户 ID，* 代表所有用户可见 */
    private String userId;

    /** 托管方实例 ID */
    private String hostingInstanceId;

    /** 托管标识符 */
    private String hostingIdentifier;
}
