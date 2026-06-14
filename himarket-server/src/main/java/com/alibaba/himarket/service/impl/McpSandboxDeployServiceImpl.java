package com.alibaba.himarket.service.impl;

import com.alibaba.himarket.core.exception.BusinessException;
import com.alibaba.himarket.core.exception.ErrorCode;
import com.alibaba.himarket.entity.SandboxInstance;
import com.alibaba.himarket.repository.SandboxInstanceRepository;
import com.alibaba.himarket.service.McpSandboxDeployService;
import com.alibaba.himarket.service.mcp.McpSandboxDeployStrategy;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * MCP sandbox deployment service that dispatches each request to the matching sandbox strategy.
 */
@Service
@Slf4j
public class McpSandboxDeployServiceImpl implements McpSandboxDeployService {

    private final SandboxInstanceRepository sandboxInstanceRepository;
    private final Map<String, McpSandboxDeployStrategy> strategyMap;

    public McpSandboxDeployServiceImpl(
            SandboxInstanceRepository sandboxInstanceRepository,
            List<McpSandboxDeployStrategy> strategies) {
        this.sandboxInstanceRepository = sandboxInstanceRepository;
        this.strategyMap =
                strategies.stream()
                        .collect(
                                Collectors.toMap(
                                        McpSandboxDeployStrategy::supportedSandboxType,
                                        Function.identity()));
    }

    @Override
    public String deploy(
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
            String resourceSpec) {
        SandboxInstance sandbox =
                sandboxInstanceRepository
                        .findBySandboxId(sandboxId)
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                ErrorCode.NOT_FOUND,
                                                "Sandbox instance",
                                                sandboxId));

        // Reject deployment when the sandbox is already in an error state.
        if ("ERROR".equals(sandbox.getStatus())) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST,
                    "Sandbox status is ERROR and deployment cannot continue: "
                            + (sandbox.getStatusMessage() != null
                                    ? sandbox.getStatusMessage()
                                    : sandbox.getSandboxId()));
        }

        String sandboxType = sandbox.getSandboxType();
        McpSandboxDeployStrategy strategy = strategyMap.get(sandboxType);
        if (strategy == null) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST, "Unsupported sandbox type: " + sandboxType);
        }

        log.info(
                "Deploying MCP sandbox, sandboxId={}, sandboxType={}, strategy={}",
                sandboxId,
                sandboxType,
                strategy.getClass().getSimpleName());

        return strategy.deploy(
                sandbox,
                mcpServerId,
                mcpName,
                userId,
                transportType,
                metaProtocolType,
                connectionConfig,
                apiKey,
                authType,
                userParams,
                extraParamsDef,
                namespace,
                resourceSpec);
    }

    @Override
    public void undeploy(String sandboxId, String mcpName, String userId, String namespace) {
        undeploy(sandboxId, mcpName, userId, namespace, null);
    }

    @Override
    public void undeploy(
            String sandboxId,
            String mcpName,
            String userId,
            String namespace,
            String resourceName) {
        SandboxInstance sandbox =
                sandboxInstanceRepository
                        .findBySandboxId(sandboxId)
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                ErrorCode.NOT_FOUND,
                                                "Sandbox instance",
                                                sandboxId));

        String sandboxType = sandbox.getSandboxType();
        McpSandboxDeployStrategy strategy = strategyMap.get(sandboxType);
        if (strategy == null) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST, "Unsupported sandbox type: " + sandboxType);
        }

        log.info(
                "Undeploying MCP sandbox, sandboxId={}, sandboxType={}, mcpName={}, userId={},"
                        + " resourceName={}",
                sandboxId,
                sandboxType,
                mcpName,
                userId,
                resourceName);

        strategy.undeploy(sandbox, mcpName, userId, namespace, resourceName);
    }

    @Override
    public void undeploy(
            String sandboxId,
            String mcpName,
            String userId,
            String namespace,
            String resourceName,
            String secretName) {
        SandboxInstance sandbox =
                sandboxInstanceRepository
                        .findBySandboxId(sandboxId)
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                ErrorCode.NOT_FOUND,
                                                "Sandbox instance",
                                                sandboxId));
        String sandboxType = sandbox.getSandboxType();
        McpSandboxDeployStrategy strategy = strategyMap.get(sandboxType);
        if (strategy == null) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST, "Unsupported sandbox type: " + sandboxType);
        }
        log.info(
                "Undeploying MCP sandbox, sandboxId={}, sandboxType={}, mcpName={}, userId={},"
                        + " resourceName={}, secretName={}",
                sandboxId,
                sandboxType,
                mcpName,
                userId,
                resourceName,
                secretName);
        strategy.undeploy(sandbox, mcpName, userId, namespace, resourceName, secretName);
    }
}
