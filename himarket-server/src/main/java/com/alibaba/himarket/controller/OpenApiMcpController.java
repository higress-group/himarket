package com.alibaba.himarket.controller;

import com.alibaba.himarket.core.exception.BusinessException;
import com.alibaba.himarket.core.exception.ErrorCode;
import com.alibaba.himarket.dto.params.mcp.RegisterMcpParam;
import com.alibaba.himarket.dto.result.common.PageResult;
import com.alibaba.himarket.dto.result.mcp.McpMetaDetailResult;
import com.alibaba.himarket.dto.result.mcp.McpMetaResult;
import com.alibaba.himarket.dto.result.mcp.McpMetaSimpleResult;
import com.alibaba.himarket.service.McpServerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

/**
 * MCP Server 开放接口 — 供外部系统通过 API Key 调用。
 *
 * <p>鉴权方式：请求头 X-API-Key，值需与配置项 open-api.api-key 一致。
 *
 * <p>查询接口不暴露 productId 等内部字段：
 * <ul>
 *   <li>列表接口返回 {@link McpMetaSimpleResult}（精简）</li>
 *   <li>详情接口返回 {@link McpMetaDetailResult}（完整但脱敏）</li>
 * </ul>
 */
@Tag(name = "MCP Server 开放接口")
@RestController
@RequestMapping("/open-api/mcp-servers")
@RequiredArgsConstructor
public class OpenApiMcpController {

    private final McpServerService mcpServerService;

    @Value("${open-api.api-key}")
    private String apiKey;

    // ==================== 写入接口 ====================

    @Operation(summary = "注册 MCP Server（自动创建 Product + Meta + ProductRef）")
    @PostMapping("/register")
    public McpMetaDetailResult register(
            @RequestHeader("X-API-Key") String key, @RequestBody @Valid RegisterMcpParam param) {
        verifyApiKey(key);
        McpMetaResult full = mcpServerService.registerMcp(param);
        return McpMetaDetailResult.fromFull(full);
    }

    // 更新接口暂不对外开放
    // @PostMapping("/meta")
    // public McpMetaDetailResult saveMeta(...)

    // ==================== 查询接口（详情） ====================

    @Operation(summary = "按 mcpServerId 查询 MCP Server 详情")
    @GetMapping("/meta/{mcpServerId}")
    public McpMetaDetailResult getMeta(
            @RequestHeader("X-API-Key") String key, @PathVariable String mcpServerId) {
        verifyApiKey(key);
        return McpMetaDetailResult.fromFull(mcpServerService.getMeta(mcpServerId));
    }

    @Operation(summary = "按 mcpName 查询 MCP Server 详情")
    @GetMapping("/meta/by-name/{mcpName}")
    public McpMetaDetailResult getMetaByName(
            @RequestHeader("X-API-Key") String key, @PathVariable String mcpName) {
        verifyApiKey(key);
        return McpMetaDetailResult.fromFull(mcpServerService.getMetaByName(mcpName));
    }

    // ==================== 查询接口（列表，精简） ====================

    @Operation(summary = "分页查询指定来源的 MCP Server 列表（精简）")
    @GetMapping("/meta/list")
    public PageResult<McpMetaSimpleResult> listMeta(
            @RequestHeader("X-API-Key") String key,
            @RequestParam(required = false, defaultValue = "OPEN_API") String origin,
            Pageable pageable) {
        verifyApiKey(key);
        PageResult<McpMetaResult> fullPage = mcpServerService.listMetaByOrigin(origin, pageable);
        return new PageResult<McpMetaSimpleResult>()
                .mapFrom(fullPage, McpMetaSimpleResult::fromFull);
    }

    @Operation(summary = "分页查询所有 MCP Server 列表（精简）")
    @GetMapping("/meta/list-all")
    public PageResult<McpMetaSimpleResult> listAllMeta(
            @RequestHeader("X-API-Key") String key, Pageable pageable) {
        verifyApiKey(key);
        PageResult<McpMetaResult> fullPage = mcpServerService.listAllMeta(pageable);
        return new PageResult<McpMetaSimpleResult>()
                .mapFrom(fullPage, McpMetaSimpleResult::fromFull);
    }

    // DELETE 接口暂不对外开放
    // @DeleteMapping("/meta/{mcpServerId}")

    private void verifyApiKey(String key) {
        if (key == null
                || key.length() != apiKey.length()
                || !java.security.MessageDigest.isEqual(
                        apiKey.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                        key.getBytes(java.nio.charset.StandardCharsets.UTF_8))) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Invalid API Key");
        }
    }
}
