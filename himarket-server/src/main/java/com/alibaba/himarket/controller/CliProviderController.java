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
import com.alibaba.himarket.support.chat.mcp.MCPTransportConfig;
import com.alibaba.himarket.support.consumer.ApiKeyConfig;
import com.alibaba.himarket.support.enums.MCPTransportMode;
import com.alibaba.himarket.support.enums.ProductStatus;
import com.alibaba.himarket.support.enums.ProductType;
import com.alibaba.himarket.support.enums.SubscriptionStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
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
    private final ConsumerService consumerService;
    private final ProductService productService;
    private final ContextHolder contextHolder;

    @Operation(summary = "获取当前开发者已订阅的模型市场模型列表")
    @GetMapping("/market-models")
    @DeveloperAuth
    public MarketModelsResponse listMarketModels() {
        // 1. 获取 Primary Consumer
        ConsumerResult consumer;
        try {
            consumer = consumerService.getPrimaryConsumer();
        } catch (Exception e) {
            logger.debug("No primary consumer found for current developer: {}", e.getMessage());
            return MarketModelsResponse.builder()
                    .models(Collections.emptyList())
                    .apiKey(null)
                    .build();
        }

        String consumerId = consumer.getConsumerId();

        // 2. 获取订阅列表，筛选 APPROVED 状态
        List<SubscriptionResult> subscriptions =
                consumerService.listConsumerSubscriptions(consumerId);
        List<SubscriptionResult> approvedSubscriptions =
                subscriptions.stream()
                        .filter(s -> SubscriptionStatus.APPROVED.name().equals(s.getStatus()))
                        .collect(Collectors.toList());

        // 3. 获取 apiKey
        String apiKey = extractApiKey(consumerId);

        if (approvedSubscriptions.isEmpty()) {
            return MarketModelsResponse.builder()
                    .models(Collections.emptyList())
                    .apiKey(apiKey)
                    .build();
        }

        // 4. 批量获取产品详情，然后按 MODEL_API 类型筛选
        List<String> productIds =
                approvedSubscriptions.stream()
                        .map(SubscriptionResult::getProductId)
                        .collect(Collectors.toList());
        Map<String, ProductResult> productMap = productService.getProducts(productIds);

        // 5. 对每个产品提取信息，仅处理 MODEL_API 类型
        List<MarketModelInfo> models = new ArrayList<>();
        for (SubscriptionResult subscription : approvedSubscriptions) {
            ProductResult product = productMap.get(subscription.getProductId());
            if (product == null) {
                logger.warn(
                        "Product not found for subscription: productId={}",
                        subscription.getProductId());
                continue;
            }

            // 通过产品详情中的 type 字段筛选 MODEL_API
            if (product.getType() != ProductType.MODEL_API) {
                continue;
            }

            MarketModelInfo modelInfo = buildMarketModelInfo(product);
            if (modelInfo != null) {
                models.add(modelInfo);
            }
        }

        // 6. 组装响应
        return MarketModelsResponse.builder().models(models).apiKey(apiKey).build();
    }

    @Operation(summary = "获取当前开发者已订阅的 MCP Server 列表")
    @GetMapping("/market-mcps")
    @DeveloperAuth
    public MarketMcpsResponse listMarketMcps() {
        // 1. 获取 Primary Consumer
        ConsumerResult consumer;
        try {
            consumer = consumerService.getPrimaryConsumer();
        } catch (Exception e) {
            logger.debug("No primary consumer found for current developer: {}", e.getMessage());
            return MarketMcpsResponse.builder()
                    .mcpServers(Collections.emptyList())
                    .authHeaders(null)
                    .build();
        }

        String consumerId = consumer.getConsumerId();

        // 2. 获取订阅列表，筛选 APPROVED 状态
        List<SubscriptionResult> subscriptions =
                consumerService.listConsumerSubscriptions(consumerId);
        List<SubscriptionResult> approvedSubscriptions =
                subscriptions.stream()
                        .filter(s -> SubscriptionStatus.APPROVED.name().equals(s.getStatus()))
                        .collect(Collectors.toList());

        if (approvedSubscriptions.isEmpty()) {
            return MarketMcpsResponse.builder()
                    .mcpServers(Collections.emptyList())
                    .authHeaders(extractAuthHeaders())
                    .build();
        }

        // 3. 批量获取产品详情，筛选 MCP_SERVER 类型
        List<String> productIds =
                approvedSubscriptions.stream()
                        .map(SubscriptionResult::getProductId)
                        .collect(Collectors.toList());
        Map<String, ProductResult> productMap = productService.getProducts(productIds);

        // 4. 对每个产品提取 MCP 信息
        List<MarketMcpInfo> mcpServers = new ArrayList<>();
        for (SubscriptionResult subscription : approvedSubscriptions) {
            ProductResult product = productMap.get(subscription.getProductId());
            if (product == null) {
                logger.warn(
                        "Product not found for subscription: productId={}",
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

        // 5. 获取 CredentialContext 提取 authHeaders
        Map<String, String> authHeaders = extractAuthHeaders();

        // 6. 组装响应
        return MarketMcpsResponse.builder().mcpServers(mcpServers).authHeaders(authHeaders).build();
    }

    @Operation(summary = "获取已发布的 Skill 列表")
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
                .collect(Collectors.toList());
    }

    private Map<String, String> extractAuthHeaders() {
        try {
            CredentialContext credentialContext =
                    consumerService.getDefaultCredential(contextHolder.getUser());
            Map<String, String> headers = credentialContext.copyHeaders();
            return headers.isEmpty() ? null : headers;
        } catch (Exception e) {
            logger.debug("Failed to get auth headers: {}", e.getMessage());
            return null;
        }
    }

    private MarketMcpInfo buildMarketMcpInfo(ProductResult product) {
        if (product.getMcpConfig() == null) {
            logger.warn(
                    "Product mcpConfig is incomplete, skipping: productId={}, name={}",
                    product.getProductId(),
                    product.getName());
            return null;
        }

        try {
            MCPTransportConfig transportConfig = product.getMcpConfig().toTransportConfig();
            if (transportConfig == null) {
                logger.warn(
                        "Failed to extract transport config from product, skipping: productId={},"
                                + " name={}",
                        product.getProductId(),
                        product.getName());
                return null;
            }

            String transportType =
                    transportConfig.getTransportMode() == MCPTransportMode.STREAMABLE_HTTP
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
                    "Error processing mcpConfig for product, skipping: productId={}, name={},"
                            + " error={}",
                    product.getProductId(),
                    product.getName(),
                    e.getMessage());
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
                    "Failed to get credential for consumer {}: {}", consumerId, e.getMessage());
            return null;
        }
    }

    private MarketModelInfo buildMarketModelInfo(ProductResult product) {
        // 提取 modelId
        String modelId = null;
        if (product.getFeature() != null
                && product.getFeature().getModelFeature() != null
                && product.getFeature().getModelFeature().getModel() != null) {
            modelId = product.getFeature().getModelFeature().getModel();
        }

        // 提取 baseUrl
        ModelConfigResult modelConfig = product.getModelConfig();
        if (modelConfig == null || modelConfig.getModelAPIConfig() == null) {
            logger.warn(
                    "Product modelConfig is incomplete, skipping: productId={}, name={}",
                    product.getProductId(),
                    product.getName());
            return null;
        }

        String baseUrl = BaseUrlExtractor.extract(modelConfig.getModelAPIConfig().getRoutes());
        if (baseUrl == null) {
            logger.warn(
                    "Failed to extract baseUrl from product routes, skipping: productId={},"
                            + " name={}",
                    product.getProductId(),
                    product.getName());
            return null;
        }

        // 提取 protocolType
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

    @Operation(summary = "获取 HiCoding 功能开关状态")
    @GetMapping("/features")
    public Map<String, Boolean> getFeatures() {
        return Map.of("terminalEnabled", acpProperties.isTerminalEnabled());
    }

    @Operation(summary = "获取可用的 CLI Provider 列表（含运行时兼容性信息）")
    @GetMapping
    public List<CliProviderInfo> listProviders() {
        List<CliProviderInfo> result = new ArrayList<>();
        String defaultKey = acpProperties.getDefaultProvider();
        for (Map.Entry<String, CliProviderConfig> entry : acpProperties.getProviders().entrySet()) {
            CliProviderConfig config = entry.getValue();
            // 兼容 K8S 运行时的 Provider 可在沙箱中运行，无需本机安装命令
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
            List<SandboxType> compatibleRuntimes,
            boolean supportsCustomModel,
            boolean supportsMcp,
            boolean supportsSkill,
            List<String> authOptions,
            String authEnvVar) {}
}
