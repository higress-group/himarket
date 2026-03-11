package com.alibaba.himarket.service.hicoding.sandbox;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * SandboxProvider 注册中心。
 * 根据 SandboxType 查找对应的 Provider 实现。
 */
@Component
public class SandboxProviderRegistry {

    private final Map<SandboxType, SandboxProvider> providers;

    public SandboxProviderRegistry(List<SandboxProvider> providerList) {
        this.providers =
                providerList.stream()
                        .collect(Collectors.toMap(SandboxProvider::getType, Function.identity()));
    }

    public SandboxProvider getProvider(SandboxType type) {
        SandboxProvider provider = providers.get(type);
        if (provider == null) {
            throw new IllegalArgumentException("不支持的沙箱类型: " + type);
        }
        return provider;
    }

    public Set<SandboxType> supportedTypes() {
        return providers.keySet();
    }
}
