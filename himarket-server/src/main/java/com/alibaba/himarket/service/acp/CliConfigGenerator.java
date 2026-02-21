package com.alibaba.himarket.service.acp;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * CLI 工具的自定义模型配置文件生成器接口。
 * 每个支持自定义模型的 CLI 工具（如 Open Code、Qwen Code）提供各自的实现，
 * 负责生成对应格式的配置文件并返回需要注入到 CLI 进程的额外环境变量。
 */
public interface CliConfigGenerator {

    /**
     * 该生成器支持的 provider key（如 "opencode"、"qwen-code"）。
     *
     * @return provider key
     */
    String supportedProvider();

    /**
     * 生成配置文件并返回需要注入的额外环境变量。
     *
     * @param workingDirectory CLI 进程的工作目录，配置文件将写入该目录
     * @param config 用户的自定义模型配置
     * @return 需要注入到 CLI 进程的额外环境变量（如 API Key）
     * @throws IOException 配置文件写入失败时抛出
     */
    Map<String, String> generateConfig(String workingDirectory, CustomModelConfig config)
            throws IOException;

    /**
     * 生成 MCP Server 配置（新增，默认空实现）。
     * 子类按需覆盖以实现具体的 MCP 配置注入逻辑。
     *
     * @param workingDirectory CLI 进程的工作目录
     * @param mcpServers 选中的 MCP Server 列表
     * @throws IOException 配置文件写入失败时抛出
     */
    default void generateMcpConfig(
            String workingDirectory, List<CliSessionConfig.McpServerEntry> mcpServers)
            throws IOException {
        // 默认不执行任何操作，子类按需覆盖
    }

    /**
     * 生成 Skill 配置（新增，默认空实现）。
     * 子类按需覆盖以实现具体的 Skill 配置注入逻辑。
     *
     * @param workingDirectory CLI 进程的工作目录
     * @param skills 选中的 Skill 列表
     * @throws IOException 配置文件写入失败时抛出
     */
    default void generateSkillConfig(
            String workingDirectory, List<CliSessionConfig.SkillEntry> skills) throws IOException {
        // 默认不执行任何操作，子类按需覆盖
    }
}
