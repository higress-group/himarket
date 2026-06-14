package com.alibaba.himarket.service.hicoding.session;

import com.alibaba.himarket.core.security.ContextHolder;
import com.alibaba.himarket.dto.result.consumer.CredentialContext;
import com.alibaba.himarket.service.ConsumerService;
import com.alibaba.himarket.service.McpServerService;
import com.alibaba.himarket.support.chat.mcp.McpTransportConfig;
import com.alibaba.himarket.support.enums.McpTransportMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Resolves complete MCP connection configuration from MCP product IDs.
 *
 * <p>Uses {@code McpServerService.resolveTransportConfigs()} first so endpoint data and user
 * subscription checks stay in one place. Products without approved subscriptions are skipped.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class McpConfigResolver {

    private final ConsumerService consumerService;
    private final McpServerService mcpServerService;
    private final ContextHolder contextHolder;

    /**
     * Resolves complete MCP connection configuration from MCP product IDs.
     *
     * @param mcpEntries MCP identifier list from the frontend
     * @return resolved ResolvedMcpEntry list; unsubscribed or failed entries are skipped
     */
    public List<ResolvedSessionConfig.ResolvedMcpEntry> resolve(
            List<CliSessionConfig.McpServerEntry> mcpEntries) {
        if (mcpEntries == null || mcpEntries.isEmpty()) {
            return Collections.emptyList();
        }

        String userId = contextHolder.getUser();

        // 1. Extract product IDs and build the display-name lookup.
        List<String> productIds =
                mcpEntries.stream().map(CliSessionConfig.McpServerEntry::getProductId).toList();
        Map<String, String> nameByProductId =
                mcpEntries.stream()
                        .collect(
                                Collectors.toMap(
                                        CliSessionConfig.McpServerEntry::getProductId,
                                        CliSessionConfig.McpServerEntry::getName,
                                        (a, b) -> a));

        // 2. Resolve endpoint data through McpServerService, including subscription checks.
        List<McpTransportConfig> hotConfigs =
                mcpServerService.resolveTransportConfigs(productIds, userId);
        Map<String, McpTransportConfig> hotConfigMap =
                hotConfigs.stream()
                        .collect(
                                Collectors.toMap(
                                        McpTransportConfig::getProductId, c -> c, (a, b) -> a));

        // 3. Load auth headers for entries whose endpoint data does not include headers.
        Map<String, String> authHeaders = extractAuthHeaders();

        // 4. Build resolved entries from available endpoint data.
        List<ResolvedSessionConfig.ResolvedMcpEntry> result = new ArrayList<>();
        for (CliSessionConfig.McpServerEntry entry : mcpEntries) {
            String productId = entry.getProductId();
            McpTransportConfig hotConfig = hotConfigMap.get(productId);

            if (hotConfig != null) {
                // Endpoint data is available and has passed subscription checks.
                String transportType =
                        hotConfig.getTransportMode() == McpTransportMode.STREAMABLE_HTTP
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
                log.info(
                        "MCP transport config unavailable or subscription not approved. "
                                + "Skipping entry, productId={}, name={}",
                        productId,
                        nameByProductId.get(productId));
            }
        }
        return result;
    }

    /**
     * Extracts auth headers for the current developer.
     * Reuses the CliProviderController.extractAuthHeaders() behavior.
     */
    private Map<String, String> extractAuthHeaders() {
        try {
            CredentialContext credentialContext =
                    consumerService.getDefaultCredential(contextHolder.getUser());
            Map<String, String> headers = credentialContext.copyHeaders();
            return headers.isEmpty() ? null : headers;
        } catch (Exception e) {
            log.debug("Failed to resolve auth headers, errorMessage={}", e.getMessage(), e);
            return null;
        }
    }
}
