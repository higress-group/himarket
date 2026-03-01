package com.alibaba.himarket.service.acp.runtime;

import com.alibaba.himarket.config.AcpProperties;
import com.alibaba.himarket.config.AcpProperties.CliProviderConfig;
import java.util.Collections;
import java.util.List;

/**
 * 运行时选择器，根据 CLI Provider 兼容性和环境可用性过滤运行时选项。
 * <p>
 * 核心职责：
 * <ul>
 *   <li>根据 CLI Provider 的 compatibleRuntimes 过滤兼容的运行时</li>
 *   <li>检查每种运行时在当前环境中的可用性（K8s 是否配置等）</li>
 *   <li>支持默认运行时优先级配置</li>
 *   <li>当仅有一个兼容且可用的运行时时自动选中</li>
 * </ul>
 * <p>
 * 通过 {@link com.alibaba.himarket.config.RuntimeSelectorConfig} 注册为 Spring Bean。
 */
public class RuntimeSelector {

    private final AcpProperties acpProperties;
    private final K8sConfigService k8sConfigService;

    public RuntimeSelector(AcpProperties acpProperties, K8sConfigService k8sConfigService) {
        this.acpProperties = acpProperties;
        this.k8sConfigService = k8sConfigService;
    }

    public List<RuntimeOption> getAvailableRuntimes(String providerKey) {
        CliProviderConfig provider = acpProperties.getProvider(providerKey);
        if (provider == null) {
            throw new IllegalArgumentException("未知的 CLI Provider: " + providerKey);
        }

        List<SandboxType> compatible = provider.getCompatibleRuntimes();
        if (compatible == null || compatible.isEmpty()) {
            return Collections.emptyList();
        }

        return compatible.stream().map(type -> toRuntimeOption(type, true)).toList();
    }

    /**
     * 获取所有运行时类型的可用性状态（不依赖具体 CLI Provider）。
     * 前端在组件挂载时调用，用于获取全局运行时环境状态。
     */
    public List<RuntimeOption> getAllRuntimeAvailability() {
        return java.util.Arrays.stream(SandboxType.values())
                .filter(type -> type != SandboxType.E2B) // E2B 尚未实现，不暴露给前端
                .map(type -> toRuntimeOption(type, true))
                .toList();
    }

    public SandboxType selectDefault(String providerKey) {
        CliProviderConfig provider = acpProperties.getProvider(providerKey);
        if (provider == null) {
            throw new IllegalArgumentException("未知的 CLI Provider: " + providerKey);
        }

        List<SandboxType> compatible = provider.getCompatibleRuntimes();
        if (compatible == null || compatible.isEmpty()) {
            throw new IllegalStateException("CLI Provider '" + providerKey + "' 没有配置兼容的运行时");
        }

        List<SandboxType> available = compatible.stream().filter(this::isSandboxAvailable).toList();

        if (available.isEmpty()) {
            throw new IllegalStateException("CLI Provider '" + providerKey + "' 没有可用的运行时");
        }

        if (available.size() == 1) {
            return available.get(0);
        }

        SandboxType defaultType = resolveDefaultSandboxType();
        if (defaultType != null && available.contains(defaultType)) {
            return defaultType;
        }

        return available.get(0);
    }

    /**
     * 检查指定沙箱类型在当前环境中是否可用。
     * <ul>
     *   <li>LOCAL: 仅当管理员启用时可用</li>
     *   <li>K8S: 仅当 K8s 集群已配置时可用</li>
     *   <li>E2B: 尚未实现，始终不可用</li>
     * </ul>
     */
    public boolean isSandboxAvailable(SandboxType type) {
        return switch (type) {
            case LOCAL -> acpProperties.isLocalEnabled();
            case K8S -> k8sConfigService.hasAnyCluster();
            case E2B -> false;
        };
    }

    public RuntimeOption toRuntimeOption(SandboxType type, boolean compatible) {
        boolean available = compatible && isSandboxAvailable(type);
        String unavailableReason = null;

        if (!compatible) {
            unavailableReason = "该 CLI 工具不兼容此运行时";
        } else if (!isSandboxAvailable(type)) {
            unavailableReason = getUnavailableReason(type);
        }

        return new RuntimeOption(
                type,
                getLabelForType(type),
                getDescriptionForType(type),
                available,
                unavailableReason);
    }

    private SandboxType resolveDefaultSandboxType() {
        String defaultRuntime = acpProperties.getDefaultRuntime();
        if (defaultRuntime == null || defaultRuntime.isBlank()) {
            return null;
        }
        try {
            return SandboxType.fromValue(defaultRuntime);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private String getUnavailableReason(SandboxType type) {
        return switch (type) {
            case LOCAL -> acpProperties.isLocalEnabled() ? null : "本地模式已被管理员禁用";
            case K8S -> "K8s 集群未配置，请联系平台管理员配置 kubeconfig";
            case E2B -> "E2B 云沙箱尚未实现";
        };
    }

    private String getLabelForType(SandboxType type) {
        return switch (type) {
            case LOCAL -> "本地运行";
            case K8S -> "K8s 沙箱";
            case E2B -> "E2B 云沙箱";
        };
    }

    private String getDescriptionForType(SandboxType type) {
        return switch (type) {
            case LOCAL -> "在服务器本地通过进程启动 CLI Agent，适用于开发调试";
            case K8S -> "通过 K8s 集群按需拉起 Pod，提供隔离的沙箱环境";
            case E2B -> "通过 E2B SDK 管理远程云沙箱（未来扩展）";
        };
    }
}
