package com.alibaba.himarket.service.mcp;

import com.alibaba.himarket.entity.SandboxInstance;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * SELF_HOSTED 类型沙箱的部署策略 — 预留接口，暂不实现。
 */
@Component
@Slf4j
public class SelfHostedDeployStrategy implements McpSandboxDeployStrategy {

    @Override
    public String supportedSandboxType() {
        return "SELF_HOSTED";
    }

    @Override
    public String deploy(
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
            String resourceSpec) {
        // TODO: 实现 SELF_HOSTED 类型沙箱的部署逻辑
        throw new UnsupportedOperationException(
                "SELF_HOSTED 类型沙箱暂不支持 MCP 部署，请使用 AGENT_RUNTIME 类型沙箱");
    }

    @Override
    public void undeploy(SandboxInstance sandbox, String mcpName, String userId, String namespace) {
        // TODO: 实现 SELF_HOSTED 类型沙箱的卸载逻辑
        throw new UnsupportedOperationException(
                "SELF_HOSTED 类型沙箱暂不支持 undeploy，请使用 AGENT_RUNTIME 类型沙箱");
    }
}
