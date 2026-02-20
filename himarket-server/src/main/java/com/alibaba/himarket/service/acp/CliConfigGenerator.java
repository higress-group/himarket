package com.alibaba.himarket.service.acp;

import java.io.IOException;
import java.util.Map;

/**
 * CLI 工具的自定义模型配置文件生成器接口。
 * 每个支持自定义模型的 CLI 工具（如 Open Code、Qwen Code）提供各自的实现，
 * 负责生成对应格式的配置文件并返回需要注入到 CLI 进程的额外环境变量。
 */
public interface CliConfigGenerator {

    /**
     * 该生成器支持的 provider key（如 "opencode"、"qwen-code"）。
     *
     * @return provider key
     */
    String supportedProvider();

    /**
     * 生成配置文件并返回需要注入的额外环境变量。
     *
     * @param workingDirectory CLI 进程的工作目录，配置文件将写入该目录
     * @param config 用户的自定义模型配置
     * @return 需要注入到 CLI 进程的额外环境变量（如 API Key）
     * @throws IOException 配置文件写入失败时抛出
     */
    Map<String, String> generateConfig(String workingDirectory, CustomModelConfig config)
            throws IOException;
}
