package com.alibaba.himarket.service.hicoding.session;

import com.alibaba.himarket.dto.result.consumer.ConsumerCredentialResult;
import com.alibaba.himarket.dto.result.consumer.ConsumerResult;
import com.alibaba.himarket.dto.result.model.ModelConfigResult;
import com.alibaba.himarket.dto.result.product.ProductResult;
import com.alibaba.himarket.dto.result.product.SubscriptionResult;
import com.alibaba.himarket.service.ConsumerService;
import com.alibaba.himarket.service.ProductService;
import com.alibaba.himarket.service.hicoding.cli.ProtocolTypeMapper;
import com.alibaba.himarket.service.hicoding.filesystem.BaseUrlExtractor;
import com.alibaba.himarket.support.consumer.ApiKeyConfig;
import com.alibaba.himarket.support.enums.SubscriptionStatus;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Resolves complete model configuration from a marketplace product ID.
 *
 * <p>Resolution flow:
 * <ol>
 *   <li>Load the current developer's primary consumer.</li>
 *   <li>Load subscriptions and keep APPROVED products.</li>
 *   <li>Load product details, then extract baseUrl, protocolType, and modelId.</li>
 *   <li>Load the API key.</li>
 *   <li>Build CustomModelConfig.</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ModelConfigResolver {

    private final ConsumerService consumerService;
    private final ProductService productService;

    /**
     * Resolves complete model configuration from a marketplace product ID.
     *
     * @param modelProductId marketplace product ID
     * @param userId developer ID used by async threads instead of SecurityContextHolder
     * @return resolved CustomModelConfig, or null when resolution fails
     */
    public CustomModelConfig resolve(String modelProductId, String userId) {
        log.info("Resolving model config, modelProductId={}", modelProductId);

        // 1. Load the primary consumer.
        ConsumerResult consumer;
        try {
            consumer = consumerService.getPrimaryConsumer(userId);
            log.info("Resolved primary consumer, consumerId={}", consumer.getConsumerId());
        } catch (Exception e) {
            log.warn("Failed to resolve primary consumer, errorMessage={}", e.getMessage(), e);
            return null;
        }

        String consumerId = consumer.getConsumerId();

        // 2. Load subscriptions and keep APPROVED products.
        List<SubscriptionResult> subscriptions =
                consumerService.listConsumerSubscriptions(consumerId);
        List<String> approvedProductIds =
                subscriptions.stream()
                        .filter(s -> SubscriptionStatus.APPROVED.name().equals(s.getStatus()))
                        .map(SubscriptionResult::getProductId)
                        .toList();

        log.info(
                "Loaded consumer subscriptions, total={}, approved={}",
                subscriptions.size(),
                approvedProductIds.size());

        if (!approvedProductIds.contains(modelProductId)) {
            log.warn(
                    "Model product is not approved for this consumer, modelProductId={}",
                    modelProductId);
            return null;
        }

        // 3. Load product details.
        Map<String, ProductResult> productMap = productService.getProducts(List.of(modelProductId));
        ProductResult product = productMap.get(modelProductId);
        if (product == null) {
            log.warn("Model product not found, modelProductId={}", modelProductId);
            return null;
        }

        log.info("Loaded model product, name={}, type={}", product.getName(), product.getType());

        // 4. Extract baseUrl.
        ModelConfigResult modelConfig = product.getModelConfig();
        if (modelConfig == null || modelConfig.getModelAPIConfig() == null) {
            log.warn(
                    "Model config is incomplete, modelProductId={}, name={}",
                    modelProductId,
                    product.getName());
            return null;
        }

        String baseUrl =
                BaseUrlExtractor.extract(
                        modelConfig.getModelAPIConfig().getRoutes(),
                        modelConfig.getModelAPIConfig().getAiProtocols());
        if (baseUrl == null) {
            log.warn(
                    "Failed to extract baseUrl from model routes, modelProductId={}, name={}",
                    modelProductId,
                    product.getName());
            return null;
        }

        log.info("Extracted model base URL, baseUrl={}", baseUrl);

        // 5. Extract protocolType.
        String protocolType =
                ProtocolTypeMapper.map(modelConfig.getModelAPIConfig().getAiProtocols());

        // 6. Extract modelId.
        String modelId = null;
        if (product.getFeature() != null
                && product.getFeature().getModelFeature() != null
                && product.getFeature().getModelFeature().getModel() != null) {
            modelId = product.getFeature().getModelFeature().getModel();
        }

        // 7. Load apiKey.
        String apiKey = extractApiKey(consumerId);
        if (apiKey == null) {
            log.warn(
                    "Failed to extract API key, modelProductId={}, consumerId={}",
                    modelProductId,
                    consumerId);
            return null;
        }

        // 8. Build CustomModelConfig.
        CustomModelConfig config = new CustomModelConfig();
        config.setBaseUrl(baseUrl);
        config.setApiKey(apiKey);
        config.setModelId(modelId);
        config.setModelName(product.getName());
        config.setProtocolType(protocolType);
        return config;
    }

    private String extractApiKey(String consumerId) {
        log.info("Extracting API key for model config, consumerId={}", consumerId);
        try {
            ConsumerCredentialResult credential = consumerService.getCredential(consumerId);
            if (credential == null) {
                log.warn("Credential is missing for model config, consumerId={}", consumerId);
                return null;
            }
            if (credential.getApiKeyConfig() == null) {
                log.warn("API key config is missing for model config, consumerId={}", consumerId);
                return null;
            }
            ApiKeyConfig apiKeyConfig = credential.getApiKeyConfig();
            if (apiKeyConfig.getCredentials() == null || apiKeyConfig.getCredentials().isEmpty()) {
                log.warn(
                        "API key credentials are empty for model config, consumerId={}",
                        consumerId);
                return null;
            }
            String apiKey = apiKeyConfig.getCredentials().get(0).getApiKey();
            log.info("Extracted API key for model config, consumerId={}", consumerId);
            return apiKey;
        } catch (Exception e) {
            log.warn(
                    "Failed to extract API key for model config, consumerId={}, errorMessage={}",
                    consumerId,
                    e.getMessage(),
                    e);
            return null;
        }
    }
}
