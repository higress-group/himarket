package com.alibaba.himarket.controller;

import com.alibaba.himarket.config.AcpProperties;
import com.alibaba.himarket.config.AcpProperties.CliProviderConfig;
import com.alibaba.himarket.service.acp.runtime.RuntimeType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "CLI Provider管理", description = "查询可用的 ACP CLI Provider 列表")
@RestController
@RequestMapping("/cli-providers")
@RequiredArgsConstructor
public class CliProviderController {

    private static final Logger logger = LoggerFactory.getLogger(CliProviderController.class);

    private final AcpProperties acpProperties;

    @Operation(summary = "获取可用的 CLI Provider 列表（含运行时兼容性信息）")
    @GetMapping
    public List<CliProviderInfo> listProviders() {
        List<CliProviderInfo> result = new ArrayList<>();
        String defaultKey = acpProperties.getDefaultProvider();
        for (Map.Entry<String, CliProviderConfig> entry : acpProperties.getProviders().entrySet()) {
            CliProviderConfig config = entry.getValue();
            boolean available = isCommandAvailable(config.getCommand());
            result.add(
                    new CliProviderInfo(
                            entry.getKey(),
                            config.getDisplayName() != null
                                    ? config.getDisplayName()
                                    : entry.getKey(),
                            entry.getKey().equals(defaultKey),
                            available,
                            config.getRuntimeCategory(),
                            config.getCompatibleRuntimes(),
                            config.getContainerImage()));
        }
        return result;
    }

    /**
     * 检测命令是否在系统 PATH 中可用。
     * 对于 npx 类命令，只检查 npx 本身是否存在（包会按需下载）。
     */
    static boolean isCommandAvailable(String command) {
        if (command == null || command.isBlank()) {
            return false;
        }
        try {
            ProcessBuilder pb = new ProcessBuilder("which", command).redirectErrorStream(true);
            Process process = pb.start();
            boolean exited = process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
            if (!exited) {
                process.destroyForcibly();
                return false;
            }
            return process.exitValue() == 0;
        } catch (Exception e) {
            logger.debug(
                    "Failed to check command availability for '{}': {}", command, e.getMessage());
            return false;
        }
    }

    public record CliProviderInfo(
            String key,
            String displayName,
            boolean isDefault,
            boolean available,
            String runtimeCategory,
            List<RuntimeType> compatibleRuntimes,
            String containerImage) {}
}
