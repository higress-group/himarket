package com.alibaba.himarket.service.hicoding.sandbox;

public record ConfigFile(String relativePath, String content, String contentHash, ConfigType type) {

    public enum ConfigType {
        MODEL_SETTINGS,
        MCP_CONFIG,
        SKILL_CONFIG,
        CUSTOM
    }
}
