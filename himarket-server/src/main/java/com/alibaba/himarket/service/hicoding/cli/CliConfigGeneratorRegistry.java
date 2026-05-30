/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.alibaba.himarket.service.hicoding.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * CLI 配置生成器注册表。
 *
 * <p>将各 {@link CliConfigGenerator} 实现注册为 Spring Bean，
 * 供 {@link ConfigFileBuilder} 注入使用。
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
