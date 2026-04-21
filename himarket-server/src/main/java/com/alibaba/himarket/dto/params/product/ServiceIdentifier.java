package com.alibaba.himarket.dto.params.product;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ServiceIdentifier {

    @NotBlank(message = "Service name cannot be blank")
    private String name;

    private String description;

    // Gateway MCP Server fields
    private String apiId;
    private String mcpServerId;
    private String mcpRouteId;

    // Gateway Agent API fields
    private String agentApiId;

    // Gateway Model API fields
    private String modelApiId;

    // Nacos fields
    private String mcpServerName;
    private String agentName;
    private String namespaceId;
}
