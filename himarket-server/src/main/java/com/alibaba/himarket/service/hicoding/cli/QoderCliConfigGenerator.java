package com.alibaba.himarket.service.hicoding.cli;

import com.alibaba.himarket.service.hicoding.session.CustomModelConfig;
import com.alibaba.himarket.service.hicoding.session.ResolvedSessionConfig;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Configuration generator for QoderCli.
 * Generates .qoder/settings.json under the working directory and writes MCP server configuration
 * into mcpServers. Existing .qoder/settings.json content is merged to preserve unrelated user
 * settings.
 */
public class QoderCliConfigGenerator implements CliConfigGenerator {

    private static final Logger logger = LoggerFactory.getLogger(QoderCliConfigGenerator.class);

    private static final String QODER_DIR = ".qoder";
    private static final String CONFIG_FILE_NAME = "settings.json";

    private final ObjectMapper objectMapper;

    public QoderCliConfigGenerator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String supportedProvider() {
        return "qodercli";
    }

    @Override
    public Map<String, String> generateConfig(String workingDirectory, CustomModelConfig config)
            throws IOException {
        // QoderCli does not support custom model configuration.
        return Map.of();
    }

    @Override
    public void generateMcpConfig(
            String workingDirectory, List<ResolvedSessionConfig.ResolvedMcpEntry> mcpServers)
            throws IOException {
        if (mcpServers == null || mcpServers.isEmpty()) return;

        Path qoderDir = Path.of(workingDirectory, QODER_DIR);
        Path configPath = qoderDir.resolve(CONFIG_FILE_NAME);
        Files.createDirectories(qoderDir);

        Map<String, Object> root = readExistingConfig(configPath);
        mergeMcpServers(root, mcpServers);
        writeConfig(configPath, root);
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
     * Reads the existing .qoder/settings.json file.
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
                    "Existing .qoder/settings.json is not valid JSON and will be overwritten,"
                            + " errorMessage={}",
                    e.getMessage());
            return new LinkedHashMap<>();
        }
    }

    /**
     * Writes .qoder/settings.json.
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
    public String skillsDirectory() {
        return ".qoder/skills/";
    }
}
