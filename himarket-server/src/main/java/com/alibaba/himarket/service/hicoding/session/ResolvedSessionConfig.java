package com.alibaba.himarket.service.hicoding.session;

import java.util.List;
import java.util.Map;
import lombok.Data;

/**
 * 后端解析后的完整会话配置 DTO。
 * 由 CliSessionConfig（纯标识符）经后端解析服务填充而成，供 CliConfigGenerator 使用。
 */
@Data
public class ResolvedSessionConfig {

    /** 解析后的完整模型配置（可能为 null） */
    private CustomModelConfig customModelConfig;

    /** 解析后的 MCP Server 列表（含完整连接信息） */
    private List<ResolvedMcpEntry> mcpServers;

    /** 解析后的 Skill 列表（含坐标+凭证） */
    private List<ResolvedSkillEntry> skills;

    /** 认证凭据（直接透传） */
    private String authToken;

    @Data
    public static class ResolvedMcpEntry {
        /** MCP 服务名称 */
        private String name;

        /** MCP 端点 URL */
        private String url;

        /** 传输协议类型：sse 或 streamable-http */
        private String transportType;

        /** 认证请求头（可能为 null） */
        private Map<String, String> headers;
    }

    @Data
    public static class ResolvedSkillEntry {
        /** Skill 名称 */
        private String name;

        // Skill 坐标
        private String nacosId;
        private String namespace;
        private String skillName;

        // Nacos 凭证
        private String serverAddr;
        private String username;
        private String password;
        private String accessKey;
        private String secretKey;
    }
}
