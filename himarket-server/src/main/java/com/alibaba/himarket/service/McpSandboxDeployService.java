package com.alibaba.himarket.service;

/**
 * MCP sandbox deployment service that deploys MCP Servers and resolves endpoint URLs.
 *
 * <p>Requests are dispatched to deployment strategies by sandboxType.
 */
public interface McpSandboxDeployService {

    /**
     * Deploys an MCP Server to a sandbox cluster and returns the endpoint URL.
     *
     * @param sandboxId        sandbox instance ID
     * @param mcpServerId      MCP Server ID
     * @param mcpName          MCP Server name
     * @param userId           subscribing user ID
     * @param transportType    transport type: sse / http, used by the endpoint protocol
     * @param metaProtocolType MCP meta protocol type: stdio / sse / http, used by the CRD
     * @param connectionConfig connection config JSON from MCP cold data
     * @param apiKey           user API key
     * @param authType         auth type: none / bearer
     * @param userParams       user-submitted parameter values as JSON
     * @param extraParamsDef   extra parameter definition JSON, including position metadata
     * @param namespace        target Namespace, defaulting to "default" when blank
     * @param resourceSpec     resource spec JSON, including CPU and memory
     * @return endpoint URL
     */
    String deploy(
            String sandboxId,
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
     * @param sandboxId sandbox instance ID
     * @param mcpName   MCP Server name
     * @param userId    subscribing user ID
     * @param namespace Namespace used during deployment, defaulting to "default" when blank
     */
    void undeploy(String sandboxId, String mcpName, String userId, String namespace);

    /**
     * Deletes the ToolServer CRD from the sandbox cluster using the specified resourceName.
     */
    default void undeploy(
            String sandboxId,
            String mcpName,
            String userId,
            String namespace,
            String resourceName) {
        undeploy(sandboxId, mcpName, userId, namespace);
    }

    /**
     * Deletes the ToolServer CRD and associated K8s Secret from the sandbox cluster.
     */
    default void undeploy(
            String sandboxId,
            String mcpName,
            String userId,
            String namespace,
            String resourceName,
            String secretName) {
        undeploy(sandboxId, mcpName, userId, namespace, resourceName);
    }
}
