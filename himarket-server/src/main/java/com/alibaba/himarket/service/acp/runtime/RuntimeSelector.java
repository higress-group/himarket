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

    /**
     * @deprecated 仅用于测试兼容，生产环境请使用带 K8sConfigService 的构造函数
     */
    @Deprecated
    public RuntimeSelector(AcpProperties acpProperties, boolean k8sAvailable) {
        this.acpProperties = acpProperties;
        // 测试兼容：用一个简单的 stub
        this.k8sConfigService =
                k8sAvailable
                        ? new K8sConfigService() {
                            @Override
                            public boolean hasAnyCluster() {
                                return true;
                            }
                        }
                        : new K8sConfigService() {
                            @Override
                            public boolean hasAnyCluster() {
                                return false;
                            }
                        };
    }

    public RuntimeSelector(AcpProperties acpProperties, K8sConfigService k8sConfigService) {
        this.acpProperties = acpProperties;
        this.k8sConfigService = k8sConfigService;
    }

    public List<RuntimeOption> getAvailableRuntimes(String providerKey) {
        CliProviderConfig provider = acpProperties.getProvider(providerKey);
        if (provider == null) {
            throw new IllegalArgumentException("未知的 CLI Provider: " + providerKey);
        }

        List<RuntimeType> compatible = provider.getCompatibleRuntimes();
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
        return java.util.Arrays.stream(RuntimeType.values())
                .map(type -> toRuntimeOption(type, true))
                .toList();
    }

    public RuntimeType selectDefault(String providerKey) {
        CliProviderConfig provider = acpProperties.getProvider(providerKey);
        if (provider == null) {
            throw new IllegalArgumentException("未知的 CLI Provider: " + providerKey);
        }

        List<RuntimeType> compatible = provider.getCompatibleRuntimes();
        if (compatible == null || compatible.isEmpty()) {
            throw new IllegalStateException("CLI Provider '" + providerKey + "' 没有配置兼容的运行时");
        }

        List<RuntimeType> available = compatible.stream().filter(this::isRuntimeAvailable).toList();

        if (available.isEmpty()) {
            throw new IllegalStateException("CLI Provider '" + providerKey + "' 没有可用的运行时");
        }

        if (available.size() == 1) {
            return available.get(0);
        }

        RuntimeType defaultType = resolveDefaultRuntimeType();
        if (defaultType != null && available.contains(defaultType)) {
            return defaultType;
        }

        return available.get(0);
    }

    /**
     * 检查指定运行时类型在当前环境中是否可用。
     * <ul>
     *   <li>LOCAL: 始终可用</li>
     *   <li>K8S: 仅当 K8s 集群已配置时可用</li>
     * </ul>
     */
    public boolean isRuntimeAvailable(RuntimeType type) {
        return switch (type) {
            case LOCAL -> true;
            case K8S -> k8sConfigService.hasAnyCluster();
        };
    }

    public RuntimeOption toRuntimeOption(RuntimeType type, boolean compatible) {
        boolean available = compatible && isRuntimeAvailable(type);
        String unavailableReason = null;

        if (!compatible) {
            unavailableReason = "该 CLI 工具不兼容此运行时";
        } else if (!isRuntimeAvailable(type)) {
            unavailableReason = getUnavailableReason(type);
        }

        return new RuntimeOption(
                type,
                getLabelForType(type),
                getDescriptionForType(type),
                available,
                unavailableReason);
    }

    private RuntimeType resolveDefaultRuntimeType() {
        String defaultRuntime = acpProperties.getDefaultRuntime();
        if (defaultRuntime == null || defaultRuntime.isBlank()) {
            return null;
        }
        try {
            return RuntimeType.valueOf(defaultRuntime.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private String getUnavailableReason(RuntimeType type) {
        return switch (type) {
            case LOCAL -> null;
            case K8S -> "K8s 集群未配置，请联系平台管理员配置 kubeconfig";
        };
    }

    private String getLabelForType(RuntimeType type) {
        return switch (type) {
            case LOCAL -> "本地运行";
            case K8S -> "K8s 沙箱";
        };
    }

    private String getDescriptionForType(RuntimeType type) {
        return switch (type) {
            case LOCAL -> "在服务器本地通过进程启动 CLI Agent，适用于开发调试";
            case K8S -> "通过 K8s 集群按需拉起 Pod，提供隔离的沙箱环境";
        };
    }
}
