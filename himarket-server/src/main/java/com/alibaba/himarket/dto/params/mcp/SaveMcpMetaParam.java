package com.alibaba.himarket.dto.params.mcp;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Parameters for saving MCP Server metadata.
 *
 * <p>This DTO supports multiple input scenarios through validation groups:
 * <ul>
 *   <li>{@link AdminCreate} for manual MCP creation in the admin console</li>
 *   <li>{@link GatewayImport} for gateway import, requiring gatewayId and refConfig</li>
 *   <li>{@link NacosImport} for Nacos import, requiring nacosId and refConfig</li>
 *   <li>{@link SandboxDeploy} for sandbox deployment, requiring sandboxId and transportType</li>
 * </ul>
 *
 * <p>The default validation group applies to all scenarios.
 */
@Data
public class SaveMcpMetaParam {

    // Validation groups.

    /**
     * Manual admin creation.
     */
    public interface AdminCreate {}

    /**
     * Gateway import.
     */
    public interface GatewayImport {}

    /**
     * Nacos import.
     */
    public interface NacosImport {}

    /**
     * Sandbox deployment.
     */
    public interface SandboxDeploy {}

    // Fields required by all scenarios.

    @NotBlank(message = "Product ID is required")
    private String productId;

    @NotBlank(message = "MCP name is required")
    @Pattern(
            regexp = "^[a-z][a-z0-9-]*$",
            message =
                    "MCP name must start with a lowercase letter and contain only lowercase"
                            + " letters, digits, and hyphens")
    @Size(max = 63, message = "MCP name must be at most 63 characters")
    private String mcpName;

    @NotBlank(message = "MCP display name is required")
    @Size(max = 128, message = "MCP display name must be at most 128 characters")
    private String displayName;

    private String description;

    private String repoUrl;

    private String sourceType;

    private String origin;

    @NotBlank(message = "Protocol type is required")
    private String protocolType;

    @NotBlank(message = "Connection config is required")
    private String connectionConfig;

    // Display fields.

    /**
     * Tags JSON string.
     */
    private String tags;

    /**
     * Icon JSON string.
     */
    private String icon;

    /**
     * Extra parameter definition JSON string.
     */
    private String extraParams;

    private String serviceIntro;

    private String visibility;

    private String publishStatus;

    /**
     * Tools config JSON string.
     */
    private String toolsConfig;

    /**
     * Creator user ID, optionally provided by external systems.
     */
    private String createdBy;

    // Gateway import fields.

    /**
     * Gateway ID required for GatewayImport.
     */
    @NotBlank(message = "Gateway ID is required", groups = GatewayImport.class)
    private String gatewayId;

    // Nacos import fields.

    /**
     * Nacos instance ID required for NacosImport.
     */
    @NotBlank(message = "Nacos instance ID is required", groups = NacosImport.class)
    private String nacosId;

    /**
     * Gateway or Nacos refConfig JSON used for ProductRef binding.
     */
    private String refConfig;

    // Sandbox deployment fields.

    /**
     * Whether sandbox hosting is required.
     */
    private Boolean sandboxRequired;

    /**
     * Sandbox ID required for SandboxDeploy.
     */
    @NotBlank(message = "Sandbox instance ID is required", groups = SandboxDeploy.class)
    private String sandboxId;

    /**
     * Pre-deployment transport protocol, such as sse or http.
     */
    private String transportType;

    /**
     * Pre-deployment authentication type, such as none or bearer.
     */
    private String authType;

    /**
     * Actual parameter values JSON provided during pre-deployment.
     */
    private String paramValues;

    /**
     * Target deployment namespace for AGENT_RUNTIME sandbox.
     */
    private String namespace;

    /**
     * Resource specification JSON, such as CPU and memory settings.
     */
    private String resourceSpec;
}
