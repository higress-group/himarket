package com.alibaba.himarket.service.hicoding.sandbox;

import java.util.Map;

/**
 * 沙箱创建/获取配置。
 * 统一各沙箱类型的配置参数。
 */
public record SandboxConfig(
        String userId,
        SandboxType type,
        String workspacePath,
        Map<String, String> env,
        Map<String, String> resources,
        // E2B 特有配置（未来）
        String e2bTemplate) {}
