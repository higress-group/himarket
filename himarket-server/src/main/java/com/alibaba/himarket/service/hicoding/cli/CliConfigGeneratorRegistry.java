package com.alibaba.himarket.service.hicoding.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * CLI configuration generator registry.
 *
 * <p>Registers {@link CliConfigGenerator} implementations as a Spring Bean for {@link
 * ConfigFileBuilder}.
 */
@Configuration
public class CliConfigGeneratorRegistry {

    @Bean
    public Map<String, CliConfigGenerator> configGeneratorRegistry(ObjectMapper objectMapper) {
        Map<String, CliConfigGenerator> registry = new HashMap<>();
        OpenCodeConfigGenerator openCodeGenerator = new OpenCodeConfigGenerator(objectMapper);
        QwenCodeConfigGenerator qwenCodeGenerator = new QwenCodeConfigGenerator(objectMapper);
        ClaudeCodeConfigGenerator claudeCodeGenerator = new ClaudeCodeConfigGenerator(objectMapper);
        QoderCliConfigGenerator qoderCliGenerator = new QoderCliConfigGenerator(objectMapper);
        registry.put(openCodeGenerator.supportedProvider(), openCodeGenerator);
        registry.put(qwenCodeGenerator.supportedProvider(), qwenCodeGenerator);
        registry.put(claudeCodeGenerator.supportedProvider(), claudeCodeGenerator);
        registry.put(qoderCliGenerator.supportedProvider(), qoderCliGenerator);
        return registry;
    }
}
