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
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 根据市场产品 ID 解析完整模型配置的服务。
 *
 * <p>解析流程：
 * <ol>
 *   <li>获取当前开发者的 Primary Consumer</li>
 *   <li>获取订阅列表并筛选 APPROVED 状态</li>
 *   <li>获取产品详情，提取 baseUrl、protocolType、modelId</li>
 *   <li>获取 apiKey</li>
 *   <li>组装 CustomModelConfig</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ModelConfigResolver {

    private final ConsumerService consumerService;
    private final ProductService productService;

    /**
     * 根据市场产品 ID 解析完整模型配置。
     *
     * @param modelProductId 市场产品 ID
     * @param userId 开发者 ID（用于异步线程上下文，避免依赖 SecurityContextHolder）
     * @return 解析后的 CustomModelConfig，解析失败时返回 null
     */
    public CustomModelConfig resolve(String modelProductId, String userId) {
        log.info("[ModelConfigResolver] ===== 开始解析 ===== modelProductId={}", modelProductId);

        // 1. 获取 Primary Consumer
        ConsumerResult consumer;
        try {
            consumer = consumerService.getPrimaryConsumer(userId);
            log.info(
                    "[ModelConfigResolver] Primary Consumer 获取成功: consumerId={}",
                    consumer.getConsumerId());
        } catch (Exception e) {
            log.warn("[ModelConfigResolver] 无法获取 Primary Consumer: {}", e.getMessage());
            return null;
        }

        String consumerId = consumer.getConsumerId();

        // 2. 获取订阅列表，筛选 APPROVED 状态
        List<SubscriptionResult> subscriptions =
                consumerService.listConsumerSubscriptions(consumerId);
        List<String> approvedProductIds =
                subscriptions.stream()
                        .filter(s -> SubscriptionStatus.APPROVED.name().equals(s.getStatus()))
                        .map(SubscriptionResult::getProductId)
                        .collect(Collectors.toList());

        log.info(
                "[ModelConfigResolver] 订阅列表: total={}, approved={}",
                subscriptions.size(),
                approvedProductIds.size());

        if (!approvedProductIds.contains(modelProductId)) {
            log.warn("[ModelConfigResolver] 产品未订阅或订阅未批准: modelProductId={}", modelProductId);
            return null;
        }

        // 3. 获取产品详情
        Map<String, ProductResult> productMap = productService.getProducts(List.of(modelProductId));
        ProductResult product = productMap.get(modelProductId);
        if (product == null) {
            log.warn("[ModelConfigResolver] 产品不存在: modelProductId={}", modelProductId);
            return null;
        }

        log.info(
                "[ModelConfigResolver] 产品获取成功: name={}, type={}",
                product.getName(),
                product.getType());

        // 4. 提取 baseUrl
        ModelConfigResult modelConfig = product.getModelConfig();
        if (modelConfig == null || modelConfig.getModelAPIConfig() == null) {
            log.warn(
                    "[ModelConfigResolver] 产品 modelConfig 不完整: modelProductId={}, name={}",
                    modelProductId,
                    product.getName());
            return null;
        }

        String baseUrl = BaseUrlExtractor.extract(modelConfig.getModelAPIConfig().getRoutes());
        if (baseUrl == null) {
            log.warn(
                    "[ModelConfigResolver] 无法从路由中提取 baseUrl: modelProductId={}, name={}",
                    modelProductId,
                    product.getName());
            return null;
        }

        log.info("[ModelConfigResolver] baseUrl 提取成功: {}", baseUrl);

        // 5. 提取 protocolType
        String protocolType =
                ProtocolTypeMapper.map(modelConfig.getModelAPIConfig().getAiProtocols());

        // 6. 提取 modelId
        String modelId = null;
        if (product.getFeature() != null
                && product.getFeature().getModelFeature() != null
                && product.getFeature().getModelFeature().getModel() != null) {
            modelId = product.getFeature().getModelFeature().getModel();
        }

        // 7. 提取 apiKey
        String apiKey = extractApiKey(consumerId);
        if (apiKey == null) {
            log.warn(
                    "Failed to extract apiKey: modelProductId={}, consumerId={}",
                    modelProductId,
                    consumerId);
            return null;
        }

        // 8. 组装 CustomModelConfig
        CustomModelConfig config = new CustomModelConfig();
        config.setBaseUrl(baseUrl);
        config.setApiKey(apiKey);
        config.setModelId(modelId);
        config.setModelName(product.getName());
        config.setProtocolType(protocolType);
        return config;
    }

    private String extractApiKey(String consumerId) {
        log.info("[ModelConfigResolver] 开始提取 apiKey: consumerId={}", consumerId);
        try {
            ConsumerCredentialResult credential = consumerService.getCredential(consumerId);
            if (credential == null) {
                log.warn("[ModelConfigResolver] credential 为 null: consumerId={}", consumerId);
                return null;
            }
            if (credential.getApiKeyConfig() == null) {
                log.warn("[ModelConfigResolver] apiKeyConfig 为 null: consumerId={}", consumerId);
                return null;
            }
            ApiKeyConfig apiKeyConfig = credential.getApiKeyConfig();
            if (apiKeyConfig.getCredentials() == null || apiKeyConfig.getCredentials().isEmpty()) {
                log.warn("[ModelConfigResolver] credentials 为空: consumerId={}", consumerId);
                return null;
            }
            String apiKey = apiKeyConfig.getCredentials().get(0).getApiKey();
            log.info("[ModelConfigResolver] apiKey 提取成功: consumerId={}", consumerId);
            return apiKey;
        } catch (Exception e) {
            log.warn(
                    "[ModelConfigResolver] 提取 apiKey 失败: consumerId={}, error={}",
                    consumerId,
                    e.getMessage(),
                    e);
            return null;
        }
    }
}
