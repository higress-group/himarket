package com.alibaba.himarket.service.acp;

import java.util.List;
import java.util.Map;
import lombok.Data;

/**
 * CLI 会话配置，统一承载模型配置、MCP Server 配置和 Skill 配置。
 * 通过 WebSocket 查询参数传递给后端。
 */
@Data
public class CliSessionConfig {

    /** 自定义模型配置（可选，复用现有 CustomModelConfig） */
    private CustomModelConfig customModelConfig;

    /** 选中的 MCP Server 列表（可选） */
    private List<McpServerEntry> mcpServers;

    /** 选中的 Skill 列表（可选） */
    private List<SkillEntry> skills;

    /** 认证凭据（PAT / API Key），用于注入到 CLI 进程环境变量中（可选） */
    private String authToken;

    @Data
    public static class McpServerEntry {
        /** MCP 服务名称 */
        private String name;

        /** MCP 端点 URL（已拼接完成） */
        private String url;

        /** 传输协议类型：sse 或 streamable-http */
        private String transportType;

        /** 认证请求头（可选） */
        private Map<String, String> headers;
    }

    @Data
    public static class SkillEntry {
        /** 技能名称 */
        private String name;

        /** SKILL.md 文件内容（向后兼容，files 为空时使用） */
        private String skillMdContent;

        /** 完整文件列表（非空时优先使用） */
        private List<SkillFileEntry> files;

        @Data
        public static class SkillFileEntry {
            /** 相对路径，如 SKILL.md、scripts/fetch.py */
            private String path;

            /** 文本内容或 Base64 字符串 */
            private String content;

            /** "text" 或 "base64" */
            private String encoding;
        }
    }
}
