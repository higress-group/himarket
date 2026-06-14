package com.alibaba.himarket.dto.params.mcp;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Parameters for external or user-initiated MCP Server registration.
 *
 * <p>productId is not required because the system creates a Product with the same name.
 */
@Data
public class RegisterMcpParam {

    @NotBlank(message = "MCP name is required")
    @Size(max = 63, message = "MCP name must be at most 63 characters")
    @Pattern(
            regexp = "^[a-z][a-z0-9-]*$",
            message =
                    "MCP name must start with a lowercase letter and contain only lowercase"
                            + " letters, digits, and hyphens")
    private String mcpName;

    @NotBlank(message = "MCP display name is required")
    @Size(max = 128, message = "MCP display name must be at most 128 characters")
    private String displayName;

    private String description;

    private String repoUrl;

    /**
     * Tags JSON string.
     */
    private String tags;

    /**
     * Icon JSON string, such as { type: "URL", url: "..." } or { type: "BASE64", data: "..." }.
     */
    private String icon;

    /**
     * Origin identifier, such as OPEN_API or AGENTRUNTIME.
     */
    private String origin;

    /**
     * External system user ID stored in createdBy.
     */
    private String createdBy;

    @NotBlank(message = "Protocol type is required")
    private String protocolType;

    @NotBlank(message = "Connection config is required")
    private String connectionConfig;

    /**
     * Extra parameter definition JSON string.
     */
    private String extraParams;

    /**
     * Service introduction in Markdown format.
     */
    private String serviceIntro;

    /**
     * Visibility, such as PUBLIC or PRIVATE.
     */
    private String visibility;

    /**
     * Publish status, such as DRAFT or PUBLISHED.
     */
    private String publishStatus;

    /**
     * Tools config JSON string.
     */
    private String toolsConfig;

    /**
     * Whether sandbox hosting is required.
     */
    private Boolean sandboxRequired;

    /**
     * Sandbox ID used when sandboxRequired is true.
     */
    private String sandboxId;

    /**
     * Transport protocol, such as sse or http, used when sandboxRequired is true.
     */
    private String transportType;

    /**
     * Authentication type, such as none or bearer.
     */
    private String authType;

    /**
     * Actual parameter values JSON.
     */
    private String paramValues;
}
