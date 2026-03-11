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
 * QoderCli 工具的配置文件生成器。
 * 生成 .qoder/settings.json 文件到工作目录下的 .qoder/ 子目录，
 * 将 MCP Server 配置写入 mcpServers 字段。
 * 支持与已有 .qoder/settings.json 合并，保留用户已有的其他配置项。
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
        // QoderCli 不支持自定义模型配置，返回空 map
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

    /**
     * 读取已有的 .qoder/settings.json 配置文件。
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
            logger.warn("已有 .qoder/settings.json 不是合法 JSON，将使用全新配置覆盖: {}", e.getMessage());
            return new LinkedHashMap<>();
        }
    }

    /**
     * 将配置写入 .qoder/settings.json 文件。
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
