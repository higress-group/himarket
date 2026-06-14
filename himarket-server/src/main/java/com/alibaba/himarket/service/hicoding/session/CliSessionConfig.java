package com.alibaba.himarket.service.hicoding.session;

import java.util.List;
import lombok.Data;

/**
 * CLI session configuration DTO from the frontend.
 * Carries identifiers such as modelProductId, mcpServers[].productId, skills[].productId, and auth
 * credentials through WebSocket messages.
 */
@Data
public class CliSessionConfig {

    /**
     * Marketplace model product ID.
     */
    private String modelProductId;

    /**
     * Selected MCP servers represented by identifiers.
     */
    private List<McpServerEntry> mcpServers;

    /**
     * Selected skills represented by identifiers.
     */
    private List<SkillEntry> skills;

    /**
     * Auth credential, such as PAT or API Key, injected into CLI process environment variables.
     */
    private String authToken;

    @Data
    public static class McpServerEntry {
        /**
         * MCP product ID.
         */
        private String productId;

        /**
         * MCP server name.
         */
        private String name;
    }

    @Data
    public static class SkillEntry {
        /**
         * Skill product ID.
         */
        private String productId;

        /**
         * Skill name.
         */
        private String name;
    }
}
