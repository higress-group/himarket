package com.alibaba.himarket.service.hicoding.session;

import com.alibaba.himarket.core.security.ContextHolder;
import com.alibaba.himarket.dto.result.consumer.CredentialContext;
import com.alibaba.himarket.service.ConsumerService;
import com.alibaba.himarket.service.McpServerService;
import com.alibaba.himarket.support.chat.mcp.MCPTransportConfig;
import com.alibaba.himarket.support.enums.MCPTransportMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 根据 MCP 产品 ID 列表解析完整 MCP 连接配置的服务。
 *
 * <p>优先通过 {@code McpServerService.resolveTransportConfigs()} 从热数据（endpoint）解析，
 * 同时校验用户订阅状态。未订阅的产品会被跳过。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class McpConfigResolver {

    private final ConsumerService consumerService;
    private final McpServerService mcpServerService;
    private final ContextHolder contextHolder;

    /**
     * 根据 MCP 产品 ID 列表解析完整 MCP 连接配置。
     *
     * @param mcpEntries 前端传入的 MCP 标识符列表
     * @return 解析后的 ResolvedMcpEntry 列表（未订阅或解析失败的条目被跳过）
     */
    public List<ResolvedSessionConfig.ResolvedMcpEntry> resolve(
            List<CliSessionConfig.McpServerEntry> mcpEntries) {
        if (mcpEntries == null || mcpEntries.isEmpty()) {
            return Collections.emptyList();
        }

        String userId = contextHolder.getUser();

        // 1. 提取 productId 列表，构建 name 映射
        List<String> productIds =
                mcpEntries.stream()
                        .map(CliSessionConfig.McpServerEntry::getProductId)
                        .collect(Collectors.toList());
        Map<String, String> nameByProductId =
                mcpEntries.stream()
                        .collect(
                                Collectors.toMap(
                                        CliSessionConfig.McpServerEntry::getProductId,
                                        CliSessionConfig.McpServerEntry::getName,
                                        (a, b) -> a));

        // 2. 通过 McpServerService 解析热数据（含订阅校验）
        List<MCPTransportConfig> hotConfigs =
                mcpServerService.resolveTransportConfigs(productIds, userId);
        Map<String, MCPTransportConfig> hotConfigMap =
                hotConfigs.stream()
                        .collect(
                                Collectors.toMap(
                                        MCPTransportConfig::getProductId, c -> c, (a, b) -> a));

        // 3. 获取认证头（热数据已自带 headers，但冷数据 fallback 需要）
        Map<String, String> authHeaders = extractAuthHeaders();

        // 4. 逐个组装结果：优先热数据，fallback 冷数据
        List<ResolvedSessionConfig.ResolvedMcpEntry> result = new ArrayList<>();
        for (CliSessionConfig.McpServerEntry entry : mcpEntries) {
            String productId = entry.getProductId();
            MCPTransportConfig hotConfig = hotConfigMap.get(productId);

            if (hotConfig != null) {
                // 热数据可用（已通过订阅校验）
                String transportType =
                        hotConfig.getTransportMode() == MCPTransportMode.STREAMABLE_HTTP
                                ? "streamable-http"
                                : "sse";
                ResolvedSessionConfig.ResolvedMcpEntry resolved =
                        new ResolvedSessionConfig.ResolvedMcpEntry();
                resolved.setName(entry.getName());
                resolved.setUrl(hotConfig.getUrl());
                resolved.setTransportType(transportType);
                resolved.setHeaders(
                        hotConfig.getHeaders() != null ? hotConfig.getHeaders() : authHeaders);
                result.add(resolved);
            } else {
                // 热数据不可用：可能未订阅，也可能无 endpoint
                // 跳过并记录日志（订阅校验已在 resolveTransportConfigs 中完成）
                log.info(
                        "MCP 产品无可用热数据或未订阅，跳过: productId={}, name={}",
                        productId,
                        nameByProductId.get(productId));
            }
        }
        return result;
    }

    /**
     * 提取当前开发者的认证请求头。
     * 复用 CliProviderController.extractAuthHeaders() 逻辑。
     */
    private Map<String, String> extractAuthHeaders() {
        try {
            CredentialContext credentialContext =
                    consumerService.getDefaultCredential(contextHolder.getUser());
            Map<String, String> headers = credentialContext.copyHeaders();
            return headers.isEmpty() ? null : headers;
        } catch (Exception e) {
            log.debug("Failed to get auth headers: {}", e.getMessage());
            return null;
        }
    }
}
