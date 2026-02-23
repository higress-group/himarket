package com.alibaba.himarket.service.acp.runtime;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 将配置文件注入到沙箱内部。 通过 SandboxProvider.writeFile() 统一写入，写入后通过 readFile() 读回验证。 所有沙箱类型走完全相同的代码路径。
 */
public class ConfigInjectionPhase implements InitPhase {

    private static final Logger logger = LoggerFactory.getLogger(ConfigInjectionPhase.class);

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
        if (pendingConfigs == null || pendingConfigs.isEmpty()) {
            logger.info("[ConfigInjection] 无配置文件需要注入");
            return;
        }

        SandboxProvider provider = context.getProvider();
        SandboxInfo info = context.getSandboxInfo();

        for (ConfigFile config : pendingConfigs) {
            try {
                // 通过 Sidecar HTTP API 写入配置文件
                provider.writeFile(info, config.relativePath(), config.content());

                // 读回并验证 SHA-256 哈希一致性
                String readBack = provider.readFile(info, config.relativePath());
                String writeHash = sha256(config.content());
                String readHash = sha256(readBack);

                if (!writeHash.equals(readHash)) {
                    throw new InitPhaseException(
                            "config-injection",
                            "配置文件验证失败: "
                                    + config.relativePath()
                                    + " (写入哈希="
                                    + writeHash
                                    + ", 读回哈希="
                                    + readHash
                                    + ")",
                            true);
                }

                // 敏感信息仅记录文件路径和哈希值，不记录内容
                logger.info(
                        "[ConfigInjection] 配置文件已注入: path={}, hash={}, type={}",
                        config.relativePath(),
                        writeHash,
                        config.type());
            } catch (InitPhaseException e) {
                throw e;
            } catch (IOException e) {
                throw new InitPhaseException(
                        "config-injection", "配置注入失败: " + e.getMessage(), e, true);
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
        return RetryPolicy.fileOperation();
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
