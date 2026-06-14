package com.alibaba.himarket.service.mcp;

import com.alibaba.himarket.entity.SandboxInstance;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Deployment strategy reserved for SELF_HOSTED sandboxes.
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
        // TODO: Implement deployment for SELF_HOSTED sandboxes.
        throw new UnsupportedOperationException(
                "SELF_HOSTED sandboxes do not support MCP deployment yet. Use AGENT_RUNTIME"
                        + " sandboxes instead.");
    }

    @Override
    public void undeploy(SandboxInstance sandbox, String mcpName, String userId, String namespace) {
        // TODO: Implement undeploy for SELF_HOSTED sandboxes.
        throw new UnsupportedOperationException(
                "SELF_HOSTED sandboxes do not support undeploy yet. Use AGENT_RUNTIME sandboxes"
                        + " instead.");
    }
}
