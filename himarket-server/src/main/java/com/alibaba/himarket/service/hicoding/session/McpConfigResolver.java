package com.alibaba.himarket.service.hicoding.session;

import com.alibaba.himarket.core.security.ContextHolder;
import com.alibaba.himarket.dto.result.consumer.CredentialContext;
import com.alibaba.himarket.dto.result.product.ProductResult;
import com.alibaba.himarket.service.ConsumerService;
import com.alibaba.himarket.service.ProductService;
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
 * <p>复用 {@code CliProviderController.buildMarketMcpInfo()} 和 {@code extractAuthHeaders()} 中的逻辑：
 * <ol>
 *   <li>批量获取产品详情</li>
 *   <li>通过 {@code product.getMcpConfig().toTransportConfig()} 提取 url 和 transportType</li>
 *   <li>通过 {@code ConsumerService.getDefaultCredential()} 提取认证请求头</li>
 *   <li>组装 ResolvedMcpEntry</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class McpConfigResolver {

    private final ConsumerService consumerService;
    private final ProductService productService;
    private final ContextHolder contextHolder;

    /**
     * 根据 MCP 产品 ID 列表解析完整 MCP 连接配置。
     *
     * @param mcpEntries 前端传入的 MCP 标识符列表
     * @return 解析后的 ResolvedMcpEntry 列表（解析失败的条目被跳过）
     */
    public List<ResolvedSessionConfig.ResolvedMcpEntry> resolve(
            List<CliSessionConfig.McpServerEntry> mcpEntries) {
        if (mcpEntries == null || mcpEntries.isEmpty()) {
            return Collections.emptyList();
        }

        // 1. 批量获取产品详情
        List<String> productIds =
                mcpEntries.stream()
                        .map(CliSessionConfig.McpServerEntry::getProductId)
                        .collect(Collectors.toList());
        Map<String, ProductResult> productMap = productService.getProducts(productIds);

        // 2. 获取认证头（所有 MCP 共用同一个开发者的认证信息）
        Map<String, String> authHeaders = extractAuthHeaders();

        // 3. 逐个解析 MCP 配置
        List<ResolvedSessionConfig.ResolvedMcpEntry> result = new ArrayList<>();
        for (CliSessionConfig.McpServerEntry entry : mcpEntries) {
            ResolvedSessionConfig.ResolvedMcpEntry resolved =
                    resolveEntry(entry, productMap, authHeaders);
            if (resolved != null) {
                result.add(resolved);
            }
        }
        return result;
    }

    private ResolvedSessionConfig.ResolvedMcpEntry resolveEntry(
            CliSessionConfig.McpServerEntry entry,
            Map<String, ProductResult> productMap,
            Map<String, String> authHeaders) {
        String productId = entry.getProductId();
        ProductResult product = productMap.get(productId);

        if (product == null) {
            log.warn("MCP product not found, skipping: productId={}", productId);
            return null;
        }

        if (product.getMcpConfig() == null) {
            log.warn(
                    "Product mcpConfig is incomplete, skipping: productId={}, name={}",
                    productId,
                    product.getName());
            return null;
        }

        try {
            MCPTransportConfig transportConfig = product.getMcpConfig().toTransportConfig();
            if (transportConfig == null) {
                log.warn(
                        "Failed to extract transport config from product, skipping: productId={},"
                                + " name={}",
                        productId,
                        product.getName());
                return null;
            }

            String transportType =
                    transportConfig.getTransportMode() == MCPTransportMode.STREAMABLE_HTTP
                            ? "streamable-http"
                            : "sse";

            ResolvedSessionConfig.ResolvedMcpEntry resolved =
                    new ResolvedSessionConfig.ResolvedMcpEntry();
            resolved.setName(entry.getName());
            resolved.setUrl(transportConfig.getUrl());
            resolved.setTransportType(transportType);
            resolved.setHeaders(authHeaders);
            return resolved;
        } catch (Exception e) {
            log.warn(
                    "Error processing mcpConfig for product, skipping: productId={}, name={},"
                            + " error={}",
                    productId,
                    product.getName(),
                    e.getMessage());
            return null;
        }
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
