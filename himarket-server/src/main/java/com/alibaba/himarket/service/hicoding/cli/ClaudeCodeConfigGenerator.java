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
 * Configuration generator for Claude Code CLI.
 * Generates .claude/settings.json under the working directory and writes MCP server configuration
 * to {workingDirectory}/.mcp.json. Existing configuration is merged to preserve unrelated user
 * settings.
 */
public class ClaudeCodeConfigGenerator implements CliConfigGenerator {

    private static final Logger logger = LoggerFactory.getLogger(ClaudeCodeConfigGenerator.class);

    private static final String CLAUDE_DIR = ".claude";
    private static final String CONFIG_FILE_NAME = "settings.json";

    private final ObjectMapper objectMapper;

    public ClaudeCodeConfigGenerator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String supportedProvider() {
        return "claude-code";
    }

    @Override
    public Map<String, String> generateConfig(String workingDirectory, CustomModelConfig config)
            throws IOException {
        // Step 1: Build environment variables.
        Map<String, String> envVars = new HashMap<>();
        envVars.put("ANTHROPIC_API_KEY", config.getApiKey());
        envVars.put("ANTHROPIC_BASE_URL", config.getBaseUrl());
        envVars.put("ANTHROPIC_MODEL", config.getModelId());

        // Step 2: Create .claude/.
        Path claudeDir = Path.of(workingDirectory, CLAUDE_DIR);
        Files.createDirectories(claudeDir);

        // Step 3: Read existing settings.json and preserve unrelated fields.
        Path configPath = claudeDir.resolve(CONFIG_FILE_NAME);
        Map<String, Object> root = readExistingConfig(configPath);

        // Step 4: Merge the declarative env section.
        @SuppressWarnings("unchecked")
        Map<String, Object> envSection =
                root.containsKey("env")
                        ? (Map<String, Object>) root.get("env")
                        : new LinkedHashMap<>();
        envSection.put("ANTHROPIC_API_KEY", config.getApiKey());
        envSection.put("ANTHROPIC_BASE_URL", config.getBaseUrl());
        envSection.put("ANTHROPIC_MODEL", config.getModelId());
        root.put("env", envSection);

        // Step 5: Set the model field.
        root.put("model", config.getModelId());

        // Step 6: Write settings.json.
        writeConfig(configPath, root);

        return envVars;
    }

    @Override
    public void generateMcpConfig(
            String workingDirectory, List<ResolvedSessionConfig.ResolvedMcpEntry> mcpServers)
            throws IOException {
        if (mcpServers == null || mcpServers.isEmpty()) return;

        Path mcpConfigPath = Path.of(workingDirectory, ".mcp.json");

        Map<String, Object> root = readExistingConfig(mcpConfigPath);
        mergeMcpServers(root, mcpServers);
        writeConfig(mcpConfigPath, root);
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

    @Override
    public String skillsDirectory() {
        return ".claude/skills/";
    }

    /**
     * Reads an existing JSON configuration file.
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
                    "Existing configuration file is not valid JSON and will be overwritten,"
                            + " errorMessage={}",
                    e.getMessage());
            return new LinkedHashMap<>();
        }
    }

    /**
     * Writes the JSON configuration file.
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
