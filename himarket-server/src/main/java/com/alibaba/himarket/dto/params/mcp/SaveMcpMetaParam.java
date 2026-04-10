package com.alibaba.himarket.dto.params.mcp;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 保存 MCP Server 元信息参数。
 *
 * <p>该 DTO 承载多个场景的输入，通过验证组区分：
 * <ul>
 *   <li>{@link AdminCreate} — 管理员在 Admin 后台手动创建 MCP</li>
 *   <li>{@link GatewayImport} — 从网关导入，需要 gatewayId + refConfig</li>
 *   <li>{@link NacosImport} — 从 Nacos 导入，需要 nacosId + refConfig</li>
 *   <li>{@link SandboxDeploy} — 沙箱部署，需要 sandboxId + transportType</li>
 * </ul>
 *
 * <p>默认验证组（无 groups 注解）适用于所有场景。
 */
@Data
public class SaveMcpMetaParam {

    // ==================== 验证组定义 ====================

    /** 管理员手动创建 */
    public interface AdminCreate {}

    /** 网关导入 */
    public interface GatewayImport {}

    /** Nacos 导入 */
    public interface NacosImport {}

    /** 沙箱部署 */
    public interface SandboxDeploy {}

    // ==================== 通用字段（所有场景必填） ====================

    @NotBlank(message = "关联产品ID不能为空")
    private String productId;

    @NotBlank(message = "MCP 英文名称不能为空")
    @Pattern(regexp = "^[a-z][a-z0-9-]*$", message = "小写字母开头，仅含小写字母、数字、连字符")
    @Size(max = 63, message = "不超过 63 个字符")
    private String mcpName;

    @NotBlank(message = "MCP 展示名称不能为空")
    @Size(max = 128, message = "不超过 128 个字符")
    private String displayName;

    private String description;

    private String repoUrl;

    private String sourceType;

    private String origin;

    @NotBlank(message = "协议类型不能为空")
    private String protocolType;

    @NotBlank(message = "连接配置不能为空")
    private String connectionConfig;

    // ==================== 展示字段 ====================

    /** JSON 字符串 */
    private String tags;

    /** JSON 字符串 */
    private String icon;

    /** JSON 字符串 */
    private String extraParams;

    private String serviceIntro;

    private String visibility;

    private String publishStatus;

    /** JSON 字符串 */
    private String toolsConfig;

    /** 创建者（外部系统可传入用户ID） */
    private String createdBy;

    // ==================== 网关导入字段 ====================

    /** 网关导入时的网关ID（GatewayImport 场景必填） */
    @NotBlank(message = "网关ID不能为空", groups = GatewayImport.class)
    private String gatewayId;

    // ==================== Nacos 导入字段 ====================

    /** Nacos 导入时的 Nacos 实例ID（NacosImport 场景必填） */
    @NotBlank(message = "Nacos实例ID不能为空", groups = NacosImport.class)
    private String nacosId;

    /** 网关/Nacos 的 refConfig JSON（用于 ProductRef 关联） */
    private String refConfig;

    // ==================== 沙箱部署字段 ====================

    /** 是否需要沙箱托管 */
    private Boolean sandboxRequired;

    /** 管理员预部署沙箱ID（SandboxDeploy 场景必填） */
    @NotBlank(message = "沙箱实例ID不能为空", groups = SandboxDeploy.class)
    private String sandboxId;

    /** 管理员预部署传输协议：sse / http（sandboxRequired=true 时使用） */
    private String transportType;

    /** 管理员预部署鉴权方式：none / bearer（sandboxRequired=true 时使用） */
    private String authType;

    /** 管理员预部署时填写的参数实际值 JSON（如 {"API_KEY":"sk-xxx"}） */
    private String paramValues;

    /** 部署目标 Namespace（AGENT_RUNTIME 沙箱在 MCP 创建时选择） */
    private String namespace;

    /** 资源规格配置 JSON（CPU/内存等，在 MCP 配置沙箱部署时设置） */
    private String resourceSpec;
}
