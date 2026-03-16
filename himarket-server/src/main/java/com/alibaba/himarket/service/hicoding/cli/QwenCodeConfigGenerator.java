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
 * Qwen Code CLI 工具的配置文件生成器。
 * 生成 .qwen/settings.json 文件到工作目录下的 .qwen/ 子目录，
 * 包含 modelProviders 和 env 字段。
 * 支持与已有 .qwen/settings.json 合并，保留用户已有的其他配置项。
 */
public class QwenCodeConfigGenerator implements CliConfigGenerator {

    private static final Logger logger = LoggerFactory.getLogger(QwenCodeConfigGenerator.class);

    private static final String QWEN_DIR = ".qwen";
    private static final String CONFIG_FILE_NAME = "settings.json";

    /** 协议类型到环境变量名的映射 */
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

        // 1. 创建 .qwen/ 目录
        Files.createDirectories(qwenDir);

        // 2. 读取已有 settings.json（如存在）
        Map<String, Object> root = readExistingConfig(configPath);

        // 3. 合并自定义 modelProviders 配置
        mergeCustomModelProvider(root, config);

        // 4. 写入 .qwen/settings.json
        writeConfig(configPath, root);

        // 5. 返回对应的环境变量 map
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
     * 读取已有的 .qwen/settings.json 配置文件。
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
            logger.warn("已有 .qwen/settings.json 不是合法 JSON，将使用全新配置覆盖: {}", e.getMessage());
            return new LinkedHashMap<>();
        }
    }

    /**
     * 将自定义模型配置合并到根配置的 modelProviders 和 env 中。
     * 保留已有的其他 provider 条目，在对应 protocolType 的 provider 列表中追加或替换模型。
     * 如果已存在相同 id 的模型，用新配置替换它（用户明确想用自定义接入点）。
     */
    @SuppressWarnings("unchecked")
    void mergeCustomModelProvider(Map<String, Object> root, CustomModelConfig config) {
        String protocolType = config.getProtocolType();
        String envKey = getEnvKeyForProtocol(protocolType);
        String modelName =
                (config.getModelName() != null && !config.getModelName().isBlank())
                        ? config.getModelName()
                        : config.getModelId();

        // 构建模型条目
        Map<String, Object> modelEntry = new LinkedHashMap<>();
        modelEntry.put("id", config.getModelId());
        modelEntry.put("name", modelName);
        modelEntry.put("envKey", envKey);
        modelEntry.put("baseUrl", config.getBaseUrl());

        // 合并到 modelProviders（保留已有 provider）
        Map<String, Object> modelProviders =
                root.containsKey("modelProviders")
                        ? (Map<String, Object>) root.get("modelProviders")
                        : new LinkedHashMap<>();

        // 获取或创建对应 protocolType 的 provider 列表
        List<Map<String, Object>> providerList =
                modelProviders.containsKey(protocolType)
                        ? (List<Map<String, Object>>) modelProviders.get(protocolType)
                        : new java.util.ArrayList<>();

        // 移除已有的相同 id 的模型条目（避免重复，用新配置替换）
        providerList.removeIf(entry -> config.getModelId().equals(entry.get("id")));

        providerList.add(modelEntry);
        modelProviders.put(protocolType, providerList);
        root.put("modelProviders", modelProviders);

        // 设置 security.auth.selectedType，告诉 qwen CLI 已选择认证方式，跳过登录交互
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

        // 设置 model.name，指定默认使用的模型
        Map<String, Object> model =
                root.containsKey("model")
                        ? (Map<String, Object>) root.get("model")
                        : new LinkedHashMap<>();
        model.put("name", config.getModelId());
        root.put("model", model);

        // 沙箱环境设置 tools.approvalMode 为 yolo，自动批准所有工具调用，避免非交互模式下卡在确认提示
        Map<String, Object> tools =
                root.containsKey("tools")
                        ? (Map<String, Object>) root.get("tools")
                        : new LinkedHashMap<>();
        tools.put("approvalMode", "yolo");
        root.put("tools", tools);
    }

    /**
     * 根据协议类型获取对应的环境变量名。
     */
    static String getEnvKeyForProtocol(String protocolType) {
        return PROTOCOL_ENV_KEY_MAP.getOrDefault(protocolType, "OPENAI_API_KEY");
    }

    /**
     * 将配置写入 .qwen/settings.json 文件。
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
