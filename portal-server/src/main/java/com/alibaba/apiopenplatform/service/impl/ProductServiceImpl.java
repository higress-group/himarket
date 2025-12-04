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

package com.alibaba.apiopenplatform.service.impl;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import com.alibaba.apiopenplatform.dto.result.consumer.CredentialContext;
import com.alibaba.apiopenplatform.dto.result.mcp.McpToolListResult;
import com.alibaba.apiopenplatform.support.chat.mcp.McpServerConfig;
import com.alibaba.apiopenplatform.support.product.ProductFeature;
import io.modelcontextprotocol.spec.McpSchema;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import jakarta.transaction.Transactional;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.alibaba.apiopenplatform.core.constant.Resources;
import com.alibaba.apiopenplatform.core.event.PortalDeletingEvent;
import com.alibaba.apiopenplatform.core.event.ProductDeletingEvent;
import com.alibaba.apiopenplatform.core.exception.BusinessException;
import com.alibaba.apiopenplatform.core.exception.ErrorCode;
import com.alibaba.apiopenplatform.core.security.ContextHolder;
import com.alibaba.apiopenplatform.core.utils.IdGenerator;
import com.alibaba.apiopenplatform.dto.params.product.*;
import com.alibaba.apiopenplatform.dto.result.agent.AgentConfigResult;
import com.alibaba.apiopenplatform.dto.result.httpapi.APIConfigResult;
import com.alibaba.apiopenplatform.dto.result.common.PageResult;
import com.alibaba.apiopenplatform.dto.result.gateway.GatewayResult;
import com.alibaba.apiopenplatform.dto.result.mcp.MCPConfigResult;
import com.alibaba.apiopenplatform.dto.result.model.ModelConfigResult;
import com.alibaba.apiopenplatform.dto.result.portal.PortalResult;
import com.alibaba.apiopenplatform.dto.result.product.ProductPublicationResult;
import com.alibaba.apiopenplatform.dto.result.product.ProductRefResult;
import com.alibaba.apiopenplatform.dto.result.product.ProductResult;
import com.alibaba.apiopenplatform.dto.result.product.SubscriptionResult;
import com.alibaba.apiopenplatform.entity.*;
import com.alibaba.apiopenplatform.repository.*;
import com.alibaba.apiopenplatform.service.*;
import com.alibaba.apiopenplatform.support.enums.ProductStatus;
import com.alibaba.apiopenplatform.support.enums.ProductType;
import com.alibaba.apiopenplatform.support.enums.SourceType;
import com.alibaba.apiopenplatform.support.product.NacosRefConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.apache.commons.lang3.BooleanUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEventPublisher;
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

    private final ApplicationContext ctx;

    private final ContextHolder contextHolder;

    private final PortalService portalService;

    private final GatewayService gatewayService;

    private final ProductRepository productRepository;

    private final ProductRefRepository productRefRepository;

    private final ProductPublicationRepository publicationRepository;

    private final SubscriptionRepository subscriptionRepository;

    private final ConsumerRepository consumerRepository;

    private final NacosService nacosService;

    private final ApplicationEventPublisher eventPublisher;

    private final ProductCategoryService productCategoryService;

    @Override
    public ProductResult createProduct(CreateProductParam param) {
        productRepository.findByNameAndAdminId(param.getName(), contextHolder.getUser())
                .ifPresent(product -> {
                    throw new BusinessException(ErrorCode.CONFLICT, StrUtil.format("Product with name '{}' already exists", product.getName()));
                });

        String productId = IdGenerator.genApiProductId();

        Product product = param.convertTo();
        product.setProductId(productId);
        product.setAdminId(contextHolder.getUser());

        productRepository.save(product);

        // Set product categories
        setProductCategories(productId, param.getCategories());

        return getProduct(productId);
    }

    @Override
    public ProductResult getProduct(String productId) {
        Product product = contextHolder.isAdministrator() ?
                findProduct(productId) :
                findPublishedProduct(contextHolder.getPortal(), productId);

        ProductResult result = new ProductResult().convertFrom(product);

        // Fill product information
        fillProduct(result);
        return result;
    }

    @Override
    public PageResult<ProductResult> listProducts(QueryProductParam param, Pageable pageable) {
        if (contextHolder.isDeveloper()) {
            param.setPortalId(contextHolder.getPortal());
        }

        Page<Product> products = productRepository.findAll(buildSpecification(param), pageable);
        return new PageResult<ProductResult>().convertFrom(
                products, product -> {
                    ProductResult result = new ProductResult().convertFrom(product);
                    fillProduct(result);
                    fillProductSubscribeInfo(result, param);
                    return result;
                });
    }

    @Override
    public ProductResult updateProduct(String productId, UpdateProductParam param) {
        Product product = findProduct(productId);
        // Change API product type
        if (param.getType() != null && product.getType() != param.getType()) {
            productRefRepository.findFirstByProductId(productId)
                    .ifPresent(productRef -> {
                        throw new BusinessException(ErrorCode.INVALID_REQUEST, "API product already linked to API");
                    });
            // Clear product features
            product.setFeature(null);
            param.setFeature(null);
        }
        param.update(product);

        productRepository.saveAndFlush(product);

        // Set product categories
        setProductCategories(product.getProductId(), param.getCategories());

        return getProduct(product.getProductId());
    }

    @Override
    public void publishProduct(String productId, String portalId) {
        portalService.existsPortal(portalId);
        if (publicationRepository.findByPortalIdAndProductId(portalId, productId).isPresent()) {
            return;
        }

        Product product = findProduct(productId);
        product.setStatus(ProductStatus.PUBLISHED);

        // Cannot publish if not linked
        if (getProductRef(productId) == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "API product not linked to API");
        }

        ProductPublication productPublication = new ProductPublication();
        productPublication.setPortalId(portalId);
        productPublication.setProductId(productId);

        publicationRepository.save(productPublication);
        productRepository.save(product);
    }

    @Override
    public PageResult<ProductPublicationResult> getPublications(String productId, Pageable pageable) {
        Page<ProductPublication> publications = publicationRepository.findByProductId(productId, pageable);

        return new PageResult<ProductPublicationResult>().convertFrom(
                publications, publication -> {
                    ProductPublicationResult publicationResult = new ProductPublicationResult().convertFrom(publication);
                    PortalResult portal;
                    try {
                        portal = portalService.getPortal(publication.getPortalId());
                    } catch (Exception e) {
                        log.error("Failed to get portal: {}", publication.getPortalId(), e);
                        return null;
                    }

                    publicationResult.setPortalName(portal.getName());
                    publicationResult.setAutoApproveSubscriptions(portal.getPortalSettingConfig().getAutoApproveSubscriptions());

                    return publicationResult;
                });
    }

    @Override
    public void unpublishProduct(String productId, String portalId) {
        portalService.existsPortal(portalId);
        Product product = findProduct(productId);

        /*
         * Update product status:
         * If the product is published in other portals -> set to PUBLISHED
         * If not published anywhere else -> set to READY
         */
        ProductStatus toStatus = publicationRepository.existsByProductIdAndPortalIdNot(productId, portalId) ?
                ProductStatus.PUBLISHED : ProductStatus.READY;
        product.setStatus(toStatus);

        publicationRepository.findByPortalIdAndProductId(portalId, productId)
                .ifPresent(publicationRepository::delete);
        productRepository.save(product);
    }

    @Override
    public void deleteProduct(String productId) {
        Product product = findProduct(productId);

        // Delete after unpublishing
        publicationRepository.deleteByProductId(productId);

        // Clear product category relationships
        clearProductCategoryRelations(productId);

        productRepository.delete(product);
        productRefRepository.deleteByProductId(productId);

        // Asynchronously clean up product resources
        eventPublisher.publishEvent(new ProductDeletingEvent(productId));
    }

    private Product findProduct(String productId) {
        return productRepository.findByProductId(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, Resources.PRODUCT, productId));
    }

    @Override
    public void addProductRef(String productId, CreateProductRefParam param) {
        Product product = findProduct(productId);

        // Check if API reference already exists
        productRefRepository.findByProductId(product.getProductId())
                .ifPresent(productRef -> {
                    throw new BusinessException(ErrorCode.CONFLICT, "Product is already linked to an API");
                });
        ProductRef productRef = param.convertTo();
        productRef.setProductId(productId);
        syncConfig(product, productRef);

        // Update status
        product.setStatus(ProductStatus.READY);
        productRef.setEnabled(true);

        productRepository.save(product);
        productRefRepository.save(productRef);
    }

    @Override
    public ProductRefResult getProductRef(String productId) {
        return productRefRepository.findFirstByProductId(productId)
                .map(productRef -> new ProductRefResult().convertFrom(productRef))
                .orElse(null);
    }

    @Override
    public void deleteProductRef(String productId) {
        Product product = findProduct(productId);
        product.setStatus(ProductStatus.PENDING);

        ProductRef productRef = productRefRepository.findFirstByProductId(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REQUEST, "API product not linked to API"));

        // Published products cannot be unbound
        if (publicationRepository.existsByProductId(productId)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "API product already published");
        }

        productRefRepository.delete(productRef);
        productRepository.save(product);
    }

    @EventListener
    @Async("taskExecutor")
    @Override
    public void handlePortalDeletion(PortalDeletingEvent event) {
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
        return products.stream()
                .collect(Collectors.toMap(Product::getProductId, product -> new ProductResult().convertFrom(product)));
    }

    @Override
    public String getProductDashboard(String productId) {
        // Get product associated gateway information
        ProductRef productRef = productRefRepository.findFirstByProductId(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, Resources.PRODUCT, productId));

        if (productRef.getGatewayId() == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Product not linked to gateway service");
        }
        // Select dashboard type based on product type
        Product product = findProduct(productId);
        String dashboardType;
        if (product.getType() == ProductType.MCP_SERVER) {
            dashboardType = "MCP";
        } else {
            // REST_API, HTTP_API use unified API dashboard
            dashboardType = "API";
        }
        // Get dashboard URL through gateway service
        return gatewayService.getDashboard(productRef.getGatewayId(), dashboardType);
    }

    @Override
    public PageResult<SubscriptionResult> listProductSubscriptions(String productId, QueryProductSubscriptionParam param, Pageable pageable) {
        existsProduct(productId);
        Page<ProductSubscription> subscriptions = subscriptionRepository.findAll(buildProductSubscriptionSpec(productId, param), pageable);

        List<String> consumerIds = subscriptions.getContent().stream()
                .map(ProductSubscription::getConsumerId)
                .collect(Collectors.toList());
        if (CollUtil.isEmpty(consumerIds)) {
            return PageResult.empty(pageable.getPageNumber(), pageable.getPageSize());
        }

        Map<String, Consumer> consumers = consumerRepository.findByConsumerIdIn(consumerIds)
                .stream()
                .collect(Collectors.toMap(Consumer::getConsumerId, consumer -> consumer));

        return new PageResult<SubscriptionResult>().convertFrom(subscriptions, s -> {
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
        productRepository.findByProductId(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, Resources.PRODUCT, productId));
    }

    @Override
    public void existsProducts(List<String> productIds) {
        List<String> existedProductIds = productRepository.findByProductIdIn(productIds)
                .stream()
                .map(Product::getProductId)
                .collect(Collectors.toList());

        List<String> notFoundProductIds = productIds.stream()
                .filter(productId -> !existedProductIds.contains(productId))
                .collect(Collectors.toList());

        if (!notFoundProductIds.isEmpty()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, Resources.PRODUCT, String.join(",", notFoundProductIds));
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
        ProductRef productRef = productRefRepository.findFirstByProductId(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REQUEST, "API product not linked to API"));

        syncConfig(product, productRef);

        productRefRepository.saveAndFlush(productRef);
    }

    @Override
    public McpToolListResult listMcpTools(String productId) {
        // check product exists and check whether it is a mcp server type
        ProductResult product = getProduct(productId);
        if (product.getType() != ProductType.MCP_SERVER) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "API product is not a mcp server");
        }
        ConsumerService consumerService = ctx.getBean(ConsumerService.class);
        String consumerId = consumerService.getPrimaryConsumer().getConsumerId();

        // check if product is subscribed by consumer
        boolean subscribed = subscriptionRepository.findByConsumerIdAndProductId(consumerId, productId).isPresent();
        if (!subscribed) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "API product is not subscribed, not allowed to list tools");
        }

        // get mcp server config, and replace domain with gateway ip
        MCPConfigResult mcpConfig = product.getMcpConfig();
        // Get gateway IPs
        ProductRefResult productRef = getProductRef(productId);
        String gatewayId = productRef.getGatewayId();
        List<String> gatewayIps = gatewayService.fetchGatewayIps(gatewayId);
        mcpConfig.convertDomainToGatewayIp(gatewayIps);

        McpServerConfig.McpServer server = mcpConfig.toStandardMcpServer()
                .getMcpServers().get(mcpConfig.getMcpServerName());

        // Get authentication info (use applicationContext to get bean to avoid circular dependency)
        CredentialContext credentialContext = consumerService.getDefaultCredential(contextHolder.getUser());

        // get mcp tools info
        McpToolListResult mcpToolListResult = new McpToolListResult();
        McpClientFactory mcpClientFactory = new McpClientFactory();

        try (McpClientHolder mcpClientHolder =
                     mcpClientFactory.initClient(server.getType(), server.getUrl(), credentialContext.getHeaders(), credentialContext.getQueryParams())) {
            if (mcpClientHolder == null) {
                log.error("initClient returned null");
                throw new BusinessException(ErrorCode.INTERNAL_ERROR, Resources.PRODUCT, productId, "initClient returned null");
            }
            List<McpSchema.Tool> tools = mcpClientHolder.listTools();
            mcpToolListResult.setTools(tools);
        } catch (IOException e) {
            log.error("mcp client close error {}", e.getMessage());
            return mcpToolListResult;
        }
        return mcpToolListResult;
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
                    productRef.setApiConfig(gatewayService.fetchAPIConfig(gateway.getGatewayId(), config));
                    break;
                case MCP_SERVER:
                    productRef.setMcpConfig(gatewayService.fetchMcpConfig(gateway.getGatewayId(), config));
                    break;
                case AGENT_API:
                    productRef.setAgentConfig(gatewayService.fetchAgentConfig(gateway.getGatewayId(), config));
                    break;
                case MODEL_API:
                    productRef.setModelConfig(gatewayService.fetchModelConfig(gateway.getGatewayId(), config));
                    break;
            }
        } else if (sourceType.isNacos()) {
            // Handle Nacos configuration
            NacosRefConfig nacosRefConfig = productRef.getNacosRefConfig();
            if (nacosRefConfig == null) {
                throw new BusinessException(ErrorCode.INVALID_REQUEST,
                        "Nacos reference config is required");
            }

            switch (product.getType()) {
                case MCP_SERVER:
                    // MCP Server 配置同步（现有逻辑）
                    String mcpConfig = nacosService.fetchMcpConfig(
                            productRef.getNacosId(),
                            nacosRefConfig
                    );
                    productRef.setMcpConfig(mcpConfig);
                    break;

                case AGENT_API:
                    // Agent 配置同步
                    String agentConfig = nacosService.fetchAgentConfig(
                            productRef.getNacosId(),
                            nacosRefConfig
                    );
                    productRef.setAgentConfig(agentConfig);
                    break;

                default:
                    throw new BusinessException(ErrorCode.INVALID_REQUEST,
                            "Nacos source does not support product type: " + product.getType());
            }
        }
    }

    private void fillProduct(ProductResult product) {
        // Fill product category information
        product.setCategories(productCategoryService.listCategoriesForProduct(product.getProductId()));

        productRefRepository.findFirstByProductId(product.getProductId())
                .ifPresent(productRef -> {
                    product.setEnabled(productRef.getEnabled());
                    if (StrUtil.isNotBlank(productRef.getApiConfig())) {
                        product.setApiConfig(JSONUtil.toBean(productRef.getApiConfig(), APIConfigResult.class));
                    }

                    // MCP Config
                    if (StrUtil.isNotBlank(productRef.getMcpConfig())) {
                        product.setMcpConfig(JSONUtil.toBean(productRef.getMcpConfig(), MCPConfigResult.class));
                    }

                    // Agent Config
                    if (StrUtil.isNotBlank(productRef.getAgentConfig())) {
                        product.setAgentConfig(JSONUtil.toBean(productRef.getAgentConfig(), AgentConfigResult.class));
                    }

                    // Model Config
                    if (StrUtil.isNotBlank(productRef.getModelConfig())) {
                        product.setModelConfig(JSONUtil.toBean(productRef.getModelConfig(), ModelConfigResult.class));
                    }
                });
    }

    private Product findPublishedProduct(String portalId, String productId) {
        ProductPublication publication = publicationRepository.findByPortalIdAndProductId(portalId, productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, Resources.PRODUCT, productId));

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
                Root<ProductCategoryRelation> relationRoot = subquery.from(ProductCategoryRelation.class);
                subquery.select(relationRoot.get("productId"))
                        .where(relationRoot.get("categoryId").in(param.getCategoryIds()));
                predicates.add(root.get("productId").in(subquery));
            }

            if (StrUtil.isNotBlank(param.getExcludeCategoryId())) {
                Subquery<String> subquery = query.subquery(String.class);
                Root<ProductCategoryRelation> relationRoot = subquery.from(ProductCategoryRelation.class);
                subquery.select(relationRoot.get("productId"))
                        .where(cb.equal(relationRoot.get("categoryId"), param.getExcludeCategoryId()));
                predicates.add(cb.not(root.get("productId").in(subquery)));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private Specification<ProductSubscription> buildProductSubscriptionSpec(String productId, QueryProductSubscriptionParam param) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("productId"), productId));

            // If developer, can only view own consumer subscriptions
            if (contextHolder.isDeveloper()) {
                Subquery<String> consumerSubquery = query.subquery(String.class);
                Root<Consumer> consumerRoot = consumerSubquery.from(Consumer.class);
                consumerSubquery.select(consumerRoot.get("consumerId"))
                        .where(cb.equal(consumerRoot.get("developerId"), contextHolder.getUser()));

                predicates.add(root.get("consumerId").in(consumerSubquery));
            }

            if (param.getStatus() != null) {
                predicates.add(cb.equal(root.get("status"), param.getStatus()));
            }

            if (StrUtil.isNotBlank(param.getConsumerName())) {
                Subquery<String> consumerSubquery = query.subquery(String.class);
                Root<Consumer> consumerRoot = consumerSubquery.from(Consumer.class);

                consumerSubquery.select(consumerRoot.get("consumerId"))
                        .where(cb.like(
                                cb.lower(consumerRoot.get("name")),
                                "%" + param.getConsumerName().toLowerCase() + "%"
                        ));

                predicates.add(root.get("consumerId").in(consumerSubquery));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private void fillProductSubscribeInfo(ProductResult product, QueryProductParam param) {
        // if null or false, then skip
        if (!BooleanUtils.isTrue(param.getQuerySubscribeStatus())) {
            return;
        }

        // get default consumer id (use applicationContext to get bean to avoid circular dependency)
        ConsumerService consumerService = ctx.getBean(ConsumerService.class);
        String consumerId = consumerService.getPrimaryConsumer().getConsumerId();

        // check if product is subscribed by consumer
        boolean exists = subscriptionRepository.findByConsumerIdAndProductId(consumerId, product.getProductId()).isPresent();
        product.setIsSubscribed(exists);
    }
}
