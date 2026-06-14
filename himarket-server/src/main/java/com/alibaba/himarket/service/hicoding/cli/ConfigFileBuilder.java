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
 * Builds CLI configuration files.
 *
 * <p>Converts {@link ResolvedSessionConfig} into {@link ConfigFile} entries. Encapsulates {@link
 * CliConfigGenerator} calls, temporary directory management, and file collection.
 *
 * <p>Extracted from {@code HiCodingWebSocketHandler.prepareConfigFiles()}.
 */
@Component
public class ConfigFileBuilder {

    private static final Logger logger = LoggerFactory.getLogger(ConfigFileBuilder.class);

    private final Map<String, CliConfigGenerator> configGeneratorRegistry;

    public ConfigFileBuilder(Map<String, CliConfigGenerator> configGeneratorRegistry) {
        this.configGeneratorRegistry = configGeneratorRegistry;
    }

    /**
     * Builds configuration files.
     *
     * @param resolved       resolved session configuration
     * @param providerKey    CLI provider key
     * @param providerConfig CLI provider configuration
     * @param runtimeConfig  runtime configuration; extra environment variables are merged into it
     * @return configuration files with path, content, and hash
     */
    public List<ConfigFile> build(
            ResolvedSessionConfig resolved,
            String providerKey,
            CliProviderConfig providerConfig,
            RuntimeConfig runtimeConfig) {

        CliConfigGenerator generator = configGeneratorRegistry.get(providerKey);
        if (generator == null) {
            logger.warn("CLI config generator not found, providerKey={}", providerKey);
            return List.of();
        }

        List<ConfigFile> configFiles = new ArrayList<>();
        Path tempDir = null;
        try {
            tempDir = Files.createTempDirectory("sandbox-config-");

            // 1. Model configuration.
            generateModelConfig(generator, tempDir, resolved, runtimeConfig, providerKey);

            // 2. MCP configuration.
            if (resolved.getMcpServers() != null
                    && !resolved.getMcpServers().isEmpty()
                    && providerConfig.isSupportsMcp()) {
                generateMcpConfig(generator, tempDir, resolved, providerKey);
            }

            // 3. Skill configuration.
            if (resolved.getSkills() != null
                    && !resolved.getSkills().isEmpty()
                    && providerConfig.isSupportsSkill()) {
                generateSkillConfig(generator, tempDir, resolved, providerKey);
            }

            // 4. Collect generated files.
            configFiles = collectConfigFiles(tempDir);

        } catch (Exception e) {
            logger.error(
                    "Failed to build config files, provider={}, errorMessage={}",
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
                    "Model config prepared, provider={}, baseUrl={}, modelId={}",
                    providerKey,
                    resolved.getCustomModelConfig().getBaseUrl(),
                    resolved.getCustomModelConfig().getModelId());
        } catch (Exception e) {
            logger.error(
                    "Failed to generate model config, provider={}, errorMessage={}",
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
                    "MCP config prepared, provider={}, serverCount={}",
                    providerKey,
                    resolved.getMcpServers().size());
        } catch (Exception e) {
            logger.error(
                    "Failed to generate MCP config, provider={}, errorMessage={}",
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
                    "Skill config prepared, provider={}, skillCount={}",
                    providerKey,
                    resolved.getSkills().size());
        } catch (Exception e) {
            logger.error(
                    "Failed to generate skill config, provider={}, errorMessage={}",
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
                                logger.warn(
                                        "Failed to read config file, path={}, errorMessage={}",
                                        file,
                                        e.getMessage(),
                                        e);
                            }
                        });
        return configFiles;
    }

    /**
     * Infers the configuration type from a relative file path.
     */
    static ConfigFile.ConfigType inferConfigType(String relativePath) {
        // Treat .nacos/*.yaml files as skill configuration.
        if (relativePath.startsWith(".nacos/") && relativePath.endsWith(".yaml")) {
            return ConfigFile.ConfigType.SKILL_CONFIG;
        }
        if (relativePath.contains("skills") && relativePath.endsWith("SKILL.md")) {
            return ConfigFile.ConfigType.SKILL_CONFIG;
        }
        if (relativePath.endsWith("settings.json")) {
            // settings.json contains merged model and MCP configuration.
            return ConfigFile.ConfigType.MODEL_SETTINGS;
        }
        return ConfigFile.ConfigType.CUSTOM;
    }

    /**
     * Computes the SHA-256 hash of the content.
     */
    static String sha256(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 is not available", e);
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
