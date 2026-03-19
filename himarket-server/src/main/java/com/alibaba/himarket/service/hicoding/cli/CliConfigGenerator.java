package com.alibaba.himarket.service.hicoding.cli;

import com.alibaba.himarket.service.hicoding.session.CustomModelConfig;
import com.alibaba.himarket.service.hicoding.session.ResolvedSessionConfig;
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
     * 生成 MCP Server 配置（默认空实现）。
     * 子类按需覆盖以实现具体的 MCP 配置注入逻辑。
     *
     * @param workingDirectory CLI 进程的工作目录
     * @param mcpServers 解析后的 MCP Server 列表（含完整连接信息）
     * @throws IOException 配置文件写入失败时抛出
     */
    default void generateMcpConfig(
            String workingDirectory, List<ResolvedSessionConfig.ResolvedMcpEntry> mcpServers)
            throws IOException {
        // 默认不执行任何操作，子类按需覆盖
    }

    /**
     * 返回该 CLI 工具的 skills 目录路径（相对于工作目录）。
     * 用于 SkillDownloadPhase 确定 nacos-cli 的输出目录。
     *
     * @return skills 目录相对路径，如 ".qoder/skills/"
     */
    default String skillsDirectory() {
        return "skills/";
    }

    /**
     * 生成 Skill 配置（默认实现：生成 nacos-env.yaml）。
     * 按 nacosId 分组，为每个 Nacos 实例生成独立的 .nacos/nacos-env-{nacosId}.yaml 文件。
     * 子类不再需要覆写此方法。
     *
     * @param workingDirectory CLI 进程的工作目录
     * @param skills 解析后的 Skill 列表（含 Nacos 坐标和凭证）
     * @throws IOException 配置文件写入失败时抛出
     */
    default void generateSkillConfig(
            String workingDirectory, List<ResolvedSessionConfig.ResolvedSkillEntry> skills)
            throws IOException {
        if (skills == null || skills.isEmpty()) return;
        NacosEnvGenerator.generateNacosEnvFiles(workingDirectory, skills);
    }
}
