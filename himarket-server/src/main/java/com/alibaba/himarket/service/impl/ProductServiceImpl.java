/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.alibaba.himarket.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.extra.spring.SpringUtil;
import cn.hutool.json.JSONUtil;
import com.alibaba.himarket.core.constant.Resources;
import com.alibaba.himarket.core.event.PortalDeletingEvent;
import com.alibaba.himarket.core.event.ProductConfigReloadEvent;
import com.alibaba.himarket.core.event.ProductDeletingEvent;
import com.alibaba.himarket.core.exception.BusinessException;
import com.alibaba.himarket.core.exception.ErrorCode;
import com.alibaba.himarket.core.security.ContextHolder;
import com.alibaba.himarket.core.utils.CacheUtil;
import com.alibaba.himarket.core.utils.IdGenerator;
import com.alibaba.himarket.dto.params.product.*;
import com.alibaba.himarket.dto.result.ProductCategoryResult;
import com.alibaba.himarket.dto.result.agent.AgentConfigResult;
import com.alibaba.himarket.dto.result.common.PageResult;
import com.alibaba.himarket.dto.result.common.VersionResult;
import com.alibaba.himarket.dto.result.consumer.CredentialContext;
import com.alibaba.himarket.dto.result.gateway.GatewayResult;
import com.alibaba.himarket.dto.result.httpapi.APIConfigResult;
import com.alibaba.himarket.dto.result.mcp.MCPConfigResult;
import com.alibaba.himarket.dto.result.mcp.McpToolListResult;
import com.alibaba.himarket.dto.result.model.ModelConfigResult;
import com.alibaba.himarket.dto.result.nacos.NacosResult;
import com.alibaba.himarket.dto.result.portal.PortalResult;
import com.alibaba.himarket.dto.result.product.*;
import com.alibaba.himarket.entity.*;
import com.alibaba.himarket.repository.*;
import com.alibaba.himarket.service.*;
import com.alibaba.himarket.service.hichat.manager.ToolManager;
import com.alibaba.himarket.service.mcp.McpProtocolUtils;
import com.alibaba.himarket.service.mcp.McpToolsConfigParser;
import com.alibaba.himarket.support.chat.mcp.MCPTransportConfig;
import com.alibaba.himarket.support.enums.McpEndpointStatus;
import com.alibaba.himarket.support.enums.ProductStatus;
import com.alibaba.himarket.support.enums.ProductType;
import com.alibaba.himarket.support.enums.SourceType;
import com.alibaba.himarket.support.product.*;
import com.github.benmanes.caffeine.cache.Cache;
import io.agentscope.core.tool.mcp.McpClientWrapper;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import jakarta.transaction.Transactional;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional
public class ProductServiceImpl implements ProductService {

    private final ContextHolder contextHolder;

    private final PortalService portalService;

    private final GatewayService gatewayService;

    private final ProductRepository productRepository;

    private final ProductRefRepository productRefRepository;

    private final ProductPublicationRepository publicationRepository;

    private final SubscriptionRepository subscriptionRepository;

    private final ConsumerRepository consumerRepository;

    private final NacosService nacosService;

    private final ProductCategoryService productCategoryService;

    private final ToolManager toolManager;

    private final McpServerMetaRepository mcpServerMetaRepository;

    private final McpServerEndpointRepository mcpServerEndpointRepository;

    private final McpServerService mcpServerService;

    private final WorkerService workerService;

    private final SkillService skillService;

    /**
     * Cache to prevent duplicate sync within interval (5 minutes default)
     */
    private final Cache<String, Boolean> productSyncCache = CacheUtil.newCache(5);

    @Override
    public ProductResult createProduct(CreateProductParam param) {
        productRepository
                .findByNameAndAdminId(param.getName(), contextHolder.getUser())
                .ifPresent(
                        product -> {
                            throw new BusinessException(
                                    ErrorCode.CONFLICT,
                                    StrUtil.format(
                                            "Product with name '{}' already exists",
                                            product.getName()));
                        });

        String productId = IdGenerator.genApiProductId();

        Product product = param.convertTo();
        product.setProductId(productId);
        product.setAdminId(contextHolder.getUser());

        // Set feature for AGENT_SKILL / WORKER products
        initDefaultFeature(product);

        validateModelFeature(product.getType(), product.getFeature());

        productRepository.save(product);

        // Set product categories
        setProductCategories(productId, param.getCategories());

        return getProduct(productId);
    }

    private void initDefaultFeature(Product product) {
        ProductType productType = product.getType();
        if (productType != ProductType.AGENT_SKILL && productType != ProductType.WORKER) {
            return;
        }

        NacosResult nacos = nacosService.getDefaultNacosInstance();
        if (nacos == null) {
            return;
        }

        ProductFeature feature =
                Optional.ofNullable(product.getFeature()).orElse(ProductFeature.builder().build());

        if (productType == ProductType.AGENT_SKILL) {
            feature.setSkillConfig(
                    SkillConfig.builder()
                            .nacosId(nacos.getNacosId())
                            .namespace(nacos.getDefaultNamespace())
                            .build());
        } else {
            feature.setWorkerConfig(
                    WorkerConfig.builder()
                            .nacosId(nacos.getNacosId())
                            .namespace(nacos.getDefaultNamespace())
                            .build());
        }

        product.setFeature(feature);
    }

    @Override
    public ProductResult getProduct(String productId) {
        Product product =
                contextHolder.isAdministrator()
                        ? findProduct(productId)
                        : findPublishedProduct(contextHolder.getPortal(), productId);

        // Trigger async sync if not synced recently (cache miss)
        if (productSyncCache.getIfPresent(productId) == null) {
            productRefRepository
                    .findByProductId(productId)
                    .ifPresent(
                            o -> {
                                productSyncCache.put(productId, Boolean.TRUE);
                                SpringUtil.getApplicationContext()
                                        .publishEvent(new ProductConfigReloadEvent(productId));
                            });
        }

        ProductResult result = new ProductResult().convertFrom(product);

        // Fill product information
        fillProducts(Collections.singletonList(result));
        return result;
    }

    @Override
    public PageResult<ProductResult> listProducts(QueryProductParam param, Pageable pageable) {
        if (!contextHolder.isAdministrator()) {
            param.setPortalId(contextHolder.getPortal());
        }

        // Non-admin users can only see published products
        if (!contextHolder.isAdministrator()) {
            param.setStatus(ProductStatus.PUBLISHED);
        }

        if (param.getType() != null && param.hasFilter()) {
            return listProductsWithFilter(param, pageable);
        }

        // Skill/Worker: sort by updated time (default) or download count
        if (param.getType() == ProductType.AGENT_SKILL || param.getType() == ProductType.WORKER) {
            if (param.getSortBy() == ProductSortBy.DOWNLOAD_COUNT) {
                return listProductsSortedByDownloadCount(param, pageable);
            }
            // UPDATED_AT (default): use DB-level sort
            Pageable sortedPageable =
                    org.springframework.data.domain.PageRequest.of(
                            pageable.getPageNumber(),
                            pageable.getPageSize(),
                            org.springframework.data.domain.Sort.by(
                                    org.springframework.data.domain.Sort.Direction.DESC,
                                    "updatedAt"));
            Page<Product> page =
                    productRepository.findAll(buildSpecification(param), sortedPageable);
            List<ProductResult> results =
                    page.stream()
                            .map(product -> new ProductResult().convertFrom(product))
                            .collect(Collectors.toList());
            fillProducts(results);
            return PageResult.of(
                    results, page.getNumber() + 1, page.getSize(), page.getTotalElements());
        }

        Page<Product> page = productRepository.findAll(buildSpecification(param), pageable);
        List<ProductResult> results =
                page.stream()
                        .map(product -> new ProductResult().convertFrom(product))
                        .collect(Collectors.toList());

        // Fill product information
        fillProducts(results);

        return PageResult.of(
                results, page.getNumber() + 1, page.getSize(), page.getTotalElements());
    }

    @Override
    public ProductResult updateProduct(String productId, UpdateProductParam param) {
        Product product = findProduct(productId);
        // Change API product type
        if (param.getType() != null && product.getType() != param.getType()) {
            productRefRepository
                    .findFirstByProductId(productId)
                    .ifPresent(
                            productRef -> {
                                throw new BusinessException(
                                        ErrorCode.INVALID_REQUEST,
                                        "API product already linked to API");
                            });
            // Clear product features
            product.setFeature(null);
            param.setFeature(null);
        }
        param.update(product);

        validateModelFeature(product.getType(), product.getFeature());

        productRepository.saveAndFlush(product);

        // Set product categories
        setProductCategories(product.getProductId(), param.getCategories());

        return getProduct(product.getProductId());
    }

    @Override
    public void publishProduct(String productId, String portalId) {
        portalService.existsPortal(portalId);
        if (publicationRepository.findByPortalIdAndProductId(portalId, productId).isPresent()) {
            // Already published to this portal, ensure product status is correct
            Product product = findProduct(productId);
            if (product.getStatus() != ProductStatus.PUBLISHED) {
                product.setStatus(ProductStatus.PUBLISHED);
                productRepository.save(product);
            }
            return;
        }

        Product product = findProduct(productId);

        // Validate Nacos online version for AGENT_SKILL and WORKER types
        validateNacosOnlineVersion(product);

        product.setStatus(ProductStatus.PUBLISHED);

        ProductPublication productPublication = new ProductPublication();
        productPublication.setPublicationId(IdGenerator.genPublicationId());
        productPublication.setPortalId(portalId);
        productPublication.setProductId(productId);

        publicationRepository.save(productPublication);
        productRepository.save(product);
    }

    @Override
    public PageResult<ProductPublicationResult> getPublications(
            String productId, Pageable pageable) {
        Page<ProductPublication> publications =
                publicationRepository.findByProductId(productId, pageable);

        return new PageResult<ProductPublicationResult>()
                .convertFrom(
                        publications,
                        publication -> {
                            ProductPublicationResult publicationResult =
                                    new ProductPublicationResult().convertFrom(publication);
                            PortalResult portal;
                            try {
                                portal = portalService.getPortal(publication.getPortalId());
                            } catch (Exception e) {
                                log.error("Failed to get portal: {}", publication.getPortalId(), e);
                                return null;
                            }

                            publicationResult.setPortalName(portal.getName());
                            publicationResult.setAutoApproveSubscriptions(
                                    portal.getPortalSettingConfig().getAutoApproveSubscriptions());

                            return publicationResult;
                        });
    }

    @Override
    public void unpublishProduct(String productId, String publicationId) {
        Product product = findProduct(productId);

        // Find publication by publicationId
        ProductPublication publication =
                publicationRepository
                        .findByPublicationId(publicationId)
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                ErrorCode.NOT_FOUND,
                                                Resources.PUBLICATION,
                                                publicationId));

        // Verify: ensure publication belongs to this product
        if (!publication.getProductId().equals(productId)) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST, "Publication does not belong to this product");
        }

        String portalId = publication.getPortalId();
        portalService.existsPortal(portalId);

        /*
         * Update product status:
         * If the product is published in other portals -> set to PUBLISHED
         * If not published anywhere else -> set to READY
         */
        ProductStatus toStatus =
                publicationRepository.existsByProductIdAndPortalIdNot(productId, portalId)
                        ? ProductStatus.PUBLISHED
                        : ProductStatus.READY;
        product.setStatus(toStatus);

        publicationRepository.delete(publication);
        productRepository.save(product);
    }

    @Override
    public void deleteProduct(String productId) {
        Product product = findProduct(productId);

        // Unpublish from all portals first
        publicationRepository.deleteByProductId(productId);

        // Cascade delete Nacos versions for WORKER/AGENT_SKILL products
        cleanupNacosResources(product);

        // Clear product category relationships
        clearProductCategoryRelations(productId);

        // MCP 产品：级联删除 mcp_server_meta、mcp_server_endpoint 及沙箱资源
        if (product.getType() == ProductType.MCP_SERVER) {
            mcpServerService.forceDeleteMetaByProduct(productId);
        }

        productRepository.delete(product);
        productRefRepository.deleteByProductId(productId);

        // Asynchronously clean up product resources
        SpringUtil.getApplicationContext().publishEvent(new ProductDeletingEvent(productId));
    }

    private void cleanupNacosResources(Product product) {
        try {
            switch (product.getType()) {
                case WORKER -> workerService.deleteAgentSpec(product.getProductId());
                case AGENT_SKILL -> skillService.deleteSkill(product.getProductId());
                default -> {}
            }
        } catch (Exception e) {
            log.warn(
                    "Failed to cleanup Nacos resources for product {}, continuing with deletion",
                    product.getProductId(),
                    e);
        }
    }

    private void validateNacosOnlineVersion(Product product) {
        if (product.getType() == ProductType.AGENT_SKILL) {
            List<VersionResult> versions = skillService.listVersions(product.getProductId());
            if (versions.stream().noneMatch(v -> "online".equals(v.getStatus()))) {
                throw new BusinessException(
                        ErrorCode.INVALID_REQUEST,
                        "Cannot publish: no online version found in Nacos");
            }
        } else if (product.getType() == ProductType.WORKER) {
            List<VersionResult> versions = workerService.listVersions(product.getProductId());
            if (versions.stream().noneMatch(v -> "online".equals(v.getStatus()))) {
                throw new BusinessException(
                        ErrorCode.INVALID_REQUEST,
                        "Cannot publish: no online version found in Nacos");
            }
        }
    }

    private void validateModelFeature(ProductType type, ProductFeature feature) {
        if (type == ProductType.MODEL_API) {
            if (feature == null
                    || feature.getModelFeature() == null
                    || StrUtil.isBlank(feature.getModelFeature().getModel())) {
                throw new BusinessException(
                        ErrorCode.INVALID_REQUEST, "MODEL_API product must specify a model name");
            }
        }
    }

    private Product findProduct(String productId) {
        return productRepository
                .findByProductId(productId)
                .orElseThrow(
                        () ->
                                new BusinessException(
                                        ErrorCode.NOT_FOUND, Resources.PRODUCT, productId));
    }

    @Override
    public void addProductRef(String productId, CreateProductRefParam param) {
        Product product = findProduct(productId);

        // Check if API reference already exists
        productRefRepository
                .findByProductId(product.getProductId())
                .ifPresent(
                        productRef -> {
                            throw new BusinessException(
                                    ErrorCode.CONFLICT, "Product is already linked to an API");
                        });
        ProductRef productRef = param.convertTo();
        productRef.setProductId(productId);
        syncConfig(product, productRef);

        // Update status
        product.setStatus(ProductStatus.READY);
        productRef.setEnabled(true);

        productRepository.save(product);
        productRefRepository.save(productRef);
        // MCP 产品的 meta 已在 syncConfig → syncMcpConfigToMeta 中创建/更新
    }

    @Override
    public ProductRefResult getProductRef(String productId) {
        return productRefRepository
                .findFirstByProductId(productId)
                .map(productRef -> new ProductRefResult().convertFrom(productRef))
                .orElse(null);
    }

    @Override
    public void deleteProductRef(String productId) {
        Product product = findProduct(productId);

        // Published products cannot be unbound
        if (publicationRepository.existsByProductId(productId)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "该 API 产品已发布，无法解绑");
        }

        ProductRef productRef =
                productRefRepository
                        .findFirstByProductId(productId)
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                ErrorCode.INVALID_REQUEST,
                                                "API product not linked to API"));

        // MCP 产品：级联删除 mcp_server_meta、mcp_server_endpoint 及沙箱资源
        if (product.getType() == ProductType.MCP_SERVER) {
            mcpServerService.forceDeleteMetaByProduct(productId);
        } else {
            productRefRepository.delete(productRef);
        }
        product.setStatus(ProductStatus.PENDING);
        productRepository.save(product);
        productSyncCache.invalidate(productId);
    }

    @EventListener
    @Async("taskExecutor")
    public void onPortalDeletion(PortalDeletingEvent event) {
        String portalId = event.getPortalId();
        try {
            publicationRepository.deleteAllByPortalId(portalId);

            log.info("Completed cleanup publications for portal {}", portalId);
        } catch (Exception e) {
            log.error("Failed to unpublish products for portal {}: {}", portalId, e.getMessage());
        }
    }

    @Override
    public Map<String, ProductResult> getProducts(List<String> productIds) {
        List<Product> products = productRepository.findByProductIdIn(productIds);

        List<ProductResult> productResults =
                products.stream().map(product -> new ProductResult().convertFrom(product)).toList();

        fillProducts(productResults);

        return productResults.stream()
                .collect(Collectors.toMap(ProductResult::getProductId, Function.identity()));
    }

    @Override
    public PageResult<SubscriptionResult> listProductSubscriptions(
            String productId, QueryProductSubscriptionParam param, Pageable pageable) {
        existsProduct(productId);
        Page<ProductSubscription> subscriptions =
                subscriptionRepository.findAll(
                        buildProductSubscriptionSpec(productId, param), pageable);

        List<String> consumerIds =
                subscriptions.getContent().stream()
                        .map(ProductSubscription::getConsumerId)
                        .collect(Collectors.toList());
        if (CollUtil.isEmpty(consumerIds)) {
            return PageResult.empty(pageable.getPageNumber(), pageable.getPageSize());
        }

        Map<String, Consumer> consumers =
                consumerRepository.findByConsumerIdIn(consumerIds).stream()
                        .collect(Collectors.toMap(Consumer::getConsumerId, consumer -> consumer));

        return new PageResult<SubscriptionResult>()
                .convertFrom(
                        subscriptions,
                        s -> {
                            SubscriptionResult r = new SubscriptionResult().convertFrom(s);
                            Consumer consumer = consumers.get(r.getConsumerId());
                            if (consumer != null) {
                                r.setConsumerName(consumer.getName());
                            }
                            return r;
                        });
    }

    @Override
    public void existsProduct(String productId) {
        productRepository
                .findByProductId(productId)
                .orElseThrow(
                        () ->
                                new BusinessException(
                                        ErrorCode.NOT_FOUND, Resources.PRODUCT, productId));
    }

    @Override
    public void existsProducts(List<String> productIds) {
        List<String> existedProductIds =
                productRepository.findByProductIdIn(productIds).stream()
                        .map(Product::getProductId)
                        .toList();

        List<String> notFoundProductIds =
                productIds.stream()
                        .filter(productId -> !existedProductIds.contains(productId))
                        .collect(Collectors.toList());

        if (!notFoundProductIds.isEmpty()) {
            throw new BusinessException(
                    ErrorCode.NOT_FOUND, Resources.PRODUCT, String.join(",", notFoundProductIds));
        }
    }

    @Override
    public void setProductCategories(String productId, List<String> categoryIds) {
        existsProduct(productId);

        productCategoryService.unbindAllProductCategories(productId);
        productCategoryService.bindProductCategories(productId, categoryIds);
    }

    @Override
    public void clearProductCategoryRelations(String productId) {
        productCategoryService.unbindAllProductCategories(productId);
    }

    @Override
    public void reloadProductConfig(String productId) {
        Product product = findProduct(productId);
        ProductRef productRef =
                productRefRepository
                        .findFirstByProductId(productId)
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                ErrorCode.INVALID_REQUEST,
                                                "API product not linked to API"));

        // Update cache to prevent immediate re-sync
        productSyncCache.put(productId, Boolean.TRUE);

        syncConfig(product, productRef);
        syncMcpTools(product, productRef);
        productRefRepository.saveAndFlush(productRef);
    }

    @Override
    public McpToolListResult listMcpTools(String productId) {
        ProductResult product = getProduct(productId);
        if (product.getType() != ProductType.MCP_SERVER) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST, "API product is not a mcp server");
        }

        ConsumerService consumerService =
                SpringUtil.getApplicationContext().getBean(ConsumerService.class);
        String consumerId = consumerService.getPrimaryConsumer().getConsumerId();
        // Check subscription status
        subscriptionRepository
                .findByConsumerIdAndProductId(consumerId, productId)
                .orElseThrow(
                        () ->
                                new BusinessException(
                                        ErrorCode.INVALID_REQUEST,
                                        "API product is not subscribed, not allowed to list"
                                                + " tools"));

        // Initialize client and fetch tools
        // mcpConfig 已由 fillProductConfig 从 McpServerMeta + endpoint 构建
        if (product.getMcpConfig() == null) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "MCP 配置不可用，请检查 MCP 连接配置");
        }
        MCPTransportConfig transportConfig = product.getMcpConfig().toTransportConfig();
        if (transportConfig == null) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "无法从 MCP 配置中提取传输配置");
        }
        CredentialContext credentialContext =
                consumerService.getDefaultCredential(contextHolder.getUser());
        transportConfig.setHeaders(credentialContext.copyHeaders());
        transportConfig.setQueryParams(credentialContext.copyQueryParams());

        McpToolListResult result = new McpToolListResult();

        McpClientWrapper mcpClientWrapper = toolManager.getOrCreateClient(transportConfig);
        if (mcpClientWrapper == null) {
            throw new BusinessException(
                    ErrorCode.INTERNAL_ERROR, "Failed to initialize MCP client");
        }

        result.setTools(mcpClientWrapper.listTools().block());
        return result;
    }

    @Override
    public void bindProductNacos(String productId, BindNacosParam param) {
        Product product = findProduct(productId);
        if (product.getType() != ProductType.AGENT_SKILL
                && product.getType() != ProductType.WORKER) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Product type is not supported");
        }

        nacosService.getNacosInstance(param.getNacosId());

        ProductFeature feature =
                Optional.ofNullable(product.getFeature()).orElse(ProductFeature.builder().build());

        if (product.getType() == ProductType.AGENT_SKILL) {
            feature.setSkillConfig(
                    SkillConfig.builder()
                            .nacosId(param.getNacosId())
                            .namespace(param.getNamespace())
                            .build());
        } else {
            feature.setWorkerConfig(
                    WorkerConfig.builder()
                            .nacosId(param.getNacosId())
                            .namespace(param.getNamespace())
                            .build());
        }

        product.setFeature(feature);
        productRepository.save(product);
    }

    private void syncConfig(Product product, ProductRef productRef) {
        SourceType sourceType = productRef.getSourceType();

        if (sourceType.isGateway()) {
            GatewayResult gateway = gatewayService.getGateway(productRef.getGatewayId());

            // Determine specific configuration type
            Object config;
            if (gateway.getGatewayType().isHigress()) {
                config = productRef.getHigressRefConfig();
            } else if (gateway.getGatewayType().isAdpAIGateway()) {
                config = productRef.getAdpAIGatewayRefConfig();
            } else if (gateway.getGatewayType().isApsaraGateway()) {
                config = productRef.getApsaraGatewayRefConfig();
            } else {
                config = productRef.getApigRefConfig();
            }

            // Handle different configurations based on product type
            switch (product.getType()) {
                case REST_API:
                    productRef.setApiConfig(
                            gatewayService.fetchAPIConfig(gateway.getGatewayId(), config));
                    break;
                case MCP_SERVER:
                    // MCP 配置直接写入 McpServerMeta.connectionConfig，不再写 ProductRef.mcpConfig
                    syncMcpConfigToMeta(
                            product.getProductId(),
                            gatewayService.fetchMcpConfig(gateway.getGatewayId(), config),
                            "GATEWAY");
                    break;
                case AGENT_API:
                    productRef.setAgentConfig(
                            gatewayService.fetchAgentConfig(gateway.getGatewayId(), config));
                    break;
                case MODEL_API:
                    productRef.setModelConfig(
                            gatewayService.fetchModelConfig(gateway.getGatewayId(), config));
                    break;
            }
        } else if (sourceType.isNacos()) {
            // Handle Nacos configuration
            NacosRefConfig nacosRefConfig = productRef.getNacosRefConfig();
            if (nacosRefConfig == null) {
                throw new BusinessException(
                        ErrorCode.INVALID_REQUEST, "Nacos reference config is required");
            }

            switch (product.getType()) {
                case MCP_SERVER:
                    // MCP 配置直接写入 McpServerMeta.connectionConfig，不再写 ProductRef.mcpConfig
                    String mcpConfig =
                            nacosService.fetchMcpConfig(productRef.getNacosId(), nacosRefConfig);
                    syncMcpConfigToMeta(product.getProductId(), mcpConfig, "NACOS");
                    break;

                case AGENT_API:
                    // Agent 配置同步
                    String agentConfig =
                            nacosService.fetchAgentConfig(productRef.getNacosId(), nacosRefConfig);
                    productRef.setAgentConfig(agentConfig);
                    break;

                default:
                    throw new BusinessException(
                            ErrorCode.INVALID_REQUEST,
                            "Nacos source does not support product type: " + product.getType());
            }
        }
    }

    /**
     * 将从网关/Nacos 拉取的 MCP 配置直接同步到 McpServerMeta。
     * McpServerMeta.connectionConfig 是 MCP 技术配置的单一数据源。
     * 如果 meta 不存在（首次绑定），自动创建。
     */
    private void syncMcpConfigToMeta(String productId, String mcpConfigStr, String sourceLabel) {
        if (StrUtil.isBlank(mcpConfigStr)) {
            return;
        }

        // 解析 mcpServerName
        String mcpName = null;
        String protocol = "sse";
        String tools = null;
        try {
            cn.hutool.json.JSONObject mcpJson = JSONUtil.parseObj(mcpConfigStr);
            mcpName = mcpJson.getStr("mcpServerName");
            String p = mcpJson.getByPath("meta.protocol", String.class);
            if (StrUtil.isNotBlank(p)) {
                protocol = McpProtocolUtils.normalize(p);
            }
            tools = mcpJson.getStr("tools");
        } catch (Exception e) {
            log.warn("解析 MCP 配置失败: {}", e.getMessage());
        }

        if (StrUtil.isBlank(mcpName)) {
            mcpName = productId; // fallback
        }

        // 查找或创建 meta
        McpServerMeta meta =
                mcpServerMetaRepository.findByProductIdAndMcpName(productId, mcpName).orElse(null);

        if (meta == null) {
            // 首次绑定，创建 meta
            meta =
                    McpServerMeta.builder()
                            .mcpServerId(IdGenerator.genMcpServerId())
                            .productId(productId)
                            .mcpName(mcpName)
                            .origin(sourceLabel)
                            .protocolType(protocol)
                            .connectionConfig(mcpConfigStr)
                            .toolsConfig(McpToolsConfigParser.normalize(tools))
                            .build();
        } else {
            meta.setOrigin(sourceLabel);
            meta.setProtocolType(protocol);
            meta.setConnectionConfig(mcpConfigStr);
            if (StrUtil.isNotBlank(tools)) {
                meta.setToolsConfig(McpToolsConfigParser.normalize(tools));
            }
        }
        mcpServerMetaRepository.save(meta);
    }

    private void syncMcpTools(Product product, ProductRef productRef) {
        if (product.getType() != ProductType.MCP_SERVER
                || !productRef.getSourceType().isGateway()) {
            return;
        }

        // 从 McpServerMeta 读取配置（单一数据源）
        List<McpServerMeta> metas = mcpServerMetaRepository.findByProductId(product.getProductId());
        if (metas.isEmpty()) {
            return;
        }
        McpServerMeta meta = metas.get(0);

        MCPConfigResult mcpConfig =
                Optional.ofNullable(meta.getConnectionConfig())
                        .filter(StrUtil::isNotBlank)
                        .map(config -> JSONUtil.toBean(config, MCPConfigResult.class))
                        .orElse(null);

        if (mcpConfig == null) {
            return;
        }

        Optional.ofNullable(
                        gatewayService.fetchMcpToolsForConfig(productRef.getGatewayId(), mcpConfig))
                .filter(StrUtil::isNotBlank)
                .ifPresent(
                        tools -> {
                            meta.setToolsConfig(McpToolsConfigParser.normalize(tools));
                            mcpServerMetaRepository.save(meta);
                        });
    }

    /**
     * Fill product details, including product categories and product reference config.
     *
     * @param products the list of products to fill
     */
    private void fillProducts(List<ProductResult> products) {
        fillProducts(products, null);
    }

    private void fillProducts(
            List<ProductResult> products, Map<String, ProductRef> preloadedProductRefMap) {
        if (CollUtil.isEmpty(products)) {
            return;
        }

        List<String> productIds =
                products.stream().map(ProductResult::getProductId).collect(Collectors.toList());

        Map<String, ProductRef> productRefMap =
                preloadedProductRefMap != null
                        ? preloadedProductRefMap
                        : productRefRepository.findByProductIdIn(productIds).stream()
                                .collect(Collectors.toMap(ProductRef::getProductId, ref -> ref));

        Map<String, List<ProductCategoryResult>> categoriesMap =
                productCategoryService.listCategoriesForProducts(productIds);

        // Batch-load MCP meta and endpoint for MCP_SERVER products to avoid N+1 queries
        List<String> mcpProductIds =
                products.stream()
                        .filter(p -> p.getType() == ProductType.MCP_SERVER)
                        .map(ProductResult::getProductId)
                        .collect(Collectors.toList());

        Map<String, McpServerMeta> mcpMetaMap = Collections.emptyMap();
        Map<String, McpServerEndpoint> mcpEndpointMap = Collections.emptyMap();

        if (!mcpProductIds.isEmpty()) {
            List<McpServerMeta> allMetas = mcpServerMetaRepository.findByProductIdIn(mcpProductIds);
            mcpMetaMap = new LinkedHashMap<>();
            for (McpServerMeta m : allMetas) {
                mcpMetaMap.putIfAbsent(m.getProductId(), m);
            }

            List<String> mcpServerIds =
                    allMetas.stream()
                            .map(McpServerMeta::getMcpServerId)
                            .distinct()
                            .collect(Collectors.toList());
            if (!mcpServerIds.isEmpty()) {
                mcpEndpointMap =
                        mcpServerEndpointRepository
                                .findByMcpServerIdInAndUserIdInAndStatus(
                                        mcpServerIds,
                                        List.of(McpEndpointStatus.PUBLIC_USER_ID),
                                        McpEndpointStatus.ACTIVE.name())
                                .stream()
                                .collect(
                                        Collectors.toMap(
                                                McpServerEndpoint::getMcpServerId,
                                                ep -> ep,
                                                (a, b) -> a));
            }
        }

        for (ProductResult product : products) {
            String productId = product.getProductId();

            // Fill Categories
            product.setCategories(categoriesMap.getOrDefault(productId, Collections.emptyList()));

            // Fill ProductRef config
            ProductRef productRef = productRefMap.get(productId);
            if (productRef != null) {
                fillProductConfig(product, productRef, mcpMetaMap, mcpEndpointMap);
            }

            // Fill skill config from feature
            if (product.getFeature() != null && product.getFeature().getSkillConfig() != null) {
                product.setSkillConfig(product.getFeature().getSkillConfig());
            }

            // Fill agent spec config from feature
            if (product.getFeature() != null && product.getFeature().getWorkerConfig() != null) {
                product.setWorkerConfig(product.getFeature().getWorkerConfig());
            }
        }
    }

    /**
     * Fill product config from product reference.
     *
     * @param product    the product result to fill
     * @param productRef the product reference containing config data
     */
    private void fillProductConfig(
            ProductResult product,
            ProductRef productRef,
            Map<String, McpServerMeta> mcpMetaMap,
            Map<String, McpServerEndpoint> mcpEndpointMap) {
        product.setEnabled(productRef.getEnabled());

        // API config for REST API
        if (StrUtil.isNotBlank(productRef.getApiConfig())) {
            product.setApiConfig(JSONUtil.toBean(productRef.getApiConfig(), APIConfigResult.class));
        }

        // MCP config for MCP Server: lookup from pre-loaded maps (batch query)
        if (product.getType() == ProductType.MCP_SERVER) {
            McpServerMeta meta = mcpMetaMap.get(product.getProductId());
            McpServerEndpoint endpoint =
                    meta != null ? mcpEndpointMap.get(meta.getMcpServerId()) : null;
            product.setMcpConfig(buildMcpConfigFromPreloaded(meta, endpoint));
        } else if (StrUtil.isNotBlank(productRef.getMcpConfig())) {
            product.setMcpConfig(JSONUtil.toBean(productRef.getMcpConfig(), MCPConfigResult.class));
        }

        // Agent config for Agent API
        if (StrUtil.isNotBlank(productRef.getAgentConfig())) {
            product.setAgentConfig(
                    JSONUtil.toBean(productRef.getAgentConfig(), AgentConfigResult.class));
        }

        // Model config for Model API
        if (StrUtil.isNotBlank(productRef.getModelConfig())) {
            product.setModelConfig(
                    JSONUtil.toBean(productRef.getModelConfig(), ModelConfigResult.class));
        }
    }

    /**
     * Build MCPConfigResult from pre-loaded meta and endpoint (no DB queries).
     */
    private MCPConfigResult buildMcpConfigFromPreloaded(
            McpServerMeta meta, McpServerEndpoint endpoint) {
        if (meta == null) {
            return null;
        }
        if (endpoint != null && StrUtil.isNotBlank(endpoint.getEndpointUrl())) {
            return buildMcpConfigFromEndpoint(meta, endpoint);
        }
        if (StrUtil.isNotBlank(meta.getConnectionConfig())) {
            return buildMcpConfigFromConnectionConfig(meta);
        }
        return null;
    }

    /**
     * 从 McpServerMeta + endpoint 构建 MCPConfigResult。
     * McpServerMeta.connectionConfig 是 MCP 技术配置的单一数据源。
     */
    private MCPConfigResult buildMcpConfigFromMeta(String productId) {
        List<McpServerMeta> metas = mcpServerMetaRepository.findByProductId(productId);
        if (metas.isEmpty()) {
            return null;
        }
        McpServerMeta meta = metas.get(0);

        // 查找公共 endpoint
        McpServerEndpoint endpoint =
                mcpServerEndpointRepository
                        .findByMcpServerIdAndUserIdInAndStatus(
                                meta.getMcpServerId(),
                                List.of(McpEndpointStatus.PUBLIC_USER_ID),
                                McpEndpointStatus.ACTIVE.name())
                        .stream()
                        .findFirst()
                        .orElse(null);

        // 如果有 endpoint 热数据，构建基于 endpoint 的 MCPConfigResult
        if (endpoint != null && StrUtil.isNotBlank(endpoint.getEndpointUrl())) {
            return buildMcpConfigFromEndpoint(meta, endpoint);
        }

        // fallback: 从 meta.connectionConfig 解析
        if (StrUtil.isNotBlank(meta.getConnectionConfig())) {
            return buildMcpConfigFromConnectionConfig(meta);
        }

        return null;
    }

    /**
     * 从 endpoint 热数据构建 MCPConfigResult（用于 API 返回）。
     */
    private MCPConfigResult buildMcpConfigFromEndpoint(
            McpServerMeta meta, McpServerEndpoint endpoint) {
        MCPConfigResult result = new MCPConfigResult();
        result.setMcpServerName(meta.getMcpName());
        result.setTools(meta.getToolsConfig());

        // 构建 mcpServerConfig：用 endpoint URL 构建 domains 格式（兼容 toTransportConfig）
        String url = endpoint.getEndpointUrl();
        String protocol = StrUtil.blankToDefault(endpoint.getProtocol(), "sse");

        try {
            java.net.URI uri = java.net.URI.create(url.replaceAll("/sse$", ""));
            com.alibaba.himarket.dto.result.common.DomainResult domain =
                    com.alibaba.himarket.dto.result.common.DomainResult.builder()
                            .protocol(uri.getScheme())
                            .domain(uri.getHost())
                            .port(uri.getPort() > 0 ? uri.getPort() : null)
                            .build();

            MCPConfigResult.MCPServerConfig serverConfig = new MCPConfigResult.MCPServerConfig();
            serverConfig.setDomains(List.of(domain));
            serverConfig.setPath(uri.getPath());
            result.setMcpServerConfig(serverConfig);
        } catch (Exception e) {
            log.warn("解析 endpoint URL 失败: {}", e.getMessage());
            return null;
        }

        MCPConfigResult.McpMetadata metadata = new MCPConfigResult.McpMetadata();
        metadata.setSource(StrUtil.blankToDefault(meta.getOrigin(), "CUSTOM"));
        metadata.setProtocol(protocol);
        result.setMeta(metadata);

        return result;
    }

    /**
     * 从 meta.connectionConfig 冷数据构建 MCPConfigResult。
     */
    private MCPConfigResult buildMcpConfigFromConnectionConfig(McpServerMeta meta) {
        try {
            // connectionConfig 可能已经是 MCPConfigResult 兼容格式（网关导入时回写的）
            String connConfig = meta.getConnectionConfig();
            cn.hutool.json.JSONObject json = JSONUtil.parseObj(connConfig);

            // 如果包含 mcpServerName，说明是 MCPConfigResult 兼容格式
            if (json.containsKey("mcpServerName")) {
                MCPConfigResult result = JSONUtil.toBean(connConfig, MCPConfigResult.class);
                if (result.getTools() == null) {
                    result.setTools(meta.getToolsConfig());
                }
                return result;
            }

            // mcpServers 格式：提取 URL 构建 MCPConfigResult
            if (json.containsKey("mcpServers")) {
                cn.hutool.json.JSONObject servers = json.getJSONObject("mcpServers");
                for (String key : servers.keySet()) {
                    cn.hutool.json.JSONObject server = servers.getJSONObject(key);
                    if (server != null && StrUtil.isNotBlank(server.getStr("url"))) {
                        String url = server.getStr("url");
                        String type = server.getStr("type", "sse");

                        MCPConfigResult result = new MCPConfigResult();
                        result.setMcpServerName(meta.getMcpName());
                        result.setTools(meta.getToolsConfig());

                        java.net.URI uri = java.net.URI.create(url.replaceAll("/sse$", ""));
                        com.alibaba.himarket.dto.result.common.DomainResult domain =
                                com.alibaba.himarket.dto.result.common.DomainResult.builder()
                                        .protocol(uri.getScheme())
                                        .domain(uri.getHost())
                                        .port(uri.getPort() > 0 ? uri.getPort() : null)
                                        .build();

                        MCPConfigResult.MCPServerConfig serverConfig =
                                new MCPConfigResult.MCPServerConfig();
                        serverConfig.setDomains(List.of(domain));
                        serverConfig.setPath(uri.getPath());
                        result.setMcpServerConfig(serverConfig);

                        MCPConfigResult.McpMetadata metadata = new MCPConfigResult.McpMetadata();
                        metadata.setSource(StrUtil.blankToDefault(meta.getOrigin(), "CUSTOM"));
                        metadata.setProtocol(type);
                        result.setMeta(metadata);

                        return result;
                    }
                }
            }
        } catch (Exception e) {
            log.warn("从 connectionConfig 构建 MCPConfigResult 失败: {}", e.getMessage());
        }
        return null;
    }

    private Product findPublishedProduct(String portalId, String productId) {
        ProductPublication publication =
                publicationRepository
                        .findByPortalIdAndProductId(portalId, productId)
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                ErrorCode.NOT_FOUND, Resources.PRODUCT, productId));

        return findProduct(publication.getProductId());
    }

    private Specification<Product> buildSpecification(QueryProductParam param) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (StrUtil.isNotBlank(param.getPortalId())) {
                Subquery<String> subquery = query.subquery(String.class);
                Root<ProductPublication> publicationRoot = subquery.from(ProductPublication.class);
                subquery.select(publicationRoot.get("productId"))
                        .where(cb.equal(publicationRoot.get("portalId"), param.getPortalId()));
                predicates.add(root.get("productId").in(subquery));
            }

            if (param.getType() != null) {
                predicates.add(cb.equal(root.get("type"), param.getType()));
            }

            if (param.getStatus() != null) {
                predicates.add(cb.equal(root.get("status"), param.getStatus()));
            }

            if (StrUtil.isNotBlank(param.getName())) {
                String likePattern = "%" + param.getName() + "%";
                predicates.add(cb.like(root.get("name"), likePattern));
            }

            if (CollUtil.isNotEmpty(param.getCategoryIds())) {
                Subquery<String> subquery = query.subquery(String.class);
                Root<ProductCategoryRelation> relationRoot =
                        subquery.from(ProductCategoryRelation.class);
                subquery.select(relationRoot.get("productId"))
                        .where(relationRoot.get("categoryId").in(param.getCategoryIds()));
                predicates.add(root.get("productId").in(subquery));
            }

            if (StrUtil.isNotBlank(param.getExcludeCategoryId())) {
                Subquery<String> subquery = query.subquery(String.class);
                Root<ProductCategoryRelation> relationRoot =
                        subquery.from(ProductCategoryRelation.class);
                subquery.select(relationRoot.get("productId"))
                        .where(
                                cb.equal(
                                        relationRoot.get("categoryId"),
                                        param.getExcludeCategoryId()));
                predicates.add(cb.not(root.get("productId").in(subquery)));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private Specification<ProductSubscription> buildProductSubscriptionSpec(
            String productId, QueryProductSubscriptionParam param) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("productId"), productId));

            // If developer, can only view own consumer subscriptions
            if (contextHolder.isDeveloper()) {
                Subquery<String> consumerSubquery = query.subquery(String.class);
                Root<Consumer> consumerRoot = consumerSubquery.from(Consumer.class);
                consumerSubquery
                        .select(consumerRoot.get("consumerId"))
                        .where(cb.equal(consumerRoot.get("developerId"), contextHolder.getUser()));

                predicates.add(root.get("consumerId").in(consumerSubquery));
            }

            if (param.getStatus() != null) {
                predicates.add(cb.equal(root.get("status"), param.getStatus()));
            }

            if (StrUtil.isNotBlank(param.getConsumerName())) {
                Subquery<String> consumerSubquery = query.subquery(String.class);
                Root<Consumer> consumerRoot = consumerSubquery.from(Consumer.class);

                consumerSubquery
                        .select(consumerRoot.get("consumerId"))
                        .where(
                                cb.like(
                                        cb.lower(consumerRoot.get("name")),
                                        "%" + param.getConsumerName().toLowerCase() + "%"));

                predicates.add(root.get("consumerId").in(consumerSubquery));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    @EventListener
    @Async("taskExecutor")
    public void onProductConfigReload(ProductConfigReloadEvent event) {
        String productId = event.getProductId();

        try {
            // Double-check cache to prevent concurrent duplicate syncs
            if (productSyncCache.getIfPresent(productId) == null) {
                return;
            }

            ProductRef productRef =
                    productRefRepository.findFirstByProductId(productId).orElse(null);
            if (productRef == null) {
                return;
            }

            Product product = productRepository.findByProductId(productId).orElse(null);
            if (product == null) {
                return;
            }

            syncConfig(product, productRef);
            syncMcpTools(product, productRef);

            productRefRepository.save(productRef);

            log.info("Auto-sync product ref: {} successfully completed", productId);
        } catch (Exception e) {
            log.warn("Failed to auto-sync product ref: {}", productId, e);
        }
    }

    /**
     * List skill/worker products sorted by download count (descending).
     * Uses in-memory sorting since downloadCount is stored inside the feature JSON column.
     */
    private PageResult<ProductResult> listProductsSortedByDownloadCount(
            QueryProductParam param, Pageable pageable) {
        List<Product> allProducts = productRepository.findAll(buildSpecification(param));

        if (CollUtil.isEmpty(allProducts)) {
            return PageResult.empty(pageable.getPageNumber(), pageable.getPageSize());
        }

        // Sort by download count descending (null treated as 0)
        allProducts.sort(
                Comparator.comparingLong((Product p) -> getDownloadCount(p, param.getType()))
                        .reversed());

        // Manual pagination
        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), allProducts.size());
        List<Product> pageContent =
                start < allProducts.size()
                        ? allProducts.subList(start, end)
                        : Collections.emptyList();

        List<ProductResult> results =
                pageContent.stream()
                        .map(product -> new ProductResult().convertFrom(product))
                        .collect(Collectors.toList());

        fillProducts(results);

        return PageResult.of(
                results, pageable.getPageNumber() + 1, pageable.getPageSize(), allProducts.size());
    }

    private long getDownloadCount(Product product, ProductType type) {
        ProductFeature feature = product.getFeature();
        if (feature == null) {
            return 0L;
        }
        if (type == ProductType.AGENT_SKILL) {
            SkillConfig cfg = feature.getSkillConfig();
            return cfg != null && cfg.getDownloadCount() != null ? cfg.getDownloadCount() : 0L;
        }
        if (type == ProductType.WORKER) {
            WorkerConfig cfg = feature.getWorkerConfig();
            return cfg != null ? cfg.getDownloadCount() : 0L;
        }
        return 0L;
    }

    /**
     * List products with type-specific filter.
     * Filter is used to match specific properties in Product Config (e.g., ModelAPIConfig, APIConfig).
     *
     * @param param    query parameters including product type and filter
     * @param pageable pagination settings
     * @return paginated product results
     */
    private PageResult<ProductResult> listProductsWithFilter(
            QueryProductParam param, Pageable pageable) {
        List<Product> allProducts = productRepository.findAllByType(param.getType());

        if (CollUtil.isEmpty(allProducts)) {
            return PageResult.empty(pageable.getPageNumber(), pageable.getPageSize());
        }

        Map<String, ProductRef> productRefMap =
                productRefRepository
                        .findByProductIdIn(
                                allProducts.stream()
                                        .map(Product::getProductId)
                                        .collect(Collectors.toList()))
                        .stream()
                        .collect(Collectors.toMap(ProductRef::getProductId, ref -> ref));

        List<Product> targetProducts =
                allProducts.stream()
                        .filter(p -> matchesFilter(productRefMap.get(p.getProductId()), param))
                        // Filter by Product fields (status, name)
                        .filter(
                                p ->
                                        param.getStatus() == null
                                                || p.getStatus().equals(param.getStatus()))
                        .filter(
                                p ->
                                        StrUtil.isBlank(param.getName())
                                                || Optional.ofNullable(p.getName())
                                                        .orElse("")
                                                        .contains(param.getName()))
                        .collect(Collectors.toList());

        // Manual pagination
        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), targetProducts.size());
        List<Product> pageContent =
                start < targetProducts.size()
                        ? targetProducts.subList(start, end)
                        : Collections.emptyList();

        List<ProductResult> results =
                pageContent.stream()
                        .map(product -> new ProductResult().convertFrom(product))
                        .collect(Collectors.toList());

        // Reuse the productRefMap already queried above for filtering
        Map<String, ProductRef> pageProductRefMap = new HashMap<>();
        for (ProductResult r : results) {
            ProductRef ref = productRefMap.get(r.getProductId());
            if (ref != null) {
                pageProductRefMap.put(r.getProductId(), ref);
            }
        }
        fillProducts(results, pageProductRefMap);

        return PageResult.of(
                results,
                pageable.getPageNumber() + 1,
                pageable.getPageSize(),
                targetProducts.size());
    }

    /**
     * Check if product matches the type-specific filter
     *
     * @param productRef the product reference containing config data
     * @param param      query parameters containing filter criteria
     * @return true if product matches the filter, false otherwise
     */
    private boolean matchesFilter(ProductRef productRef, QueryProductParam param) {
        if (productRef == null || StrUtil.isBlank(productRef.getModelConfig())) {
            return false;
        }

        // MODEL_API type: use ModelFilter
        if (param.getType() == ProductType.MODEL_API && param.getModelFilter() != null) {
            try {
                ModelConfigResult config =
                        JSONUtil.toBean(productRef.getModelConfig(), ModelConfigResult.class);
                return param.getModelFilter().matches(config);
            } catch (Exception e) {
                log.warn(
                        "Failed to parse modelConfig for product: {}",
                        productRef.getProductId(),
                        e);
                return false;
            }
        }

        // TODO: Add other filter types here
        // if (param.getType() == ProductType.AGENT && param.getAgentFilter() != null) {
        //     ...
        // }

        // No filter specified or no matching filter type, pass through
        return true;
    }

    @Override
    public ImportProductsResult importProducts(ImportProductsParam param) {
        // 1. Validate parameters
        validateImportParam(param);

        // 2. Initialize result
        ImportProductsResult result = new ImportProductsResult();
        result.setTotalCount(param.getServices().size());
        result.setSuccessCount(0);
        result.setFailureCount(0);
        List<ProductImportResult> results = new ArrayList<>();

        // 3. Get gateway instance if source is gateway (for buildCreateProductRefParam)
        GatewayResult gateway = null;
        if (param.getSourceType().isGateway()) {
            gateway = gatewayService.getGateway(param.getGatewayId());
        }

        // 4. Batch query existing products to avoid N+1 query problem
        Set<String> serviceNames =
                param.getServices().stream()
                        .map(ServiceIdentifier::getName)
                        .collect(Collectors.toSet());
        List<Product> existingProducts =
                productRepository.findByNameInAndAdminId(serviceNames, contextHolder.getUser());
        Set<String> existingNames =
                existingProducts.stream().map(Product::getName).collect(Collectors.toSet());

        // 5. Import each service
        for (ServiceIdentifier service : param.getServices()) {
            ProductImportResult itemResult = new ProductImportResult();
            itemResult.setServiceName(service.getName());

            try {
                // 5.1 Build CreateProductParam
                CreateProductParam createParam = buildCreateProductParam(service, param);

                // 5.2 Check for name conflict (using pre-loaded data)
                if (existingNames.contains(createParam.getName())) {
                    itemResult.setSuccess(false);
                    itemResult.setErrorCode("NAME_CONFLICT");
                    itemResult.setErrorMessage(
                            StrUtil.format(
                                    "Product with name '{}' already exists",
                                    createParam.getName()));
                    result.setFailureCount(result.getFailureCount() + 1);
                    results.add(itemResult);
                    continue;
                }

                // 5.3 Create product (reuse existing logic)
                ProductResult product = createProduct(createParam);

                // 5.4 Build CreateProductRefParam
                CreateProductRefParam refParam =
                        buildCreateProductRefParam(service, param, gateway);

                // 5.5 Link API (reuse existing logic, auto syncConfig)
                addProductRef(product.getProductId(), refParam);

                // 5.6 Record success
                itemResult.setSuccess(true);
                itemResult.setProductId(product.getProductId());
                result.setSuccessCount(result.getSuccessCount() + 1);

            } catch (Exception e) {
                // 5.7 Record failure
                log.warn("Failed to import service: {}", service.getName(), e);
                itemResult.setSuccess(false);
                itemResult.setErrorCode("IMPORT_FAILED");
                itemResult.setErrorMessage(e.getMessage());
                result.setFailureCount(result.getFailureCount() + 1);
            }

            results.add(itemResult);
        }

        result.setResults(results);
        return result;
    }

    private void validateImportParam(ImportProductsParam param) {
        if (param.getSourceType().isGateway()) {
            if (StrUtil.isBlank(param.getGatewayId())) {
                throw new BusinessException(
                        ErrorCode.INVALID_REQUEST,
                        "Gateway ID is required when source type is GATEWAY");
            }
            gatewayService.getGateway(param.getGatewayId());

        } else if (param.getSourceType().isNacos()) {
            if (StrUtil.isBlank(param.getNacosId())) {
                throw new BusinessException(
                        ErrorCode.INVALID_REQUEST,
                        "Nacos ID is required when source type is NACOS");
            }
            nacosService.getNacosInstance(param.getNacosId());

            // Nacos does not support MODEL_API
            if (param.getProductType() == ProductType.MODEL_API) {
                throw new BusinessException(
                        ErrorCode.INVALID_REQUEST, "Nacos source does not support MODEL_API type");
            }
        }
    }

    private CreateProductParam buildCreateProductParam(
            ServiceIdentifier service, ImportProductsParam param) {
        CreateProductParam createParam = new CreateProductParam();
        createParam.setName(service.getName());
        createParam.setDescription(service.getDescription());
        createParam.setType(param.getProductType());

        if (CollUtil.isNotEmpty(param.getCategories())) {
            createParam.setCategories(param.getCategories());
        }

        // For MODEL_API, set feature with model name
        if (param.getProductType() == ProductType.MODEL_API) {
            ProductFeature feature = new ProductFeature();
            ModelFeature modelFeature = ModelFeature.builder().model(service.getName()).build();
            feature.setModelFeature(modelFeature);
            createParam.setFeature(feature);
        }

        return createParam;
    }

    private CreateProductRefParam buildCreateProductRefParam(
            ServiceIdentifier service, ImportProductsParam param, GatewayResult gateway) {
        CreateProductRefParam refParam = new CreateProductRefParam();
        refParam.setSourceType(param.getSourceType());

        if (param.getSourceType().isGateway()) {
            refParam.setGatewayId(param.getGatewayId());

            // Configure based on gateway type
            if (gateway.getGatewayType().isHigress()) {
                // Higress uses HigressRefConfig
                HigressRefConfig config = new HigressRefConfig();
                switch (param.getProductType()) {
                    case MCP_SERVER:
                        config.setMcpServerName(service.getName());
                        break;
                    case MODEL_API:
                        config.setModelRouteName(service.getName());
                        break;
                    case AGENT_API:
                        // Higress Agent API may need additional configuration
                        config.setRouteName(service.getName());
                        break;
                    default:
                        break;
                }
                refParam.setHigressRefConfig(config);
            } else {
                // AIGW/APIG/Apsara use APIGRefConfig
                APIGRefConfig config = new APIGRefConfig();
                switch (param.getProductType()) {
                    case MCP_SERVER:
                        config.setApiId(service.getApiId());
                        config.setMcpServerId(service.getMcpServerId());
                        config.setMcpRouteId(service.getMcpRouteId());
                        config.setMcpServerName(service.getName());
                        break;
                    case AGENT_API:
                        config.setAgentApiId(service.getAgentApiId());
                        config.setAgentApiName(service.getName());
                        break;
                    case MODEL_API:
                        config.setModelApiId(service.getModelApiId());
                        config.setModelApiName(service.getName());
                        break;
                    default:
                        break;
                }

                // Set config to appropriate field based on gateway type
                if (gateway.getGatewayType().isAdpAIGateway()) {
                    refParam.setAdpAIGatewayRefConfig(config);
                } else if (gateway.getGatewayType().isApsaraGateway()) {
                    refParam.setApsaraGatewayRefConfig(config);
                } else {
                    refParam.setApigRefConfig(config);
                }
            }
        } else if (param.getSourceType().isNacos()) {
            // Nacos source
            refParam.setNacosId(param.getNacosId());

            NacosRefConfig nacosConfig = new NacosRefConfig();
            // Use service-level namespaceId if specified, otherwise use param-level
            String namespaceId =
                    StrUtil.isNotBlank(service.getNamespaceId())
                            ? service.getNamespaceId()
                            : param.getNamespaceId();
            nacosConfig.setNamespaceId(namespaceId);

            switch (param.getProductType()) {
                case MCP_SERVER:
                    nacosConfig.setMcpServerName(
                            StrUtil.isNotBlank(service.getMcpServerName())
                                    ? service.getMcpServerName()
                                    : service.getName());
                    break;
                case AGENT_API:
                    nacosConfig.setAgentName(
                            StrUtil.isNotBlank(service.getAgentName())
                                    ? service.getAgentName()
                                    : service.getName());
                    break;
                default:
                    break;
            }
            refParam.setNacosRefConfig(nacosConfig);
        }

        return refParam;
    }
}
