package com.alibaba.himarket.service.hicoding.sandbox.init;

import com.alibaba.himarket.service.hicoding.cli.ConfigFileBuilder;
import com.alibaba.himarket.service.hicoding.sandbox.ConfigFile;
import com.alibaba.himarket.service.hicoding.sandbox.SandboxInfo;
import com.alibaba.himarket.service.hicoding.sandbox.SandboxProvider;
import java.io.IOException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Injects configuration files into the sandbox.
 *
 * <p>Injects configuration files one by one through writeFile. Skill files are downloaded inside
 * the sandbox by nacos-cli, so the remaining configuration files are small enough to avoid archive
 * packaging.
 */
public class ConfigInjectionPhase implements InitPhase {

    private static final Logger logger = LoggerFactory.getLogger(ConfigInjectionPhase.class);

    private final ConfigFileBuilder configFileBuilder;

    /**
     * No-arg constructor kept for backward compatibility.
     */
    public ConfigInjectionPhase() {
        this.configFileBuilder = null;
    }

    /**
     * Constructor with ConfigFileBuilder for dynamic config generation from ResolvedSessionConfig.
     */
    public ConfigInjectionPhase(ConfigFileBuilder configFileBuilder) {
        this.configFileBuilder = configFileBuilder;
    }

    @Override
    public String name() {
        return "config-injection";
    }

    @Override
    public int order() {
        return 300;
    }

    @Override
    public boolean shouldExecute(InitContext context) {
        return context.getSessionConfig() != null
                && context.getProviderConfig() != null
                && context.getProviderConfig().isSupportsCustomModel();
    }

    @Override
    public void execute(InitContext context) throws InitPhaseException {
        List<ConfigFile> pendingConfigs = context.getInjectedConfigs();

        // Generate configs from resolvedSessionConfig when injectedConfigs was not prefilled.
        if ((pendingConfigs == null || pendingConfigs.isEmpty())
                && configFileBuilder != null
                && context.getResolvedSessionConfig() != null) {
            pendingConfigs =
                    configFileBuilder.build(
                            context.getResolvedSessionConfig(),
                            context.getRuntimeConfig().getProviderKey(),
                            context.getProviderConfig(),
                            context.getRuntimeConfig());
            context.setInjectedConfigs(pendingConfigs);
        }

        if (pendingConfigs == null || pendingConfigs.isEmpty()) {
            logger.info("No configuration files to inject");
            return;
        }

        SandboxProvider provider = context.getProvider();
        SandboxInfo info = context.getSandboxInfo();

        try {
            for (ConfigFile config : pendingConfigs) {
                provider.writeFile(info, config.relativePath(), config.content());
            }
            logger.info(
                    "Configuration files written individually, fileCount={}",
                    pendingConfigs.size());

            // Count files by type for diagnostics.
            long skillCount = pendingConfigs.stream().filter(c -> "skill".equals(c.type())).count();
            long mcpCount = pendingConfigs.stream().filter(c -> "mcp".equals(c.type())).count();
            long modelCount = pendingConfigs.stream().filter(c -> "model".equals(c.type())).count();
            long otherCount = pendingConfigs.size() - skillCount - mcpCount - modelCount;

            logger.info(
                    "Configuration injection completed, totalFileCount={}, skillCount={},"
                            + " mcpCount={}, modelCount={}, otherCount={}",
                    pendingConfigs.size(),
                    skillCount,
                    mcpCount,
                    modelCount,
                    otherCount);
        } catch (IOException e) {
            throw new InitPhaseException(
                    "config-injection",
                    "Failed to inject configuration: " + e.getMessage(),
                    e,
                    true);
        }
    }

    @Override
    public boolean verify(InitContext context) {
        return true;
    }

    @Override
    public RetryPolicy retryPolicy() {
        return RetryPolicy.none();
    }
}
