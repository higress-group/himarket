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
 * 将配置文件注入到沙箱内部。
 *
 * <p>逐个 writeFile 注入配置文件。Skill 文件改由 nacos-cli 在沙箱内下载，
 * 剩余配置文件数量很少，无需压缩解压。
 */
public class ConfigInjectionPhase implements InitPhase {

    private static final Logger logger = LoggerFactory.getLogger(ConfigInjectionPhase.class);

    private final ConfigFileBuilder configFileBuilder;

    /** 无参构造函数，保持向后兼容。 */
    public ConfigInjectionPhase() {
        this.configFileBuilder = null;
    }

    /** 带 ConfigFileBuilder 的构造函数，支持从 ResolvedSessionConfig 动态生成配置文件。 */
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

        // 如果 injectedConfigs 未被外部预填充，尝试从 resolvedSessionConfig 动态生成
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
            logger.info("[ConfigInjection] 无配置文件需要注入");
            return;
        }

        SandboxProvider provider = context.getProvider();
        SandboxInfo info = context.getSandboxInfo();

        try {
            for (ConfigFile config : pendingConfigs) {
                provider.writeFile(info, config.relativePath(), config.content());
            }
            logger.info("[ConfigInjection] 逐个写入完成: {} 个文件已注入", pendingConfigs.size());

            // 统计各类型文件数量
            long skillCount = pendingConfigs.stream().filter(c -> "skill".equals(c.type())).count();
            long mcpCount = pendingConfigs.stream().filter(c -> "mcp".equals(c.type())).count();
            long modelCount = pendingConfigs.stream().filter(c -> "model".equals(c.type())).count();
            long otherCount = pendingConfigs.size() - skillCount - mcpCount - modelCount;

            logger.info(
                    "[ConfigInjection] 配置注入完成: 共 {} 个文件 (skill={}, mcp={}, model={}, other={})",
                    pendingConfigs.size(),
                    skillCount,
                    mcpCount,
                    modelCount,
                    otherCount);
        } catch (IOException e) {
            throw new InitPhaseException("config-injection", "配置注入失败: " + e.getMessage(), e, true);
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
