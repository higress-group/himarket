package com.alibaba.himarket.service.acp.runtime;

import com.alibaba.himarket.service.acp.ConfigFileBuilder;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 将配置文件注入到沙箱内部。
 *
 * <p>所有配置文件打包为 tar.gz 压缩包，通过 SandboxProvider.extractArchive() 一次性传输到沙箱，
 * 替代逐个 writeFile 调用。二进制传输避免了 JSON 序列化往返导致的 Unicode 字符规范化问题。
 *
 * <p>验证阶段通过抽样 readFile 确认文件已正确解压。
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
            boolean usedExtract = false;
            try {
                // 优先使用 extractArchive 批量注入
                byte[] tarGzBytes = buildTarGz(pendingConfigs);
                logger.info(
                        "[ConfigInjection] 打包完成: {} 个文件, 压缩后 {} 字节",
                        pendingConfigs.size(),
                        tarGzBytes.length);

                int extractedCount = provider.extractArchive(info, tarGzBytes);
                logger.info("[ConfigInjection] 解压完成: {} 个文件已注入", extractedCount);
                usedExtract = true;
            } catch (UnsupportedOperationException e) {
                logger.info("[ConfigInjection] Provider 不支持 extractArchive，降级为逐个 writeFile");
            } catch (IOException e) {
                if (e.getMessage() != null && e.getMessage().contains("Not Found")) {
                    logger.info(
                            "[ConfigInjection] Sidecar 不支持 /files/extract (404)，降级为逐个 writeFile");
                } else {
                    throw e;
                }
            }

            if (!usedExtract) {
                // Fallback: 逐个 writeFile
                for (ConfigFile config : pendingConfigs) {
                    provider.writeFile(info, config.relativePath(), config.content());
                }
                logger.info("[ConfigInjection] 逐个写入完成: {} 个文件已注入", pendingConfigs.size());
            }

            // 抽样验证：检查首尾各一个文件是否可读
            verifyExtracted(provider, info, pendingConfigs);

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
        } catch (InitPhaseException e) {
            throw e;
        } catch (IOException e) {
            throw new InitPhaseException("config-injection", "配置注入失败: " + e.getMessage(), e, true);
        }
    }

    /**
     * 将配置文件列表打包为 tar.gz 字节数组。
     */
    static byte[] buildTarGz(List<ConfigFile> configs) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (GzipCompressorOutputStream gzOut = new GzipCompressorOutputStream(baos);
                TarArchiveOutputStream tarOut = new TarArchiveOutputStream(gzOut)) {
            tarOut.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);
            for (ConfigFile config : configs) {
                byte[] contentBytes = config.content().getBytes(StandardCharsets.UTF_8);
                TarArchiveEntry entry = new TarArchiveEntry(config.relativePath());
                entry.setSize(contentBytes.length);
                tarOut.putArchiveEntry(entry);
                tarOut.write(contentBytes);
                tarOut.closeArchiveEntry();
            }
            tarOut.finish();
        }
        return baos.toByteArray();
    }

    /**
     * 抽样验证解压结果：检查首尾各一个文件是否可读且非空。
     */
    private void verifyExtracted(
            SandboxProvider provider, SandboxInfo info, List<ConfigFile> configs)
            throws InitPhaseException {
        // 抽样：第一个和最后一个文件
        int[] indices = configs.size() == 1 ? new int[] {0} : new int[] {0, configs.size() - 1};
        for (int idx : indices) {
            ConfigFile config = configs.get(idx);
            try {
                String readBack = provider.readFile(info, config.relativePath());
                if (readBack == null || readBack.isEmpty()) {
                    throw new InitPhaseException(
                            "config-injection", "抽样验证失败，文件为空: " + config.relativePath(), true);
                }
                // hash 不匹配只 warn，不阻断流程
                String expectedHash = config.contentHash();
                if (expectedHash != null && !expectedHash.isEmpty()) {
                    String actualHash = sha256(readBack);
                    if (!expectedHash.equals(actualHash)) {
                        logger.warn(
                                "[ConfigInjection] 文件 hash 不匹配: path={}, expected={}, actual={}",
                                config.relativePath(),
                                expectedHash,
                                actualHash);
                    }
                }
            } catch (InitPhaseException e) {
                throw e;
            } catch (IOException e) {
                throw new InitPhaseException(
                        "config-injection",
                        "抽样验证失败: " + config.relativePath() + " - " + e.getMessage(),
                        e,
                        true);
            }
        }
    }

    @Override
    public boolean verify(InitContext context) {
        List<ConfigFile> configs = context.getInjectedConfigs();
        if (configs == null || configs.isEmpty()) {
            return true;
        }
        try {
            SandboxProvider provider = context.getProvider();
            SandboxInfo info = context.getSandboxInfo();
            for (ConfigFile config : configs) {
                String content = provider.readFile(info, config.relativePath());
                if (content == null) {
                    return false;
                }
            }
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    @Override
    public RetryPolicy retryPolicy() {
        return RetryPolicy.none();
    }

    static String sha256(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 不可用", e);
        }
    }
}
