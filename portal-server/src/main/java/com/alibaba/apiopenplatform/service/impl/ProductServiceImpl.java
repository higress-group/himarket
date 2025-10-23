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
import com.alibaba.apiopenplatform.dto.result.*;
import com.alibaba.apiopenplatform.entity.*;
import com.alibaba.apiopenplatform.repository.*;
import com.alibaba.apiopenplatform.service.GatewayService;
import com.alibaba.apiopenplatform.service.PortalService;
import com.alibaba.apiopenplatform.service.ProductService;
import com.alibaba.apiopenplatform.service.NacosService;
import com.alibaba.apiopenplatform.support.enums.ProductStatus;
import com.alibaba.apiopenplatform.support.enums.ProductType;
import com.alibaba.apiopenplatform.support.enums.SourceType;
import com.alibaba.apiopenplatform.support.product.NacosRefConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Page;

import javax.persistence.criteria.*;
import javax.transaction.Transactional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

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

    private final ApplicationEventPublisher eventPublisher;

    @Override
    public ProductResult createProduct(CreateProductParam param) {
        productRepository.findByNameAndAdminId(param.getName(), contextHolder.getUser())
                .ifPresent(product -> {
                    throw new BusinessException(ErrorCode.CONFLICT, StrUtil.format("{}:{}已存在", Resources.PRODUCT, product.getName()));
                });

        String productId = IdGenerator.genApiProductId();

        Product product = param.convertTo();
        product.setProductId(productId);
        product.setAdminId(contextHolder.getUser());

        // 设置默认的自动审批配置，如果未指定则默认为null（使用平台级别配置）
        if (param.getAutoApprove() != null) {
            product.setAutoApprove(param.getAutoApprove());
        }

        productRepository.save(product);

        return getProduct(productId);
    }

    @Override
    public ProductResult getProduct(String productId) {
        Product product = contextHolder.isAdministrator() ?
                findProduct(productId) :
                findPublishedProduct(contextHolder.getPortal(), productId);

        ProductResult result = new ProductResult().convertFrom(product);

        // 补充Product信息
        fullFillProduct(result);
        return result;
    }

    @Override
    public PageResult<ProductResult> listProducts(QueryProductParam param, Pageable pageable) {
        log.info("zhaoh-test-listProducts-start");
        if (contextHolder.isDeveloper()) {
            param.setPortalId(contextHolder.getPortal());
        }

        Page<Product> products = productRepository.findAll(buildSpecification(param), pageable);
        return new PageResult<ProductResult>().convertFrom(
                products, product -> {
                    ProductResult result = new ProductResult().convertFrom(product);
                    fullFillProduct(result);
                    return result;
                });
    }

    @Override
    public ProductResult updateProduct(String productId, UpdateProductParam param) {
        Product product = findProduct(productId);

        // 更换API产品类型
        if (param.getType() != null && product.getType() != param.getType()) {
            productRefRepository.findFirstByProductId(productId)
                    .ifPresent(productRef -> {
                        throw new BusinessException(ErrorCode.INVALID_REQUEST, "API产品已关联API");
                    });
        }
        param.update(product);

        // Consumer鉴权配置同步至网关
        Optional.ofNullable(param.getEnableConsumerAuth()).ifPresent(product::setEnableConsumerAuth);

        // 更新自动审批配置
        Optional.ofNullable(param.getAutoApprove()).ifPresent(product::setAutoApprove);

        productRepository.saveAndFlush(product);
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

        // 未关联不允许发布
        if (getProductRef(productId) == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "API产品未关联API");
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

        publicationRepository.findByPortalIdAndProductId(portalId, productId)
                .ifPresent(publicationRepository::delete);
    }

    @Override
    public void deleteProduct(String productId) {
        Product Product = findProduct(productId);

        // 下线后删除
        publicationRepository.deleteByProductId(productId);
        productRepository.delete(Product);

        // 异步清理Product资源
        eventPublisher.publishEvent(new ProductDeletingEvent(productId));
    }

    /**
     * 查找产品，如果不存在则抛出异常
     */
    private Product findProduct(String productId) {
        return productRepository.findByProductId(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, Resources.PRODUCT, productId));
    }

    @Override
    public void addProductRef(String productId, CreateProductRefParam param) {
        Product product = findProduct(productId);

        // 是否已存在API引用
        productRefRepository.findByProductId(product.getProductId())
                .ifPresent(productRef -> {
                    throw new BusinessException(ErrorCode.CONFLICT, StrUtil.format("{}:{}已关联API", Resources.PRODUCT, productId));
                });
        ProductRef productRef = param.convertTo();
        productRef.setProductId(productId);
        syncConfig(product, productRef);

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
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REQUEST, "API产品未关联API"));

        // 已发布的产品不允许解绑
        if (publicationRepository.existsByProductId(productId)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "API产品已发布");
        }

        productRefRepository.delete(productRef);
        productRepository.save(product);
    }

    private void syncConfig(Product product, ProductRef productRef) {
        SourceType sourceType = productRef.getSourceType();

        if (sourceType.isGateway()) {
            GatewayResult gateway = gatewayService.getGateway(productRef.getGatewayId());
            // 根据网关类型选择对应的配置对象
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
            if (product.getType() == ProductType.REST_API) {
                String apiConfig = gatewayService.fetchAPIConfig(gateway.getGatewayId(), config);
                productRef.setApiConfig(apiConfig);
            } else {
                String mcpConfig = gatewayService.fetchMcpConfig(gateway.getGatewayId(), config);
                productRef.setMcpConfig(mcpConfig);
            }
        } else if (sourceType.isNacos()) {
            // 从Nacos获取MCP Server配置
            NacosRefConfig nacosRefConfig = productRef.getNacosRefConfig();
            if (nacosRefConfig != null) {
                String mcpConfig = nacosService.fetchMcpConfig(productRef.getNacosId(), nacosRefConfig);
                productRef.setMcpConfig(mcpConfig);
            }
        }
        product.setStatus(ProductStatus.READY);
        productRef.setEnabled(true);
    }

    private void fullFillProduct(ProductResult product) {
        productRefRepository.findFirstByProductId(product.getProductId())
                .ifPresent(productRef -> {
                    product.setEnabled(productRef.getEnabled());
                    if (StrUtil.isNotBlank(productRef.getApiConfig())) {
                        product.setApiConfig(JSONUtil.toBean(productRef.getApiConfig(), APIConfigResult.class));
                    }

                    // API Config
                    if (StrUtil.isNotBlank(productRef.getMcpConfig())) {
                        product.setMcpConfig(JSONUtil.toBean(productRef.getMcpConfig(), MCPConfigResult.class));
                    }
                    product.setStatus(ProductStatus.READY);
                });

        if (publicationRepository.existsByProductId(product.getProductId())) {
            product.setStatus(ProductStatus.PUBLISHED);
        }
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

            if (StrUtil.isNotBlank(param.getCategory())) {
                predicates.add(cb.equal(root.get("category"), param.getCategory()));
            }

            if (param.getStatus() != null) {
                predicates.add(cb.equal(root.get("status"), param.getStatus()));
            }

            if (StrUtil.isNotBlank(param.getName())) {
                String likePattern = "%" + param.getName() + "%";
                predicates.add(cb.like(root.get("name"), likePattern));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    @EventListener
    @Async("taskExecutor")
    @Override
    public void handlePortalDeletion(PortalDeletingEvent event) {
        String portalId = event.getPortalId();
        try {
            log.info("Starting to cleanup publications for portal {}", portalId);
            publicationRepository.deleteAllByPortalId(portalId);

            log.info("Completed cleanup publications for portal {}", portalId);
        } catch (Exception e) {
            log.error("Failed to cleanup developers for portal {}: {}", portalId, e.getMessage());
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
        // 获取产品关联的网关信息
        ProductRef productRef = productRefRepository.findFirstByProductId(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, Resources.PRODUCT, productId));

        if (productRef.getGatewayId() == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "该产品尚未关联网关服务");
        }
        // 基于产品类型选择Dashboard类型
        Product product = findProduct(productId);
        String dashboardType;
        if (product.getType() == ProductType.MCP_SERVER) {
            dashboardType = "MCP";
        } else {
            // REST_API、HTTP_API 统一走 API 面板
            dashboardType = "API";
        }
        // 通过网关服务获取Dashboard URL
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

    private Specification<ProductSubscription> buildProductSubscriptionSpec(String productId, QueryProductSubscriptionParam param) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("productId"), productId));

            // 如果是开发者，只能查看自己的Consumer订阅
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
}
