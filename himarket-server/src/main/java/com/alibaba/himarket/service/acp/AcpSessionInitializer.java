package com.alibaba.himarket.service.acp;

import com.alibaba.himarket.config.AcpProperties.CliProviderConfig;
import com.alibaba.himarket.service.acp.runtime.*;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

/**
 * 会话初始化器。
 *
 * <p>编排沙箱初始化的完整流程：获取 Provider → 注入 authToken → 解析配置 →
 * 构建 SandboxConfig/InitContext → 执行 Pipeline → 返回结果。
 *
 * <p>从 {@code AcpWebSocketHandler.initSandboxAsync()} 中提取，
 * 不包含 WebSocket 消息发送、stdout 订阅、连接状态管理等职责。
 */
@Component
public class AcpSessionInitializer {

    private static final Logger logger = LoggerFactory.getLogger(AcpSessionInitializer.class);

    private final SessionConfigResolver configResolver;
    private final ConfigFileBuilder configFileBuilder;
    private final SandboxProviderRegistry providerRegistry;

    public AcpSessionInitializer(
            SessionConfigResolver configResolver,
            ConfigFileBuilder configFileBuilder,
            SandboxProviderRegistry providerRegistry) {
        this.configResolver = configResolver;
        this.configFileBuilder = configFileBuilder;
        this.providerRegistry = providerRegistry;
    }

    /**
     * 初始化结果。
     */
    public record InitializationResult(
            boolean success,
            RuntimeAdapter adapter,
            SandboxInfo sandboxInfo,
            InitErrorCode errorCode,
            String errorMessage,
            String failedPhase,
            boolean retryable,
            Duration totalDuration) {}

    /**
     * 执行沙箱初始化。
     *
     * @param userId 用户 ID
     * @param providerKey CLI 提供者标识
     * @param providerConfig CLI 提供者配置
     * @param runtimeConfig 运行时配置
     * @param sessionConfig 前端传入的会话配置（可为 null）
     * @param sandboxType 沙箱类型
     * @param frontendSession 前端 WebSocket session（传递给 InitContext，用于阶段内推送进度）
     * @return 初始化结果
     */
    public InitializationResult initialize(
            String userId,
            String providerKey,
            CliProviderConfig providerConfig,
            RuntimeConfig runtimeConfig,
            CliSessionConfig sessionConfig,
            SandboxType sandboxType,
            WebSocketSession frontendSession) {

        try {
            // 1. 获取 Provider
            SandboxProvider provider = providerRegistry.getProvider(sandboxType);

            // 2. 注入 authToken 到 CLI 进程环境变量
            injectAuthToken(sessionConfig, providerConfig, runtimeConfig, providerKey);

            // 3. 解析配置（仅在 sessionConfig 非空且 provider 支持自定义模型时）
            ResolvedSessionConfig resolved = null;
            if (sessionConfig != null && providerConfig.isSupportsCustomModel()) {
                resolved = configResolver.resolve(sessionConfig, userId);
            }

            // 4. 构建 SandboxConfig
            SandboxConfig sandboxConfig =
                    new SandboxConfig(
                            userId,
                            sandboxType,
                            runtimeConfig.getCwd(),
                            runtimeConfig.getEnv() != null ? runtimeConfig.getEnv() : Map.of(),
                            runtimeConfig.getK8sConfigId(),
                            Map.of(),
                            null,
                            0);

            // 5. 构建 InitContext（设置 resolvedSessionConfig）
            InitContext context =
                    new InitContext(
                            provider,
                            userId,
                            sandboxConfig,
                            runtimeConfig,
                            providerConfig,
                            sessionConfig,
                            frontendSession);
            context.setResolvedSessionConfig(resolved);

            // 6. 构建 Pipeline（传入 ConfigFileBuilder 给 ConfigInjectionPhase）
            SandboxInitPipeline pipeline =
                    new SandboxInitPipeline(
                            List.of(
                                    new SandboxAcquirePhase(),
                                    new FileSystemReadyPhase(),
                                    new ConfigInjectionPhase(configFileBuilder),
                                    new SidecarConnectPhase(),
                                    new CliReadyPhase()),
                            InitConfig.defaults());

            // 7. 执行 Pipeline
            InitResult result = pipeline.execute(context);

            // 8. 转换为 InitializationResult
            return toInitializationResult(result, context);

        } catch (Exception e) {
            logger.error(
                    "[AcpSessionInitializer] 初始化异常: userId={}, provider={}, error={}",
                    userId,
                    providerKey,
                    e.getMessage(),
                    e);
            return new InitializationResult(
                    false,
                    null,
                    null,
                    InitErrorCode.UNKNOWN_ERROR,
                    e.getMessage(),
                    null,
                    false,
                    Duration.ZERO);
        }
    }

    /**
     * 注入 authToken 到 CLI 进程环境变量。
     */
    private void injectAuthToken(
            CliSessionConfig sessionConfig,
            CliProviderConfig providerConfig,
            RuntimeConfig runtimeConfig,
            String providerKey) {
        if (sessionConfig == null || sessionConfig.getAuthToken() == null) {
            return;
        }
        if (providerConfig.getAuthEnvVar() != null) {
            if (runtimeConfig.getEnv() != null) {
                runtimeConfig
                        .getEnv()
                        .put(providerConfig.getAuthEnvVar(), sessionConfig.getAuthToken());
                logger.info(
                        "[AcpSessionInitializer] authToken injected to env var '{}' for provider"
                                + " '{}', current env size: {}",
                        providerConfig.getAuthEnvVar(),
                        providerKey,
                        runtimeConfig.getEnv().size());
            } else {
                logger.error(
                        "[AcpSessionInitializer] runtimeConfig.getEnv() is null, cannot inject"
                                + " authToken");
            }
        } else {
            logger.warn(
                    "[AcpSessionInitializer] Received authToken but authEnvVar is not configured"
                            + " for provider: {}, ignoring authToken",
                    providerKey);
        }
    }

    /**
     * 将 Pipeline 的 InitResult 转换为 InitializationResult。
     */
    private InitializationResult toInitializationResult(InitResult result, InitContext context) {
        if (result.success()) {
            return new InitializationResult(
                    true,
                    context.getRuntimeAdapter(),
                    context.getSandboxInfo(),
                    null,
                    null,
                    null,
                    false,
                    result.totalDuration());
        }

        InitErrorCode errorCode = InitErrorCode.fromPhaseName(result.failedPhase());
        boolean retryable = isPhaseRetryable(result.failedPhase());

        return new InitializationResult(
                false,
                null,
                context.getSandboxInfo(),
                errorCode,
                result.errorMessage(),
                result.failedPhase(),
                retryable,
                result.totalDuration());
    }

    /**
     * 判断失败阶段是否可重试。
     */
    private boolean isPhaseRetryable(String phaseName) {
        if (phaseName == null) {
            return false;
        }
        return switch (phaseName) {
            case "filesystem-ready", "config-injection", "sidecar-connect" -> true;
            case "sandbox-acquire", "cli-ready" -> false;
            default -> false;
        };
    }
}
