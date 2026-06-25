package com.alibaba.himarket.service.mcp;

import com.alibaba.himarket.entity.SandboxInstance;

/**
 * Strategy interface for deploying MCP servers into sandbox clusters.
 *
 * <p>Each sandboxType can provide its own deployment implementation.
 */
public interface McpSandboxDeployStrategy {

    /**
     * Returns the sandbox type supported by this strategy.
     */
    String supportedSandboxType();

    /**
     * Deploys an MCP server into a sandbox cluster and returns the endpoint URL.
     *
     * @param sandbox sandbox instance
     * @param mcpServerId     MCP Server ID
     * @param mcpName MCP Server name
     * @param userId subscribing user ID
     * @param transportType transport type, such as sse or http for the endpoint protocol
     * @param metaProtocolType MCP meta protocol type, such as stdio, sse, or http for the CRD
     * @param connectionConfig MCP connection config JSON
     * @param apiKey user API key, used as the consumer credential token
     * @param authType authentication type, such as none or bearer
     * @param userParams user-submitted parameter values as JSON
     * @param extraParamsDef extra parameter definition JSON, including position metadata
     * @param namespace target namespace, defaults to "default" when blank
     * @param resourceSpec resource specification JSON, such as CPU and memory
     * @return endpoint URL
     */
    String deploy(
            SandboxInstance sandbox,
            String mcpServerId,
            String mcpName,
            String userId,
            String transportType,
            String metaProtocolType,
            String connectionConfig,
            String apiKey,
            String authType,
            String userParams,
            String extraParamsDef,
            String namespace,
            String resourceSpec);

    /**
     * Deletes the ToolServer CRD from the sandbox cluster.
     *
     * @param sandbox sandbox instance
     * @param mcpName MCP Server name
     * @param userId subscribing user ID
     * @param namespace namespace used for deployment, defaults to "default" when blank
     */
    void undeploy(SandboxInstance sandbox, String mcpName, String userId, String namespace);

    /**
     * Deletes the ToolServer CRD from the sandbox cluster with the given resourceName.
     *
     * @param sandbox sandbox instance
     * @param mcpName MCP Server name
     * @param userId subscribing user ID
     * @param namespace namespace used for deployment, defaults to "default" when blank
     * @param resourceName CRD resource name, or blank to fall back to name calculation
     */
    default void undeploy(
            SandboxInstance sandbox,
            String mcpName,
            String userId,
            String namespace,
            String resourceName) {
        undeploy(sandbox, mcpName, userId, namespace);
    }

    /**
     * Deletes the ToolServer CRD and associated K8s Secret from the sandbox cluster.
     *
     * @param sandbox sandbox instance
     * @param mcpName MCP Server name
     * @param userId subscribing user ID
     * @param namespace namespace used for deployment
     * @param resourceName CRD resource name
     * @param secretName K8s Secret name, or blank to skip Secret deletion
     */
    default void undeploy(
            SandboxInstance sandbox,
            String mcpName,
            String userId,
            String namespace,
            String resourceName,
            String secretName) {
        undeploy(sandbox, mcpName, userId, namespace, resourceName);
    }
}
