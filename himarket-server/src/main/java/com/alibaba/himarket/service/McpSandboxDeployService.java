package com.alibaba.himarket.service;

/**
 * MCP 沙箱部署服务：负责向沙箱集群部署 MCP Server 并获取 endpoint URL。
 *
 * <p>根据沙箱类型（sandboxType）分发到不同的部署策略。
 */
public interface McpSandboxDeployService {

    /**
     * 部署 MCP Server 到指定沙箱集群，返回 endpoint URL。
     *
     * @param sandboxId        沙箱实例 ID
     * @param mcpServerId      MCP Server ID
     * @param mcpName          MCP Server 名称
     * @param userId           订阅用户 ID
     * @param transportType    传输类型：sse / http（endpoint 协议）
     * @param metaProtocolType MCP meta 协议类型：stdio / sse / http（CRD 使用）
     * @param connectionConfig MCP 冷数据中的连接配置 JSON
     * @param apiKey           用户的 API Key
     * @param authType         鉴权方式：none / bearer
     * @param userParams       用户提交的参数值 JSON
     * @param extraParamsDef   额外参数定义 JSON（含 position 信息）
     * @param namespace        部署目标 Namespace（为空时使用 "default"）
     * @param resourceSpec     资源规格 JSON（CPU/内存等）
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
     * 删除沙箱集群中的 ToolServer CRD。
     *
     * @param sandboxId  沙箱实例 ID
     * @param mcpName    MCP Server 名称
     * @param userId     订阅用户 ID
     * @param namespace  部署时使用的 Namespace（为空时使用 "default"）
     */
    void undeploy(String sandboxId, String mcpName, String userId, String namespace);

    /**
     * 删除沙箱集群中的 ToolServer CRD（使用指定的 resourceName）。
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
     * 删除沙箱集群中的 ToolServer CRD 和关联的 K8s Secret。
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
