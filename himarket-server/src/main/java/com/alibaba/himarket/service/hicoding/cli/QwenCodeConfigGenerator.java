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
 * Configuration generator for Qwen Code CLI.
 * Generates .qwen/settings.json under the working directory and writes modelProviders and env
 * fields. Existing .qwen/settings.json content is merged so unrelated user settings are preserved.
 */
public class QwenCodeConfigGenerator implements CliConfigGenerator {

    private static final Logger logger = LoggerFactory.getLogger(QwenCodeConfigGenerator.class);

    private static final String QWEN_DIR = ".qwen";
    private static final String CONFIG_FILE_NAME = "settings.json";

    /**
     * Mapping from protocol type to API key environment variable name.
     */
    private static final Map<String, String> PROTOCOL_ENV_KEY_MAP =
            Map.of(
                    "openai", "OPENAI_API_KEY",
                    "anthropic", "ANTHROPIC_API_KEY",
                    "gemini", "GOOGLE_API_KEY");

    private final ObjectMapper objectMapper;

    public QwenCodeConfigGenerator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String supportedProvider() {
        return "qwen-code";
    }

    @Override
    public Map<String, String> generateConfig(String workingDirectory, CustomModelConfig config)
            throws IOException {
        Path qwenDir = Path.of(workingDirectory, QWEN_DIR);
        Path configPath = qwenDir.resolve(CONFIG_FILE_NAME);

        // 1. Create .qwen/.
        Files.createDirectories(qwenDir);

        // 2. Read existing settings.json when present.
        Map<String, Object> root = readExistingConfig(configPath);

        // 3. Merge custom modelProviders configuration.
        mergeCustomModelProvider(root, config);

        // 4. Write .qwen/settings.json.
        writeConfig(configPath, root);

        // 5. Return the corresponding environment variables.
        String envKey = getEnvKeyForProtocol(config.getProtocolType());
        Map<String, String> envVars = new HashMap<>();
        envVars.put(envKey, config.getApiKey());
        return envVars;
    }

    @Override
    public void generateMcpConfig(
            String workingDirectory, List<ResolvedSessionConfig.ResolvedMcpEntry> mcpServers)
            throws IOException {
        if (mcpServers == null || mcpServers.isEmpty()) return;

        Path qwenDir = Path.of(workingDirectory, QWEN_DIR);
        Path configPath = qwenDir.resolve(CONFIG_FILE_NAME);
        Files.createDirectories(qwenDir);

        Map<String, Object> root = readExistingConfig(configPath);
        mergeMcpServers(root, mcpServers);
        writeConfig(configPath, root);
    }

    @Override
    public String skillsDirectory() {
        return ".qwen/skills/";
    }

    /**
     * Merges MCP servers into the root mcpServers section.
     * Uses name as the deduplication key; new entries override existing entries with the same name.
     */
    @SuppressWarnings("unchecked")
    void mergeMcpServers(
            Map<String, Object> root, List<ResolvedSessionConfig.ResolvedMcpEntry> mcpServers) {
        Map<String, Object> mcpServersMap =
                root.containsKey("mcpServers")
                        ? (Map<String, Object>) root.get("mcpServers")
                        : new LinkedHashMap<>();

        for (ResolvedSessionConfig.ResolvedMcpEntry entry : mcpServers) {
            Map<String, Object> serverConfig = new LinkedHashMap<>();
            serverConfig.put("url", entry.getUrl());
            serverConfig.put("type", entry.getTransportType());
            if (entry.getHeaders() != null && !entry.getHeaders().isEmpty()) {
                serverConfig.put("headers", entry.getHeaders());
            }
            mcpServersMap.put(entry.getName(), serverConfig);
        }

        root.put("mcpServers", mcpServersMap);
    }

    /**
     * Reads the existing .qwen/settings.json file.
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
                    "Existing .qwen/settings.json is not valid JSON and will be overwritten,"
                            + " errorMessage={}",
                    e.getMessage());
            return new LinkedHashMap<>();
        }
    }

    /**
     * Merges custom model configuration into root modelProviders and env.
     * Preserves unrelated providers and replaces an existing model with the same ID.
     */
    @SuppressWarnings("unchecked")
    void mergeCustomModelProvider(Map<String, Object> root, CustomModelConfig config) {
        String protocolType = config.getProtocolType();
        String envKey = getEnvKeyForProtocol(protocolType);
        String modelName =
                (config.getModelName() != null && !config.getModelName().isBlank())
                        ? config.getModelName()
                        : config.getModelId();

        // Build the model entry.
        Map<String, Object> modelEntry = new LinkedHashMap<>();
        modelEntry.put("id", config.getModelId());
        modelEntry.put("name", modelName);
        modelEntry.put("envKey", envKey);
        modelEntry.put("baseUrl", config.getBaseUrl());

        // Merge into modelProviders while preserving existing providers.
        Map<String, Object> modelProviders =
                root.containsKey("modelProviders")
                        ? (Map<String, Object>) root.get("modelProviders")
                        : new LinkedHashMap<>();

        // Get or create the provider list for the protocol type.
        List<Map<String, Object>> providerList =
                modelProviders.containsKey(protocolType)
                        ? (List<Map<String, Object>>) modelProviders.get(protocolType)
                        : new java.util.ArrayList<>();

        // Replace an existing model entry with the same ID.
        providerList.removeIf(entry -> config.getModelId().equals(entry.get("id")));

        providerList.add(modelEntry);
        modelProviders.put(protocolType, providerList);
        root.put("modelProviders", modelProviders);

        // Set security.auth.selectedType so Qwen CLI skips the interactive login prompt.
        Map<String, Object> security =
                root.containsKey("security")
                        ? (Map<String, Object>) root.get("security")
                        : new LinkedHashMap<>();
        Map<String, Object> auth =
                security.containsKey("auth")
                        ? (Map<String, Object>) security.get("auth")
                        : new LinkedHashMap<>();
        auth.put("selectedType", protocolType);
        security.put("auth", auth);
        root.put("security", security);

        // Set model.name as the default model.
        Map<String, Object> model =
                root.containsKey("model")
                        ? (Map<String, Object>) root.get("model")
                        : new LinkedHashMap<>();
        model.put("name", config.getModelId());
        root.put("model", model);

        // Auto-approve tool calls in the sandbox to avoid non-interactive confirmation prompts.
        Map<String, Object> tools =
                root.containsKey("tools")
                        ? (Map<String, Object>) root.get("tools")
                        : new LinkedHashMap<>();
        tools.put("approvalMode", "yolo");
        root.put("tools", tools);
    }

    /**
     * Returns the API key environment variable name for the protocol type.
     */
    static String getEnvKeyForProtocol(String protocolType) {
        return PROTOCOL_ENV_KEY_MAP.getOrDefault(protocolType, "OPENAI_API_KEY");
    }

    /**
     * Writes .qwen/settings.json.
     */
    private void writeConfig(Path configPath, Map<String, Object> root) throws IOException {
        String json =
                objectMapper
                        .writer()
                        .with(SerializationFeature.INDENT_OUTPUT)
                        .writeValueAsString(root);
        Files.writeString(configPath, json);
    }
}
