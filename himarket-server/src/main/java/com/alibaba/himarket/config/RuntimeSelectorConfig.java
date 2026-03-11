package com.alibaba.himarket.config;

import com.alibaba.himarket.service.hicoding.runtime.RuntimeSelector;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RuntimeSelector 的 Spring 配置。
 */
@Configuration
public class RuntimeSelectorConfig {

    @Bean
    public RuntimeSelector runtimeSelector(AcpProperties acpProperties) {
        return new RuntimeSelector(acpProperties);
    }
}
