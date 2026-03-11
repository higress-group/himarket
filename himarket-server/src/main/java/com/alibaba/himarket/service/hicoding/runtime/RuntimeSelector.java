package com.alibaba.himarket.service.hicoding.runtime;

import com.alibaba.himarket.config.AcpProperties;
import com.alibaba.himarket.config.AcpProperties.CliProviderConfig;
import com.alibaba.himarket.service.hicoding.sandbox.SandboxType;
import java.util.Collections;
import java.util.List;

/**
 * 运行时选择器，根据 CLI Provider 兼容性和环境可用性过滤运行时选项。
 */
public class RuntimeSelector {

    private final AcpProperties acpProperties;

    public RuntimeSelector(AcpProperties acpProperties) {
        this.acpProperties = acpProperties;
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
     */
    public List<RuntimeOption> getAllRuntimeAvailability() {
        return java.util.Arrays.stream(SandboxType.values())
                .filter(type -> type != SandboxType.E2B)
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
     */
    public boolean isSandboxAvailable(SandboxType type) {
        return switch (type) {
            case REMOTE -> acpProperties.getRemote().isConfigured();
            case OPEN_SANDBOX -> false;
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
            case REMOTE -> "远程沙箱未配置，请设置 acp.remote.host";
            case OPEN_SANDBOX -> "OpenSandbox 沙箱尚未实现";
            case E2B -> "E2B 云沙箱尚未实现";
        };
    }

    private String getLabelForType(SandboxType type) {
        return switch (type) {
            case REMOTE -> "远程沙箱";
            case OPEN_SANDBOX -> "OpenSandbox";
            case E2B -> "E2B 云沙箱";
        };
    }

    private String getDescriptionForType(SandboxType type) {
        return switch (type) {
            case REMOTE -> "连接远程 Sidecar 服务提供沙箱环境（支持 K8s / Docker / 裸机部署）";
            case OPEN_SANDBOX -> "通过 OpenSandbox Server 管理沙箱实例（未实现）";
            case E2B -> "通过 E2B SDK 管理远程云沙箱（未实现）";
        };
    }
}
