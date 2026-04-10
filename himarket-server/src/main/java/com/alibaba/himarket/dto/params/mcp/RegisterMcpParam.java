package com.alibaba.himarket.dto.params.mcp;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 外部/用户注册 MCP Server 参数。
 *
 * <p>不需要传 productId，系统会自动创建同名 Product。
 */
@Data
public class RegisterMcpParam {

    @NotBlank(message = "MCP 英文名称不能为空")
    @Size(max = 63, message = "不超过 63 个字符")
    @Pattern(regexp = "^[a-z][a-z0-9-]*$", message = "小写字母开头，仅含小写字母、数字、连字符")
    private String mcpName;

    @NotBlank(message = "MCP 展示名称不能为空")
    @Size(max = 128, message = "不超过 128 个字符")
    private String displayName;

    private String description;

    private String repoUrl;

    /** JSON 字符串 */
    private String tags;

    /** JSON 字符串：{ type: "URL", url: "..." } 或 { type: "BASE64", data: "..." } */
    private String icon;

    /** 来源标识：OPEN_API（默认）/ AGENTRUNTIME 等 */
    private String origin;

    /** 外部系统的用户ID，存入 createdBy 字段 */
    private String createdBy;

    @NotBlank(message = "协议类型不能为空")
    private String protocolType;

    @NotBlank(message = "连接配置不能为空")
    private String connectionConfig;

    /** JSON 字符串：额外参数定义 */
    private String extraParams;

    /** Markdown 格式的服务介绍 */
    private String serviceIntro;

    /** 可见性：PUBLIC / PRIVATE，默认 PUBLIC */
    private String visibility;

    /** 发布状态：DRAFT / PUBLISHED，默认 PUBLISHED */
    private String publishStatus;

    /** JSON 字符串：工具配置 */
    private String toolsConfig;

    /** 是否需要沙箱托管 */
    private Boolean sandboxRequired;

    /** 沙箱ID（sandboxRequired=true 时使用） */
    private String sandboxId;

    /** 传输协议：sse / http（sandboxRequired=true 时使用） */
    private String transportType;

    /** 鉴权方式：none / bearer */
    private String authType;

    /** 参数实际值 JSON（如 {"API_KEY":"sk-xxx"}） */
    private String paramValues;
}
