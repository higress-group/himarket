package com.alibaba.himarket.service.mcp;

import com.alibaba.himarket.entity.SandboxInstance;

/**
 * MCP 沙箱部署策略接口。
 *
 * <p>不同 sandboxType 对应不同的部署实现。
 */
public interface McpSandboxDeployStrategy {

    /**
     * 该策略支持的沙箱类型。
     */
    String supportedSandboxType();

    /**
     * 部署 MCP Server 到沙箱集群，返回 endpoint URL。
     *
     * @param sandbox         沙箱实例
     * @param mcpServerId     MCP Server ID
     * @param mcpName         MCP Server 名称
     * @param userId          订阅用户 ID
     * @param transportType   传输类型：sse / http（endpoint 协议）
     * @param metaProtocolType MCP meta 协议类型：stdio / sse / http（CRD 使用）
     * @param connectionConfig MCP 连接配置 JSON
     * @param apiKey          用户的 API Key（consumer credential token）
     * @param authType        鉴权方式：none / bearer
     * @param userParams      用户提交的参数值 JSON
     * @param extraParamsDef  额外参数定义 JSON（含 position 信息）
     * @param namespace       部署目标 Namespace（为空时使用 "default"）
     * @param resourceSpec    资源规格 JSON（CPU/内存等）
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
     * 删除沙箱集群中的 ToolServer CRD。
     *
     * @param sandbox    沙箱实例
     * @param mcpName    MCP Server 名称
     * @param userId     订阅用户 ID
     * @param namespace  部署时使用的 Namespace（为空时使用 "default"）
     */
    void undeploy(SandboxInstance sandbox, String mcpName, String userId, String namespace);

    /**
     * 删除沙箱集群中的 ToolServer CRD（使用指定的 resourceName）。
     *
     * @param sandbox       沙箱实例
     * @param mcpName       MCP Server 名称
     * @param userId        订阅用户 ID
     * @param namespace     部署时使用的 Namespace（为空时使用 "default"）
     * @param resourceName  CRD 资源名称（为空时回退到名称计算）
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
     * 删除沙箱集群中的 ToolServer CRD 和关联的 K8s Secret。
     *
     * @param sandbox       沙箱实例
     * @param mcpName       MCP Server 名称
     * @param userId        订阅用户 ID
     * @param namespace     部署时使用的 Namespace
     * @param resourceName  CRD 资源名称
     * @param secretName    K8s Secret 名称（为空时跳过 Secret 删除）
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
