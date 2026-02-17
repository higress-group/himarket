package com.alibaba.himarket.config;

import com.alibaba.himarket.service.acp.runtime.K8sConfigService;
import com.alibaba.himarket.service.acp.runtime.RuntimeSelector;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RuntimeSelector 的 Spring 配置。
 * <p>
 * 通过 K8sConfigService 动态检测 K8s 集群是否已注册，
 * 实现运行时可用性的实时感知。
 */
@Configuration
public class RuntimeSelectorConfig {

    @Bean
    public RuntimeSelector runtimeSelector(
            AcpProperties acpProperties, K8sConfigService k8sConfigService) {
        return new RuntimeSelector(acpProperties, k8sConfigService);
    }
}
