package com.alibaba.himarket.service.hicoding.session;

import com.alibaba.himarket.config.AcpProperties.CliProviderConfig;
import com.alibaba.himarket.service.hicoding.cli.ConfigFileBuilder;
import com.alibaba.himarket.service.hicoding.runtime.RuntimeAdapter;
import com.alibaba.himarket.service.hicoding.runtime.RuntimeConfig;
import com.alibaba.himarket.service.hicoding.sandbox.SandboxConfig;
import com.alibaba.himarket.service.hicoding.sandbox.SandboxInfo;
import com.alibaba.himarket.service.hicoding.sandbox.SandboxProvider;
import com.alibaba.himarket.service.hicoding.sandbox.SandboxProviderRegistry;
import com.alibaba.himarket.service.hicoding.sandbox.SandboxType;
import com.alibaba.himarket.service.hicoding.sandbox.init.ConfigInjectionPhase;
import com.alibaba.himarket.service.hicoding.sandbox.init.FileSystemReadyPhase;
import com.alibaba.himarket.service.hicoding.sandbox.init.InitConfig;
import com.alibaba.himarket.service.hicoding.sandbox.init.InitContext;
import com.alibaba.himarket.service.hicoding.sandbox.init.InitErrorCode;
import com.alibaba.himarket.service.hicoding.sandbox.init.InitResult;
import com.alibaba.himarket.service.hicoding.sandbox.init.SandboxAcquirePhase;
import com.alibaba.himarket.service.hicoding.sandbox.init.SandboxInitPipeline;
import com.alibaba.himarket.service.hicoding.sandbox.init.SidecarConnectPhase;
import com.alibaba.himarket.service.hicoding.sandbox.init.SkillDownloadPhase;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

/**
 * Initializes HiCoding sessions.
 *
 * <p>Orchestrates the full sandbox initialization flow: resolve the provider, inject authToken,
 * resolve session configuration, build SandboxConfig/InitContext, execute the pipeline, and return
 * the result.
 *
 * <p>Extracted from {@code HiCodingWebSocketHandler.initSandboxAsync()}. WebSocket message
 * sending, stdout subscription, and connection state management stay outside this class.
 */
@Component
public class SessionInitializer {

    private static final Logger logger = LoggerFactory.getLogger(SessionInitializer.class);

    private final SessionConfigResolver configResolver;
    private final ConfigFileBuilder configFileBuilder;
    private final SandboxProviderRegistry providerRegistry;

    public SessionInitializer(
            SessionConfigResolver configResolver,
            ConfigFileBuilder configFileBuilder,
            SandboxProviderRegistry providerRegistry) {
        this.configResolver = configResolver;
        this.configFileBuilder = configFileBuilder;
        this.providerRegistry = providerRegistry;
    }

    /**
     * Initialization result.
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
     * Initializes a sandbox session.
     *
     * @param userId          user ID
     * @param providerKey     CLI provider key
     * @param providerConfig  CLI provider configuration
     * @param runtimeConfig   runtime configuration
     * @param sessionConfig   session configuration from the frontend, or null
     * @param sandboxType     sandbox type
     * @param frontendSession frontend WebSocket session passed to InitContext for progress updates
     * @return initialization result
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
            // 1. Resolve the provider.
            SandboxProvider provider = providerRegistry.getProvider(sandboxType);

            // 2. Inject authToken into CLI process environment variables.
            injectAuthToken(sessionConfig, providerConfig, runtimeConfig, providerKey);

            // 3. Resolve configuration when the provider supports custom models.
            ResolvedSessionConfig resolved = null;
            if (sessionConfig != null && providerConfig.isSupportsCustomModel()) {
                resolved = configResolver.resolve(sessionConfig, userId);
            }

            // 4. Build SandboxConfig.
            SandboxConfig sandboxConfig =
                    new SandboxConfig(
                            userId,
                            sandboxType,
                            runtimeConfig.getCwd(),
                            runtimeConfig.getEnv() != null ? runtimeConfig.getEnv() : Map.of(),
                            Map.of(),
                            null);

            // 5. Build InitContext and attach resolved session configuration.
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

            // 6. Build the pipeline with ConfigFileBuilder for ConfigInjectionPhase.
            SandboxInitPipeline pipeline =
                    new SandboxInitPipeline(
                            List.of(
                                    new SandboxAcquirePhase(),
                                    new FileSystemReadyPhase(),
                                    new ConfigInjectionPhase(configFileBuilder),
                                    new SkillDownloadPhase(),
                                    new SidecarConnectPhase()),
                            InitConfig.defaults());

            // 7. Execute the pipeline.
            InitResult result = pipeline.execute(context);

            // 8. Convert the pipeline result.
            return toInitializationResult(result, context);

        } catch (Exception e) {
            logger.error(
                    "Session initialization failed, userId={}, provider={}",
                    userId,
                    providerKey,
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
     * Injects authToken into CLI process environment variables.
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
                        "Injected auth token into CLI environment, provider={}, envVar={},"
                                + " envSize={}",
                        providerKey,
                        providerConfig.getAuthEnvVar(),
                        runtimeConfig.getEnv().size());
            } else {
                logger.error("Runtime config environment is missing, cannot inject auth token");
            }
        } else {
            logger.warn(
                    "Received auth token without auth environment variable, provider={}",
                    providerKey);
        }
    }

    /**
     * Converts the pipeline InitResult to InitializationResult.
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

        return new InitializationResult(
                false,
                null,
                context.getSandboxInfo(),
                errorCode,
                result.errorMessage(),
                result.failedPhase(),
                false,
                result.totalDuration());
    }
}
