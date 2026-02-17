package com.alibaba.himarket.controller;

import com.alibaba.himarket.service.acp.runtime.RuntimeOption;
import com.alibaba.himarket.service.acp.runtime.RuntimeSelector;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "运行时管理", description = "查询可用的运行时方案")
@RestController
@RequestMapping("/runtime")
@RequiredArgsConstructor
public class RuntimeController {

    private final RuntimeSelector runtimeSelector;

    @Operation(summary = "获取可用运行时列表，可选按 CLI Provider 过滤")
    @GetMapping("/available")
    public List<RuntimeOption> getAvailableRuntimes(
            @Parameter(description = "CLI Provider 的配置 key（可选，不传则返回所有运行时的可用性）")
                    @RequestParam(required = false)
                    String provider) {
        if (provider == null || provider.isBlank()) {
            return runtimeSelector.getAllRuntimeAvailability();
        }
        return runtimeSelector.getAvailableRuntimes(provider);
    }
}
