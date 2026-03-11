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
 * Claude Code CLI 工具的配置文件生成器。
 * 生成 .claude/settings.json 文件到工作目录下的 .claude/ 子目录，
 * 将 MCP Server 配置写入 {workingDirectory}/.mcp.json 文件（官方推荐路径）。
 * 支持与已有配置文件合并，保留用户已有的其他配置项。
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
        // Step 1: 构建环境变量 Map
        Map<String, String> envVars = new HashMap<>();
        envVars.put("ANTHROPIC_API_KEY", config.getApiKey());
        envVars.put("ANTHROPIC_BASE_URL", config.getBaseUrl());
        envVars.put("ANTHROPIC_MODEL", config.getModelId());

        // Step 2: 创建 .claude 目录
        Path claudeDir = Path.of(workingDirectory, CLAUDE_DIR);
        Files.createDirectories(claudeDir);

        // Step 3: 读取已有 settings.json（保留 mcpServers 等字段）
        Path configPath = claudeDir.resolve(CONFIG_FILE_NAME);
        Map<String, Object> root = readExistingConfig(configPath);

        // Step 4: 合并 env 字段（声明式环境变量）
        @SuppressWarnings("unchecked")
        Map<String, Object> envSection =
                root.containsKey("env")
                        ? (Map<String, Object>) root.get("env")
                        : new LinkedHashMap<>();
        envSection.put("ANTHROPIC_API_KEY", config.getApiKey());
        envSection.put("ANTHROPIC_BASE_URL", config.getBaseUrl());
        envSection.put("ANTHROPIC_MODEL", config.getModelId());
        root.put("env", envSection);

        // Step 5: 设置 model 字段
        root.put("model", config.getModelId());

        // Step 6: 写入 settings.json
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
     * 将 MCP Server 列表合并到根配置的 mcpServers 段中。
     * 按 name 去重，新条目覆盖同名旧条目。
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
     * 读取已有的 JSON 配置文件。
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
            logger.warn("已有配置文件不是合法 JSON，将使用全新配置覆盖: {}", e.getMessage());
            return new LinkedHashMap<>();
        }
    }

    /**
     * 将配置写入指定的 JSON 配置文件。
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
