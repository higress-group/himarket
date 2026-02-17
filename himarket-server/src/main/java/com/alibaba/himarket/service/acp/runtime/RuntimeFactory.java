package com.alibaba.himarket.service.acp.runtime;

import io.fabric8.kubernetes.client.KubernetesClient;
import org.springframework.stereotype.Component;

/**
 * 运行时工厂，根据 {@link RuntimeType} 创建对应的 {@link RuntimeAdapter} 实例。
 * <p>
 * 使用工厂模式屏蔽运行时实例的创建细节，上层业务代码通过此工厂获取运行时适配器。
 * <ul>
 *   <li>LOCAL: 创建 {@link LocalRuntimeAdapter}，封装本地 AcpProcess</li>
 *   <li>K8S: 创建 {@link K8sRuntimeAdapter}，通过 K8s Pod 提供沙箱隔离</li>
 * </ul>
 */
@Component
public class RuntimeFactory {

    private final K8sConfigService k8sConfigService;

    public RuntimeFactory(K8sConfigService k8sConfigService) {
        this.k8sConfigService = k8sConfigService;
    }

    /**
     * 根据运行时类型创建对应的 RuntimeAdapter 实例。
     *
     * @param type   运行时类型
     * @param config 运行时配置
     * @return 对应的 RuntimeAdapter 实例
     * @throws IllegalArgumentException 当 K8s 所需的配置缺失时
     */
    public RuntimeAdapter create(RuntimeType type, RuntimeConfig config) {
        return switch (type) {
            case LOCAL -> new LocalRuntimeAdapter();
            case K8S -> {
                String k8sConfigId = config.getK8sConfigId();
                if (k8sConfigId == null || k8sConfigId.isBlank()) {
                    throw new IllegalArgumentException(
                            "K8s runtime requires k8sConfigId in RuntimeConfig");
                }
                KubernetesClient client = k8sConfigService.getClient(k8sConfigId);
                yield new K8sRuntimeAdapter(client);
            }
        };
    }
}
