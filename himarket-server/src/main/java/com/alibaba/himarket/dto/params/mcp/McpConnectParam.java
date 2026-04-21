package com.alibaba.himarket.dto.params.mcp;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * MCP 远程连接请求参数。
 */
@Data
public class McpConnectParam {

    @NotBlank(message = "sandboxId 不能为空")
    private String sandboxId;

    @NotBlank(message = "传输类型不能为空")
    private String transportType;

    /** 用户填写的参数（JSON 格式），可选 */
    private String params;
}
