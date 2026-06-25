package com.alibaba.himarket.controller;

import com.alibaba.himarket.config.AcpProperties;
import com.alibaba.himarket.config.AcpProperties.CliProviderConfig;
import com.alibaba.himarket.core.annotation.DeveloperAuth;
import com.alibaba.himarket.core.security.ContextHolder;
import com.alibaba.himarket.dto.params.product.QueryProductParam;
import com.alibaba.himarket.dto.result.cli.MarketMcpInfo;
import com.alibaba.himarket.dto.result.cli.MarketMcpsResponse;
import com.alibaba.himarket.dto.result.cli.MarketModelInfo;
import com.alibaba.himarket.dto.result.cli.MarketModelsResponse;
import com.alibaba.himarket.dto.result.cli.MarketSkillInfo;
import com.alibaba.himarket.dto.result.common.PageResult;
import com.alibaba.himarket.dto.result.consumer.ConsumerCredentialResult;
import com.alibaba.himarket.dto.result.consumer.ConsumerResult;
import com.alibaba.himarket.dto.result.consumer.CredentialContext;
import com.alibaba.himarket.dto.result.model.ModelConfigResult;
import com.alibaba.himarket.dto.result.product.ProductResult;
import com.alibaba.himarket.dto.result.product.SubscriptionResult;
import com.alibaba.himarket.service.ConsumerService;
import com.alibaba.himarket.service.ProductService;
import com.alibaba.himarket.service.hicoding.cli.ProtocolTypeMapper;
import com.alibaba.himarket.service.hicoding.filesystem.BaseUrlExtractor;
import com.alibaba.himarket.service.hicoding.sandbox.SandboxType;
import com.alibaba.himarket.support.chat.mcp.McpTransportConfig;
import com.alibaba.himarket.support.consumer.ApiKeyConfig;
import com.alibaba.himarket.support.enums.McpTransportMode;
import com.alibaba.himarket.support.enums.ProductStatus;
import com.alibaba.himarket.support.enums.ProductType;
import com.alibaba.himarket.support.enums.SubscriptionStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "CLI Provider Management", description = "ACP CLI provider and marketplace helper APIs")
@RestController
@RequestMapping("/cli-providers")
@RequiredArgsConstructor
public class CliProviderController {

    private static final Logger logger = LoggerFactory.getLogger(CliProviderController.class);

    private final AcpProperties acpProperties;
    private final ConsumerService consumerService;
    private final ProductService productService;
    private final ContextHolder contextHolder;

    @Operation(summary = "List subscribed marketplace models")
    @GetMapping("/market-models")
    @DeveloperAuth
    public MarketModelsResponse listMarketModels() {
        // 1. Get the primary consumer.
        ConsumerResult consumer;
        try {
            consumer = consumerService.getPrimaryConsumer();
        } catch (Exception e) {
            logger.debug(
                    "No primary consumer found for current developer, errorMessage={}",
                    e.getMessage(),
                    e);
            return MarketModelsResponse.builder()
                    .models(Collections.emptyList())
                    .apiKey(null)
                    .build();
        }

        String consumerId = consumer.getConsumerId();

        // 2. List subscriptions and keep only approved ones.
        List<SubscriptionResult> subscriptions =
                consumerService.listConsumerSubscriptions(consumerId);
        List<SubscriptionResult> approvedSubscriptions =
                subscriptions.stream()
                        .filter(s -> SubscriptionStatus.APPROVED.name().equals(s.getStatus()))
                        .toList();

        // 3. Extract the API key.
        String apiKey = extractApiKey(consumerId);

        if (approvedSubscriptions.isEmpty()) {
            return MarketModelsResponse.builder()
                    .models(Collections.emptyList())
                    .apiKey(apiKey)
                    .build();
        }

        // 4. Batch load product details and filter by MODEL_API.
        List<String> productIds =
                approvedSubscriptions.stream().map(SubscriptionResult::getProductId).toList();
        Map<String, ProductResult> productMap = productService.getProducts(productIds);

        // 5. Extract metadata from MODEL_API products.
        List<MarketModelInfo> models = new ArrayList<>();
        for (SubscriptionResult subscription : approvedSubscriptions) {
            ProductResult product = productMap.get(subscription.getProductId());
            if (product == null) {
                logger.warn(
                        "Product not found for subscription, productId={}",
                        subscription.getProductId());
                continue;
            }

            // Filter MODEL_API products by the type field in product details.
            if (product.getType() != ProductType.MODEL_API) {
                continue;
            }

            MarketModelInfo modelInfo = buildMarketModelInfo(product);
            if (modelInfo != null) {
                models.add(modelInfo);
            }
        }

        // 6. Build the response.
        return MarketModelsResponse.builder().models(models).apiKey(apiKey).build();
    }

    @Operation(summary = "List subscribed MCP servers")
    @GetMapping("/market-mcps")
    @DeveloperAuth
    public MarketMcpsResponse listMarketMcps() {
        // 1. Get the primary consumer.
        ConsumerResult consumer;
        try {
            consumer = consumerService.getPrimaryConsumer();
        } catch (Exception e) {
            logger.debug(
                    "No primary consumer found for current developer, errorMessage={}",
                    e.getMessage(),
                    e);
            return MarketMcpsResponse.builder()
                    .mcpServers(Collections.emptyList())
                    .authHeaders(null)
                    .build();
        }

        String consumerId = consumer.getConsumerId();

        // 2. List subscriptions and keep only approved ones.
        List<SubscriptionResult> subscriptions =
                consumerService.listConsumerSubscriptions(consumerId);
        List<SubscriptionResult> approvedSubscriptions =
                subscriptions.stream()
                        .filter(s -> SubscriptionStatus.APPROVED.name().equals(s.getStatus()))
                        .toList();

        if (approvedSubscriptions.isEmpty()) {
            return MarketMcpsResponse.builder()
                    .mcpServers(Collections.emptyList())
                    .authHeaders(extractAuthHeaders())
                    .build();
        }

        // 3. Batch load product details and filter by MCP_SERVER.
        List<String> productIds =
                approvedSubscriptions.stream().map(SubscriptionResult::getProductId).toList();
        Map<String, ProductResult> productMap = productService.getProducts(productIds);

        // 4. Extract MCP metadata from each product.
        List<MarketMcpInfo> mcpServers = new ArrayList<>();
        for (SubscriptionResult subscription : approvedSubscriptions) {
            ProductResult product = productMap.get(subscription.getProductId());
            if (product == null) {
                logger.warn(
                        "Product not found for subscription, productId={}",
                        subscription.getProductId());
                continue;
            }

            if (product.getType() != ProductType.MCP_SERVER) {
                continue;
            }

            MarketMcpInfo mcpInfo = buildMarketMcpInfo(product);
            if (mcpInfo != null) {
                mcpServers.add(mcpInfo);
            }
        }

        // 5. Extract auth headers from CredentialContext.
        Map<String, String> authHeaders = extractAuthHeaders();

        // 6. Build the response.
        return MarketMcpsResponse.builder().mcpServers(mcpServers).authHeaders(authHeaders).build();
    }

    @Operation(summary = "List published Skills")
    @GetMapping("/market-skills")
    public List<MarketSkillInfo> listMarketSkills() {
        QueryProductParam param = new QueryProductParam();
        param.setType(ProductType.AGENT_SKILL);
        param.setStatus(ProductStatus.PUBLISHED);
        param.setPortalId(contextHolder.getPortal());

        PageResult<ProductResult> pageResult =
                productService.listProducts(param, PageRequest.of(0, 1000));

        return pageResult.getContent().stream()
                .map(
                        product -> {
                            List<String> skillTags = null;
                            if (product.getFeature() != null
                                    && product.getFeature().getSkillConfig() != null) {
                                skillTags = product.getFeature().getSkillConfig().getSkillTags();
                            }
                            return MarketSkillInfo.builder()
                                    .productId(product.getProductId())
                                    .name(product.getName())
                                    .description(product.getDescription())
                                    .skillTags(skillTags)
                                    .build();
                        })
                .toList();
    }

    private Map<String, String> extractAuthHeaders() {
        try {
            CredentialContext credentialContext =
                    consumerService.getDefaultCredential(contextHolder.getUser());
            Map<String, String> headers = credentialContext.copyHeaders();
            return headers.isEmpty() ? null : headers;
        } catch (Exception e) {
            logger.debug("Failed to get auth headers, errorMessage={}", e.getMessage(), e);
            return null;
        }
    }

    private MarketMcpInfo buildMarketMcpInfo(ProductResult product) {
        if (product.getMcpConfig() == null) {
            logger.warn(
                    "Product mcpConfig is incomplete, skipping, productId={}, name={}",
                    product.getProductId(),
                    product.getName());
            return null;
        }

        try {
            McpTransportConfig transportConfig = product.getMcpConfig().toTransportConfig();
            if (transportConfig == null) {
                logger.warn(
                        "Failed to extract transport config from product, skipping, productId={},"
                                + " name={}",
                        product.getProductId(),
                        product.getName());
                return null;
            }

            String transportType =
                    transportConfig.getTransportMode() == McpTransportMode.STREAMABLE_HTTP
                            ? "streamable-http"
                            : "sse";

            return MarketMcpInfo.builder()
                    .productId(product.getProductId())
                    .name(transportConfig.getMcpServerName())
                    .url(transportConfig.getUrl())
                    .transportType(transportType)
                    .description(product.getDescription())
                    .build();
        } catch (Exception e) {
            logger.warn(
                    "Error processing mcpConfig for product, skipping, productId={}, name={},"
                            + " errorMessage={}",
                    product.getProductId(),
                    product.getName(),
                    e.getMessage(),
                    e);
            return null;
        }
    }

    private String extractApiKey(String consumerId) {
        try {
            ConsumerCredentialResult credential = consumerService.getCredential(consumerId);
            if (credential == null || credential.getApiKeyConfig() == null) {
                return null;
            }
            ApiKeyConfig apiKeyConfig = credential.getApiKeyConfig();
            if (apiKeyConfig.getCredentials() == null || apiKeyConfig.getCredentials().isEmpty()) {
                return null;
            }
            return apiKeyConfig.getCredentials().get(0).getApiKey();
        } catch (Exception e) {
            logger.debug(
                    "Failed to get credential for consumer, consumerId={}, errorMessage={}",
                    consumerId,
                    e.getMessage(),
                    e);
            return null;
        }
    }

    private MarketModelInfo buildMarketModelInfo(ProductResult product) {
        // Extract modelId.
        String modelId = null;
        if (product.getFeature() != null
                && product.getFeature().getModelFeature() != null
                && product.getFeature().getModelFeature().getModel() != null) {
            modelId = product.getFeature().getModelFeature().getModel();
        }

        // Extract baseUrl.
        ModelConfigResult modelConfig = product.getModelConfig();
        if (modelConfig == null || modelConfig.getModelAPIConfig() == null) {
            logger.warn(
                    "Product modelConfig is incomplete, skipping, productId={}, name={}",
                    product.getProductId(),
                    product.getName());
            return null;
        }

        String baseUrl =
                BaseUrlExtractor.extract(
                        modelConfig.getModelAPIConfig().getRoutes(),
                        modelConfig.getModelAPIConfig().getAiProtocols());
        if (baseUrl == null) {
            logger.warn(
                    "Failed to extract baseUrl from product routes, skipping, productId={},"
                            + " name={}",
                    product.getProductId(),
                    product.getName());
            return null;
        }

        // Extract protocolType.
        String protocolType =
                ProtocolTypeMapper.map(modelConfig.getModelAPIConfig().getAiProtocols());

        return MarketModelInfo.builder()
                .productId(product.getProductId())
                .name(product.getName())
                .modelId(modelId)
                .baseUrl(baseUrl)
                .protocolType(protocolType)
                .description(product.getDescription())
                .build();
    }

    @Operation(summary = "Get HiCoding feature flags")
    @GetMapping("/features")
    public Map<String, Boolean> getFeatures() {
        return Map.of("terminalEnabled", acpProperties.isTerminalEnabled());
    }

    @Operation(summary = "List available CLI providers")
    @GetMapping
    public List<CliProviderInfo> listProviders() {
        List<CliProviderInfo> result = new ArrayList<>();
        String defaultKey = acpProperties.getDefaultProvider();
        for (Map.Entry<String, CliProviderConfig> entry : acpProperties.getProviders().entrySet()) {
            CliProviderConfig config = entry.getValue();
            // Providers compatible with the remote runtime can run in the sandbox.
            boolean canRunInSandbox =
                    config.getCompatibleRuntimes() != null
                            && config.getCompatibleRuntimes().contains(SandboxType.REMOTE);
            boolean available = canRunInSandbox || isCommandAvailable(config.getCommand());
            result.add(
                    new CliProviderInfo(
                            entry.getKey(),
                            config.getDisplayName() != null
                                    ? config.getDisplayName()
                                    : entry.getKey(),
                            entry.getKey().equals(defaultKey),
                            available,
                            config.getCompatibleRuntimes(),
                            config.isSupportsCustomModel(),
                            config.isSupportsMcp(),
                            config.isSupportsSkill(),
                            config.getAuthOptions(),
                            config.getAuthEnvVar()));
        }
        return result;
    }

    /**
     * Checks whether a command is available in the system PATH.
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
                    "Failed to check command availability, command={}, errorMessage={}",
                    command,
                    e.getMessage(),
                    e);
            return false;
        }
    }

    public record CliProviderInfo(
            String key,
            String displayName,
            boolean isDefault,
            boolean available,
            List<SandboxType> compatibleRuntimes,
            boolean supportsCustomModel,
            boolean supportsMcp,
            boolean supportsSkill,
            List<String> authOptions,
            String authEnvVar) {}
}
