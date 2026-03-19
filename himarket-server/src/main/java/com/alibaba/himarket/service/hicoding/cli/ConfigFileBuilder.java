package com.alibaba.himarket.service.hicoding.cli;

import com.alibaba.himarket.config.AcpProperties.CliProviderConfig;
import com.alibaba.himarket.service.hicoding.runtime.RuntimeConfig;
import com.alibaba.himarket.service.hicoding.sandbox.ConfigFile;
import com.alibaba.himarket.service.hicoding.session.ResolvedSessionConfig;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 配置文件构建器。
 *
 * <p>将 {@link ResolvedSessionConfig} 转换为 {@link ConfigFile} 列表。 封装了 {@link CliConfigGenerator}
 * 调用、临时目录管理、文件收集等逻辑。
 *
 * <p>从 {@code HiCodingWebSocketHandler.prepareConfigFiles()} 中提取。
 */
@Component
public class ConfigFileBuilder {

    private static final Logger logger = LoggerFactory.getLogger(ConfigFileBuilder.class);

    private final Map<String, CliConfigGenerator> configGeneratorRegistry;

    public ConfigFileBuilder(Map<String, CliConfigGenerator> configGeneratorRegistry) {
        this.configGeneratorRegistry = configGeneratorRegistry;
    }

    /**
     * 构建配置文件列表。
     *
     * @param resolved 已解析的会话配置
     * @param providerKey CLI 提供者标识
     * @param providerConfig CLI 提供者配置
     * @param runtimeConfig 运行时配置（额外环境变量会被合并到此对象）
     * @return 配置文件列表（含路径、内容、哈希）
     */
    public List<ConfigFile> build(
            ResolvedSessionConfig resolved,
            String providerKey,
            CliProviderConfig providerConfig,
            RuntimeConfig runtimeConfig) {

        CliConfigGenerator generator = configGeneratorRegistry.get(providerKey);
        if (generator == null) {
            logger.warn("[ConfigFileBuilder] 未找到 CliConfigGenerator: providerKey={}", providerKey);
            return List.of();
        }

        List<ConfigFile> configFiles = new ArrayList<>();
        Path tempDir = null;
        try {
            tempDir = Files.createTempDirectory("sandbox-config-");

            // 1. 模型配置
            generateModelConfig(generator, tempDir, resolved, runtimeConfig, providerKey);

            // 2. MCP 配置
            if (resolved.getMcpServers() != null
                    && !resolved.getMcpServers().isEmpty()
                    && providerConfig.isSupportsMcp()) {
                generateMcpConfig(generator, tempDir, resolved, providerKey);
            }

            // 3. Skill 配置
            if (resolved.getSkills() != null
                    && !resolved.getSkills().isEmpty()
                    && providerConfig.isSupportsSkill()) {
                generateSkillConfig(generator, tempDir, resolved, providerKey);
            }

            // 4. 收集所有生成的文件
            configFiles = collectConfigFiles(tempDir);

        } catch (Exception e) {
            logger.error(
                    "[ConfigFileBuilder] 配置文件构建失败: provider={}, error={}",
                    providerKey,
                    e.getMessage(),
                    e);
        } finally {
            if (tempDir != null) {
                deleteDirectory(tempDir);
            }
        }
        return configFiles;
    }

    private void generateModelConfig(
            CliConfigGenerator generator,
            Path tempDir,
            ResolvedSessionConfig resolved,
            RuntimeConfig runtimeConfig,
            String providerKey) {
        if (resolved.getCustomModelConfig() == null) {
            return;
        }
        try {
            Map<String, String> extraEnv =
                    generator.generateConfig(tempDir.toString(), resolved.getCustomModelConfig());
            if (extraEnv != null && !extraEnv.isEmpty()) {
                if (runtimeConfig.getEnv() == null) {
                    runtimeConfig.setEnv(new HashMap<>());
                }
                runtimeConfig.getEnv().putAll(extraEnv);
            }
            logger.info(
                    "[ConfigFileBuilder] 模型配置已准备: provider={}, baseUrl={}, modelId={}",
                    providerKey,
                    resolved.getCustomModelConfig().getBaseUrl(),
                    resolved.getCustomModelConfig().getModelId());
        } catch (Exception e) {
            logger.error(
                    "[ConfigFileBuilder] 模型配置生成失败: provider={}, error={}",
                    providerKey,
                    e.getMessage(),
                    e);
        }
    }

    private void generateMcpConfig(
            CliConfigGenerator generator,
            Path tempDir,
            ResolvedSessionConfig resolved,
            String providerKey) {
        try {
            generator.generateMcpConfig(tempDir.toString(), resolved.getMcpServers());
            logger.info(
                    "[ConfigFileBuilder] MCP 配置已准备: provider={}, {} server(s)",
                    providerKey,
                    resolved.getMcpServers().size());
        } catch (Exception e) {
            logger.error(
                    "[ConfigFileBuilder] MCP 配置生成失败: provider={}, error={}",
                    providerKey,
                    e.getMessage(),
                    e);
        }
    }

    private void generateSkillConfig(
            CliConfigGenerator generator,
            Path tempDir,
            ResolvedSessionConfig resolved,
            String providerKey) {
        try {
            generator.generateSkillConfig(tempDir.toString(), resolved.getSkills());
            logger.info(
                    "[ConfigFileBuilder] Skill 配置已准备: provider={}, {} skill(s)",
                    providerKey,
                    resolved.getSkills().size());
        } catch (Exception e) {
            logger.error(
                    "[ConfigFileBuilder] Skill 配置生成失败: provider={}, error={}",
                    providerKey,
                    e.getMessage(),
                    e);
        }
    }

    private List<ConfigFile> collectConfigFiles(Path tempDir) throws IOException {
        List<ConfigFile> configFiles = new ArrayList<>();
        Files.walk(tempDir)
                .filter(Files::isRegularFile)
                .forEach(
                        file -> {
                            try {
                                String relativePath = tempDir.relativize(file).toString();
                                String content = Files.readString(file);
                                String hash = sha256(content);
                                ConfigFile.ConfigType type = inferConfigType(relativePath);
                                configFiles.add(new ConfigFile(relativePath, content, hash, type));
                            } catch (IOException e) {
                                logger.warn("[ConfigFileBuilder] 读取配置文件失败: {}", e.getMessage());
                            }
                        });
        return configFiles;
    }

    /**
     * 根据文件相对路径推断配置类型。
     */
    static ConfigFile.ConfigType inferConfigType(String relativePath) {
        // .nacos/ 目录下的 yaml 文件识别为 SKILL_CONFIG
        if (relativePath.startsWith(".nacos/") && relativePath.endsWith(".yaml")) {
            return ConfigFile.ConfigType.SKILL_CONFIG;
        }
        if (relativePath.contains("skills") && relativePath.endsWith("SKILL.md")) {
            return ConfigFile.ConfigType.SKILL_CONFIG;
        }
        if (relativePath.endsWith("settings.json")) {
            // settings.json 现在包含合并后的模型+MCP配置，标记为 MODEL_SETTINGS
            return ConfigFile.ConfigType.MODEL_SETTINGS;
        }
        return ConfigFile.ConfigType.CUSTOM;
    }

    /**
     * 计算内容的 SHA-256 哈希值。
     */
    static String sha256(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 不可用", e);
        }
    }

    private void deleteDirectory(Path dir) {
        try {
            Files.walk(dir)
                    .sorted(Comparator.reverseOrder())
                    .forEach(
                            path -> {
                                try {
                                    Files.deleteIfExists(path);
                                } catch (IOException ignored) {
                                }
                            });
        } catch (IOException ignored) {
        }
    }
}
