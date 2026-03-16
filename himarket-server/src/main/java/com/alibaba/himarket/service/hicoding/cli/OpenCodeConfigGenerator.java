package com.alibaba.himarket.service.hicoding.cli;

import com.alibaba.himarket.service.hicoding.session.CustomModelConfig;
import com.alibaba.himarket.service.hicoding.session.ResolvedSessionConfig;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Open Code CLI 工具的配置文件生成器。
 * 生成 opencode.json 文件到工作目录，包含自定义 provider 定义和 model 指定。
 * 支持与已有 opencode.json 合并，保留用户已有的其他配置项。
 * 支持 MCP Server 和 Skill 配置。
 */
public class OpenCodeConfigGenerator implements CliConfigGenerator {

    private static final Logger logger = LoggerFactory.getLogger(OpenCodeConfigGenerator.class);

    private static final String CONFIG_FILE_NAME = "opencode.json";
    private static final String OPENCODE_DIR = ".opencode";
    private static final String PROVIDER_KEY = "custom-provider";
    private static final String NPM_PACKAGE = "@ai-sdk/openai-compatible";
    private static final String API_KEY_ENV_REF = "{env:CUSTOM_MODEL_API_KEY}";
    private static final String ENV_VAR_NAME = "CUSTOM_MODEL_API_KEY";

    private final ObjectMapper objectMapper;

    public OpenCodeConfigGenerator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String supportedProvider() {
        return "opencode";
    }

    @Override
    public Map<String, String> generateConfig(String workingDirectory, CustomModelConfig config)
            throws IOException {
        Path configPath = Path.of(workingDirectory, CONFIG_FILE_NAME);

        // 1. 读取已有配置（如存在）
        Map<String, Object> root = readExistingConfig(configPath);

        // 2. 合并自定义 provider 配置
        mergeCustomProvider(root, config);

        // 3. 写入 opencode.json
        writeConfig(configPath, root);

        // 4. 返回环境变量 map
        Map<String, String> envVars = new HashMap<>();
        envVars.put(ENV_VAR_NAME, config.getApiKey());
        return envVars;
    }

    /**
     * 读取已有的 opencode.json 配置文件。
     * 如果文件不存在，返回空 map。
     * 如果文件内容不是合法 JSON，记录警告并返回空 map（后续会覆盖）。
     */
    Map<String, Object> readExistingConfig(Path configPath) {
        if (!Files.exists(configPath)) {
            return new LinkedHashMap<>();
        }
        try {
            String content = Files.readString(configPath);
            Map<String, Object> existing =
                    objectMapper.readValue(
                            content, new TypeReference<LinkedHashMap<String, Object>>() {});
            return existing != null ? existing : new LinkedHashMap<>();
        } catch (Exception e) {
            logger.warn("已有 opencode.json 不是合法 JSON，将使用全新配置覆盖: {}", e.getMessage());
            return new LinkedHashMap<>();
        }
    }

    /**
     * 将自定义 provider 配置合并到根配置中。
     * 保留已有的其他 provider 条目，新增或覆盖 custom-provider。
     */
    @SuppressWarnings("unchecked")
    void mergeCustomProvider(Map<String, Object> root, CustomModelConfig config) {
        String modelName =
                (config.getModelName() != null && !config.getModelName().isBlank())
                        ? config.getModelName()
                        : config.getModelId();

        // 构建 custom-provider 的 options
        Map<String, Object> options = new LinkedHashMap<>();
        options.put("baseURL", config.getBaseUrl());
        options.put("apiKey", API_KEY_ENV_REF);

        // 构建 model 条目
        Map<String, Object> modelEntry = new LinkedHashMap<>();
        modelEntry.put("name", modelName);

        Map<String, Object> models = new LinkedHashMap<>();
        models.put(config.getModelId(), modelEntry);

        // 构建 custom-provider
        Map<String, Object> customProvider = new LinkedHashMap<>();
        customProvider.put("npm", NPM_PACKAGE);
        customProvider.put("name", modelName);
        customProvider.put("options", options);
        customProvider.put("models", models);

        // 合并到 provider map（保留已有 provider）
        Map<String, Object> providers =
                root.containsKey("provider")
                        ? (Map<String, Object>) root.get("provider")
                        : new LinkedHashMap<>();
        providers.put(PROVIDER_KEY, customProvider);
        root.put("provider", providers);

        // 设置 model 字段
        root.put("model", PROVIDER_KEY + "/" + config.getModelId());
    }

    /**
     * 将配置写入 opencode.json 文件。
     */
    private void writeConfig(Path configPath, Map<String, Object> root) throws IOException {
        String json =
                objectMapper
                        .writer()
                        .with(SerializationFeature.INDENT_OUTPUT)
                        .writeValueAsString(root);
        Files.writeString(configPath, json);
    }

    @Override
    public void generateMcpConfig(
            String workingDirectory, List<ResolvedSessionConfig.ResolvedMcpEntry> mcpServers)
            throws IOException {
        if (mcpServers == null || mcpServers.isEmpty()) return;

        Path configPath = Path.of(workingDirectory, CONFIG_FILE_NAME);
        Map<String, Object> root = readExistingConfig(configPath);
        mergeMcpServers(root, mcpServers);
        writeConfig(configPath, root);
    }

    @Override
    public String skillsDirectory() {
        return ".opencode/skills/";
    }

    /**
     * 将 MCP Server 列表合并到根配置的 mcp 段中。
     * OpenCode 使用 "mcp" 字段（不是 "mcpServers"），格式为：
     * {
     *   "mcp": {
     *     "server-name": {
     *       "type": "remote",
     *       "url": "https://...",
     *       "headers": { ... }
     *     }
     *   }
     * }
     */
    @SuppressWarnings("unchecked")
    void mergeMcpServers(
            Map<String, Object> root, List<ResolvedSessionConfig.ResolvedMcpEntry> mcpServers) {
        Map<String, Object> mcpMap =
                root.containsKey("mcp")
                        ? (Map<String, Object>) root.get("mcp")
                        : new LinkedHashMap<>();

        for (ResolvedSessionConfig.ResolvedMcpEntry entry : mcpServers) {
            Map<String, Object> serverConfig = new LinkedHashMap<>();
            // OpenCode 使用 "remote" 类型表示远程 MCP 服务器
            serverConfig.put("type", "remote");
            serverConfig.put("url", entry.getUrl());
            if (entry.getHeaders() != null && !entry.getHeaders().isEmpty()) {
                serverConfig.put("headers", entry.getHeaders());
            }
            // 默认启用
            serverConfig.put("enabled", true);
            mcpMap.put(entry.getName(), serverConfig);
        }

        root.put("mcp", mcpMap);
    }
}
