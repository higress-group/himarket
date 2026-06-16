package com.alibaba.himarket.dto.result.mcp;

import com.alibaba.himarket.dto.converter.OutputConverter;
import com.alibaba.himarket.entity.McpServerMeta;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * MCP Server metadata result.
 *
 * <p>This result contains two classes of data:
 * <ul>
 *   <li>Cold data from McpServerMeta and Product, which changes infrequently</li>
 *   <li>Hot data from McpServerEndpoint, which changes with deployment and subscription state</li>
 * </ul>
 *
 * <p>Hot data is populated only for queries that need endpoint information, such as
 * listMetaByProduct and listMetaByProductIds. Simple lookups, such as getMeta, do not populate
 * these fields.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class McpMetaResult implements OutputConverter<McpMetaResult, McpServerMeta> {

    // Cold data from McpServerMeta.

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

    // Cold display data from Product.

    /**
     * Display name from Product.name.
     */
    private String displayName;

    /**
     * Description from Product.description.
     */
    private String description;

    /**
     * Icon JSON from Product.icon.
     */
    private String icon;

    /**
     * Service introduction from Product.document.
     */
    private String serviceIntro;

    /**
     * Publish status from mapped Product.status, such as DRAFT, READY, or PUBLISHED.
     */
    private String publishStatus;

    /**
     * Visibility from mapped Product.status.
     */
    private String visibility;

    // Hot data from McpServerEndpoint. These fields are populated only when endpoint data is
    // needed.

    /**
     * Endpoint URL after sandbox hosting.
     */
    private String endpointUrl;

    /**
     * Endpoint protocol, such as sse or streamableHttp.
     */
    private String endpointProtocol;

    /**
     * Endpoint status, such as ACTIVE or INACTIVE.
     */
    private String endpointStatus;

    /**
     * Endpoint subscribeParams JSON, including namespace and extraParams deployment values.
     */
    private String subscribeParams;

    /**
     * Endpoint hosting type, such as SANDBOX, GATEWAY, NACOS, or DIRECT.
     */
    private String endpointHostingType;

    // Computed fields.

    /**
     * Connection config JSON resolved by the backend in standard mcpServers format.
     *
     * <p>Hot endpoint data takes precedence; cold connectionConfig is used as the fallback. Frontend
     * clients can display this value directly without assembling it themselves.
     */
    private String resolvedConfig;

    /**
     * Clears sensitive fields for non-admin responses.
     *
     * <p>Deployment parameters, including namespace and other infrastructure details, are hidden.
     */
    public McpMetaResult sanitize() {
        this.subscribeParams = null;
        return this;
    }
}
