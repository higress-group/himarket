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
 * Configuration generator for Open Code CLI.
 * Generates opencode.json under the working directory with a custom provider and selected model.
 * Existing opencode.json content is merged to preserve unrelated user settings. MCP server and
 * Skill configuration are supported.
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

        // 1. Read existing configuration when present.
        Map<String, Object> root = readExistingConfig(configPath);

        // 2. Merge custom provider configuration.
        mergeCustomProvider(root, config);

        // 3. Write opencode.json.
        writeConfig(configPath, root);

        // 4. Return environment variables.
        Map<String, String> envVars = new HashMap<>();
        envVars.put(ENV_VAR_NAME, config.getApiKey());
        return envVars;
    }

    /**
     * Reads the existing opencode.json file.
     * Returns an empty map when the file does not exist or cannot be parsed.
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
            logger.warn(
                    "Existing opencode.json is not valid JSON and will be overwritten,"
                            + " errorMessage={}",
                    e.getMessage());
            return new LinkedHashMap<>();
        }
    }

    /**
     * Merges custom provider configuration into the root object.
     * Preserves unrelated providers and adds or replaces custom-provider.
     */
    @SuppressWarnings("unchecked")
    void mergeCustomProvider(Map<String, Object> root, CustomModelConfig config) {
        String modelName =
                (config.getModelName() != null && !config.getModelName().isBlank())
                        ? config.getModelName()
                        : config.getModelId();

        // Build custom-provider options.
        Map<String, Object> options = new LinkedHashMap<>();
        options.put("baseURL", config.getBaseUrl());
        options.put("apiKey", API_KEY_ENV_REF);

        // Build the model entry.
        Map<String, Object> modelEntry = new LinkedHashMap<>();
        modelEntry.put("name", modelName);

        Map<String, Object> models = new LinkedHashMap<>();
        models.put(config.getModelId(), modelEntry);

        // Build custom-provider.
        Map<String, Object> customProvider = new LinkedHashMap<>();
        customProvider.put("npm", NPM_PACKAGE);
        customProvider.put("name", modelName);
        customProvider.put("options", options);
        customProvider.put("models", models);

        // Merge into provider map while preserving existing providers.
        Map<String, Object> providers =
                root.containsKey("provider")
                        ? (Map<String, Object>) root.get("provider")
                        : new LinkedHashMap<>();
        providers.put(PROVIDER_KEY, customProvider);
        root.put("provider", providers);

        // Set the model field.
        root.put("model", PROVIDER_KEY + "/" + config.getModelId());
    }

    /**
     * Writes opencode.json.
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
     * Merges MCP servers into the root mcp section.
     * OpenCode uses the "mcp" field instead of "mcpServers":
     * {
     * "mcp": {
     * "server-name": {
     * "type": "remote",
     * "url": "https://...",
     * "headers": { ... }
     * }
     * }
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
            // OpenCode uses "remote" for remote MCP servers.
            serverConfig.put("type", "remote");
            serverConfig.put("url", entry.getUrl());
            if (entry.getHeaders() != null && !entry.getHeaders().isEmpty()) {
                serverConfig.put("headers", entry.getHeaders());
            }
            // Enable by default.
            serverConfig.put("enabled", true);
            mcpMap.put(entry.getName(), serverConfig);
        }

        root.put("mcp", mcpMap);
    }
}
