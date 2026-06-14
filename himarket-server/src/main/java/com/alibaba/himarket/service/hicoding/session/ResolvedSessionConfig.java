package com.alibaba.himarket.service.hicoding.session;

import java.util.List;
import java.util.Map;
import lombok.Data;

/**
 * Complete session configuration resolved from CliSessionConfig for CliConfigGenerator.
 */
@Data
public class ResolvedSessionConfig {

    /**
     * Resolved custom model configuration, or null.
     */
    private CustomModelConfig customModelConfig;

    /**
     * Resolved MCP servers with complete connection details.
     */
    private List<ResolvedMcpEntry> mcpServers;

    /**
     * Resolved skills with coordinates and credentials.
     */
    private List<ResolvedSkillEntry> skills;

    /**
     * Auth credential passed through from the frontend.
     */
    private String authToken;

    @Data
    public static class ResolvedMcpEntry {
        /**
         * MCP server name.
         */
        private String name;

        /**
         * MCP endpoint URL.
         */
        private String url;

        /**
         * Transport protocol type: sse or streamable-http.
         */
        private String transportType;

        /**
         * Auth headers, or null.
         */
        private Map<String, String> headers;
    }

    @Data
    public static class ResolvedSkillEntry {
        /**
         * Skill name.
         */
        private String name;

        // Skill coordinates.
        private String nacosId;
        private String namespace;
        private String skillName;

        // Nacos credentials.
        private String serverAddr;
        private String username;
        private String password;
        private String accessKey;
        private String secretKey;
    }
}
