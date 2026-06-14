package com.alibaba.himarket.service.hicoding.sandbox;

import java.util.Map;

/**
 * Sandbox create/acquire configuration.
 *
 * <p>Unifies configuration parameters across sandbox types.
 */
public record SandboxConfig(
        String userId,
        SandboxType type,
        String workspacePath,
        Map<String, String> env,
        Map<String, String> resources,
        // Future E2B-specific configuration.
        String e2bTemplate) {}
