package com.alibaba.himarket.service.hicoding.cli;

import com.alibaba.himarket.service.hicoding.session.CustomModelConfig;
import com.alibaba.himarket.service.hicoding.session.ResolvedSessionConfig;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Interface for CLI custom model configuration generators.
 * Each CLI tool that supports custom models provides its own implementation and returns extra
 * environment variables that must be injected into the CLI process.
 */
public interface CliConfigGenerator {

    /**
     * Returns the provider key supported by this generator, such as "opencode" or "qwen-code".
     *
     * @return provider key
     */
    String supportedProvider();

    /**
     * Generates configuration files and returns extra environment variables to inject.
     *
     * @param workingDirectory CLI process working directory where configuration files are written
     * @param config custom model configuration
     * @return extra environment variables to inject into the CLI process
     * @throws IOException when configuration files cannot be written
     */
    Map<String, String> generateConfig(String workingDirectory, CustomModelConfig config)
            throws IOException;

    /**
     * Generates MCP server configuration.
     * Implementations can override this no-op default when they support MCP injection.
     *
     * @param workingDirectory CLI process working directory
     * @param mcpServers resolved MCP servers with complete connection details
     * @throws IOException when configuration files cannot be written
     */
    default void generateMcpConfig(
            String workingDirectory, List<ResolvedSessionConfig.ResolvedMcpEntry> mcpServers)
            throws IOException {
        // No-op by default.
    }

    /**
     * Returns the skills directory for this CLI tool, relative to the working directory.
     * Used by SkillDownloadPhase to choose the nacos-cli output directory.
     *
     * @return relative skills directory, such as ".qoder/skills/"
     */
    default String skillsDirectory() {
        return "skills/";
    }

    /**
     * Generates Skill configuration.
     * The default implementation writes nacos-env.yaml files grouped by nacosId.
     *
     * @param workingDirectory CLI process working directory
     * @param skills resolved skills with Nacos coordinates and credentials
     * @throws IOException when configuration files cannot be written
     */
    default void generateSkillConfig(
            String workingDirectory, List<ResolvedSessionConfig.ResolvedSkillEntry> skills)
            throws IOException {
        if (skills == null || skills.isEmpty()) return;
        NacosEnvGenerator.generateNacosEnvFiles(workingDirectory, skills);
    }
}
