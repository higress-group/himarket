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

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;

import com.alibaba.apiopenplatform.core.constant.Resources;
import com.alibaba.apiopenplatform.core.event.DeveloperDeletingEvent;
import com.alibaba.apiopenplatform.core.event.ProductDeletingEvent;
import com.alibaba.apiopenplatform.core.exception.BusinessException;
import com.alibaba.apiopenplatform.core.exception.ErrorCode;
import com.alibaba.apiopenplatform.core.security.ContextHolder;
import com.alibaba.apiopenplatform.core.utils.IdGenerator;
import com.alibaba.apiopenplatform.dto.params.consumer.QueryConsumerParam;
import com.alibaba.apiopenplatform.dto.params.consumer.CreateConsumerParam;
import com.alibaba.apiopenplatform.dto.params.consumer.CreateCredentialParam;
import com.alibaba.apiopenplatform.dto.params.consumer.UpdateCredentialParam;
import com.alibaba.apiopenplatform.dto.result.*;
import com.alibaba.apiopenplatform.dto.params.consumer.CreateSubscriptionParam;
import com.alibaba.apiopenplatform.dto.params.consumer.QuerySubscriptionParam;
import com.alibaba.apiopenplatform.entity.*;
import com.alibaba.apiopenplatform.repository.ConsumerRepository;
import com.alibaba.apiopenplatform.repository.ConsumerCredentialRepository;
import com.alibaba.apiopenplatform.repository.SubscriptionRepository;
import com.alibaba.apiopenplatform.service.ConsumerService;
import com.alibaba.apiopenplatform.service.GatewayService;
import com.alibaba.apiopenplatform.service.PortalService;
import com.alibaba.apiopenplatform.service.ProductService;
import com.alibaba.apiopenplatform.support.consumer.ApiKeyConfig;
import com.alibaba.apiopenplatform.support.consumer.ConsumerAuthConfig;
import com.alibaba.apiopenplatform.support.consumer.HmacConfig;
import com.alibaba.apiopenplatform.support.enums.CredentialMode;
import com.alibaba.apiopenplatform.support.enums.SourceType;
import com.alibaba.apiopenplatform.support.gateway.GatewayConfig;
import cn.hutool.core.util.BooleanUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import javax.persistence.criteria.Predicate;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import javax.persistence.criteria.Root;
import javax.persistence.criteria.Subquery;
import javax.transaction.Transactional;
import java.util.*;
import java.util.stream.Collectors;

import com.alibaba.apiopenplatform.support.enums.SubscriptionStatus;
import com.alibaba.apiopenplatform.repository.ConsumerRefRepository;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class ConsumerServiceImpl implements ConsumerService {

    private final PortalService portalService;

    private final ConsumerRepository consumerRepository;

    private final GatewayService gatewayService;

    private final ContextHolder contextHolder;

    private final ConsumerCredentialRepository credentialRepository;

    private final SubscriptionRepository subscriptionRepository;

    private final ProductService productService;

    private final ConsumerRefRepository consumerRefRepository;

    @Override
    public ConsumerResult createConsumer(CreateConsumerParam param) {
        PortalResult portal = portalService.getPortal(contextHolder.getPortal());

        String consumerId = IdGenerator.genConsumerId();
        Consumer consumer = param.convertTo();
        consumer.setConsumerId(consumerId);
        consumer.setDeveloperId(contextHolder.getUser());
        consumer.setPortalId(portal.getPortalId());

        consumerRepository.save(consumer);

        // 初始化Credential
        ConsumerCredential credential = initCredential(consumerId);
        credentialRepository.save(credential);

        return getConsumer(consumerId);
    }

    @Override
    public PageResult<ConsumerResult> listConsumers(QueryConsumerParam param, Pageable pageable) {
        Page<Consumer> consumers = consumerRepository.findAll(buildConsumerSpec(param), pageable);

        return new PageResult<ConsumerResult>().convertFrom(consumers, consumer -> new ConsumerResult().convertFrom(consumer));
    }

    @Override
    public ConsumerResult getConsumer(String consumerId) {
        Consumer consumer = contextHolder.isDeveloper() ? findDevConsumer(consumerId) : findConsumer(consumerId);

        return new ConsumerResult().convertFrom(consumer);
    }

    @Override
    public void deleteConsumer(String consumerId) {
        Consumer consumer = contextHolder.isDeveloper() ? findDevConsumer(consumerId) : findConsumer(consumerId);
        
        // 1. 先解除所有产品的授权
        List<ProductSubscription> subscriptions = subscriptionRepository.findAllByConsumerId(consumerId);
        for (ProductSubscription subscription : subscriptions) {
            try {
                // 如果订阅有授权配置，需要先解除授权
                if (subscription.getConsumerAuthConfig() != null) {
                    ProductRefResult productRef = productService.getProductRef(subscription.getProductId());
                    if (productRef != null) {
                        GatewayConfig gatewayConfig = gatewayService.getGatewayConfig(productRef.getGatewayId());
                        ConsumerRef consumerRef = matchConsumerRef(consumerId, gatewayConfig);
                        if (consumerRef != null) {
                            gatewayService.revokeConsumerAuthorization(
                                productRef.getGatewayId(), 
                                consumerRef.getGwConsumerId(), 
                                subscription.getConsumerAuthConfig()
                            );
                        }
                    }
                }
            } catch (Exception e) {
                log.error("revoke consumer authorization error, consumerId: {}, productId: {}", 
                    consumerId, subscription.getProductId(), e);
            }
        }
        
        // 2. 删除订阅记录
        subscriptionRepository.deleteAllByConsumerId(consumerId);

        // 3. 删除凭证
        credentialRepository.deleteAllByConsumerId(consumerId);

        // 4. 删除网关上的Consumer
        List<ConsumerRef> consumerRefs = consumerRefRepository.findAllByConsumerId(consumerId);
        for (ConsumerRef consumerRef : consumerRefs) {
            try {
                gatewayService.deleteConsumer(consumerRef.getGwConsumerId(), consumerRef.getGatewayConfig());
            } catch (Exception e) {
                log.error("deleteConsumer gatewayConsumer error, gwConsumerId: {}", consumerRef.getGwConsumerId(), e);
            }
        }
        
        // 5. 删除ConsumerRef记录
        consumerRefRepository.deleteAll(consumerRefs);

        // 6. 最后删除Consumer本身
        consumerRepository.delete(consumer);
    }

    @Override
    public void createCredential(String consumerId, CreateCredentialParam param) {
        existsConsumer(consumerId);
        // Consumer仅一份Credential
        credentialRepository.findByConsumerId(consumerId)
                .ifPresent(c -> {
                    throw new BusinessException(ErrorCode.CONFLICT, StrUtil.format("{}:{}已存在凭证", Resources.CONSUMER, consumerId));
                });
        ConsumerCredential credential = param.convertTo();
        credential.setConsumerId(consumerId);
        complementCredentials(credential);
        credentialRepository.save(credential);
    }

    private ConsumerCredential initCredential(String consumerId) {
        ConsumerCredential credential = new ConsumerCredential();
        credential.setConsumerId(consumerId);

        ApiKeyConfig.ApiKeyCredential apiKeyCredential = new ApiKeyConfig.ApiKeyCredential();
        ApiKeyConfig apiKeyConfig = new ApiKeyConfig();
        apiKeyConfig.setCredentials(Collections.singletonList(apiKeyCredential));

        credential.setApiKeyConfig(apiKeyConfig);
        complementCredentials(credential);

        return credential;
    }

    @Override
    public ConsumerCredentialResult getCredential(String consumerId) {
        existsConsumer(consumerId);

        return credentialRepository.findByConsumerId(consumerId)
                .map(credential -> new ConsumerCredentialResult().convertFrom(credential))
                .orElse(new ConsumerCredentialResult());
    }

    @Override
    public void updateCredential(String consumerId, UpdateCredentialParam param) {
        ConsumerCredential credential = credentialRepository.findByConsumerId(consumerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, Resources.CONSUMER_CREDENTIAL, consumerId));

        param.update(credential);

        List<ConsumerRef> consumerRefs = consumerRefRepository.findAllByConsumerId(consumerId);
        for (ConsumerRef consumerRef : consumerRefs) {
            try {
                gatewayService.updateConsumer(consumerRef.getGwConsumerId(), credential, consumerRef.getGatewayConfig());
            } catch (Exception e) {
                log.error("update gatewayConsumer error, gwConsumerId: {}", consumerRef.getGwConsumerId(), e);
            }
        }

        credentialRepository.saveAndFlush(credential);
    }

    @Override
    public void deleteCredential(String consumerId) {
        existsConsumer(consumerId);
        credentialRepository.deleteAllByConsumerId(consumerId);
    }

    @Override
    public SubscriptionResult subscribeProduct(String consumerId, CreateSubscriptionParam param) {

        Consumer consumer = contextHolder.isDeveloper() ?
                findDevConsumer(consumerId) : findConsumer(consumerId);
        // 勿重复订阅
        if (subscriptionRepository.findByConsumerIdAndProductId(consumerId, param.getProductId()).isPresent()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "重复订阅");
        }

        ProductResult product = productService.getProduct(param.getProductId());
        ProductRefResult productRef = productService.getProductRef(param.getProductId());
        if (productRef == null) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "API产品未关联API");
        }

        // 非网关型不支持订阅
        if (productRef.getSourceType() != SourceType.GATEWAY) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "API产品不支持订阅");
        }

        ConsumerCredential credential = credentialRepository.findByConsumerId(consumerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, Resources.CONSUMER_CREDENTIAL, consumerId));

        ProductSubscription subscription = param.convertTo();
        subscription.setConsumerId(consumerId);

        // 检查产品级别的自动审批设置
        boolean autoApprove = false;

        // 优先检查产品级别的autoApprove配置
        if (product.getAutoApprove() != null) {
            // 如果产品配置了autoApprove，直接使用产品级别的配置
            autoApprove = product.getAutoApprove();
            log.info("使用产品级别自动审批配置: productId={}, autoApprove={}", param.getProductId(), autoApprove);
        } else {
            // 如果产品未配置autoApprove，则使用平台级别的配置
            PortalResult portal = portalService.getPortal(consumer.getPortalId());
            log.info("portal: {}", JSONUtil.toJsonStr(portal));
            autoApprove = portal.getPortalSettingConfig() != null
                    && BooleanUtil.isTrue(portal.getPortalSettingConfig().getAutoApproveSubscriptions());
            log.info("使用平台级别自动审批配置: portalId={}, autoApprove={}", consumer.getPortalId(), autoApprove);
        }

        if (autoApprove) {
            // 如果autoApprove为true，立即授权并设置为APPROVED状态
            ConsumerAuthConfig consumerAuthConfig = authorizeConsumer(consumer, credential, productRef);
            subscription.setConsumerAuthConfig(consumerAuthConfig);
            subscription.setStatus(SubscriptionStatus.APPROVED);
        } else {
            // 如果autoApprove为false，暂时不授权，设置为PENDING状态
            subscription.setStatus(SubscriptionStatus.PENDING);
        }

        subscriptionRepository.save(subscription);

        SubscriptionResult r = new SubscriptionResult().convertFrom(subscription);
        r.setProductName(product.getName());
        r.setProductType(product.getType());

        return r;
    }

    @Override
    public void unsubscribeProduct(String consumerId, String productId) {
        existsConsumer(consumerId);

        ProductSubscription subscription = subscriptionRepository
                .findByConsumerIdAndProductId(consumerId, productId)
                .orElse(null);
        if (subscription == null) {
            return;
        }

        if (subscription.getConsumerAuthConfig() != null) {
            ProductRefResult productRef = productService.getProductRef(productId);
            GatewayConfig gatewayConfig = gatewayService.getGatewayConfig(productRef.getGatewayId());

            // 取消网关上的Consumer授权
            Optional.ofNullable(matchConsumerRef(consumerId, gatewayConfig))
                    .ifPresent(consumerRef ->
                            gatewayService.revokeConsumerAuthorization(productRef.getGatewayId(), consumerRef.getGwConsumerId(), subscription.getConsumerAuthConfig())
                    );
        }

        subscriptionRepository.deleteByConsumerIdAndProductId(consumerId, productId);
    }

    @Override
    public PageResult<SubscriptionResult> listSubscriptions(String consumerId, QuerySubscriptionParam param, Pageable pageable) {
        existsConsumer(consumerId);

        Page<ProductSubscription> subscriptions = subscriptionRepository.findAll(buildCredentialSpec(consumerId, param), pageable);

        List<String> productIds = subscriptions.getContent().stream()
                .map(ProductSubscription::getProductId)
                .collect(Collectors.toList());
        Map<String, ProductResult> products = productService.getProducts(productIds);
        return new PageResult<SubscriptionResult>().convertFrom(subscriptions, s -> {
            SubscriptionResult r = new SubscriptionResult().convertFrom(s);
            ProductResult product = products.get(r.getProductId());
            if (product != null) {
                r.setProductType(product.getType());
                r.setProductName(product.getName());
            }
            return r;
        });
    }

    @Override
    public void deleteSubscription(String consumerId, String productId) {
        existsConsumer(consumerId);

        subscriptionRepository.findByConsumerIdAndProductId(consumerId, productId)
                .ifPresent(subscriptionRepository::delete);
    }

    @Override
    public SubscriptionResult approveSubscription(String consumerId, String productId) {
        existsConsumer(consumerId);

        ProductSubscription subscription = subscriptionRepository.findByConsumerIdAndProductId(consumerId, productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, Resources.SUBSCRIPTION, StrUtil.format("{}:{}", productId, consumerId)));

        // 检查订阅状态，只有PENDING状态的订阅才能被审批
        if (subscription.getStatus() != SubscriptionStatus.PENDING) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "订阅已审批");
        }

        // 获取消费者和凭证信息
        Consumer consumer = contextHolder.isDeveloper() ?
                findDevConsumer(consumerId) : findConsumer(consumerId);
        ConsumerCredential credential = credentialRepository.findByConsumerId(consumerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, Resources.CONSUMER_CREDENTIAL, consumerId));

        // 获取产品引用信息
        ProductRefResult productRef = productService.getProductRef(productId);
        if (productRef == null) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "API产品未关联API");
        }

        // 执行授权操作
        ConsumerAuthConfig consumerAuthConfig = authorizeConsumer(consumer, credential, productRef);

        // 更新订阅状态和授权配置
        subscription.setConsumerAuthConfig(consumerAuthConfig);
        subscription.setStatus(SubscriptionStatus.APPROVED);
        subscriptionRepository.saveAndFlush(subscription);

        ProductResult product = productService.getProduct(productId);
        SubscriptionResult result = new SubscriptionResult().convertFrom(subscription);
        if (product != null) {
            result.setProductName(product.getName());
            result.setProductType(product.getType());
        }
        return result;
    }

    private Consumer findConsumer(String consumerId) {
        return consumerRepository.findByConsumerId(consumerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, Resources.CONSUMER, consumerId));
    }

    private Consumer findDevConsumer(String consumerId) {
        return consumerRepository.findByDeveloperIdAndConsumerId(contextHolder.getUser(), consumerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, Resources.CONSUMER, consumerId));
    }

    private void existsConsumer(String consumerId) {
        (contextHolder.isDeveloper() ?
                consumerRepository.findByDeveloperIdAndConsumerId(contextHolder.getUser(), consumerId) :
                consumerRepository.findByConsumerId(consumerId))
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, Resources.CONSUMER, consumerId));
    }

    private Specification<Consumer> buildConsumerSpec(QueryConsumerParam param) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (contextHolder.isDeveloper()) {
                param.setDeveloperId(contextHolder.getUser());
            }

            if (StrUtil.isNotBlank(param.getDeveloperId())) {
                predicates.add(cb.equal(root.get("developerId"), param.getDeveloperId()));
            }

            if (StrUtil.isNotBlank(param.getPortalId())) {
                predicates.add(cb.equal(root.get("portalId"), param.getPortalId()));
            }

            if (StrUtil.isNotBlank(param.getName())) {
                String likePattern = "%" + param.getName() + "%";
                predicates.add(cb.like(cb.lower(root.get("name")), likePattern));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private Specification<ProductSubscription> buildCredentialSpec(String consumerId, QuerySubscriptionParam param) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("consumerId"), consumerId));
            if (param.getStatus() != null) {
                predicates.add(cb.equal(root.get("status"), param.getStatus()));
            }
            if (StrUtil.isNotBlank(param.getProductName())) {
                // 使用子查询
                Subquery<String> productSubquery = query.subquery(String.class);
                Root<Product> productRoot = productSubquery.from(Product.class);

                productSubquery.select(productRoot.get("productId"))
                        .where(cb.like(
                                cb.lower(productRoot.get("name")),
                                "%" + param.getProductName().toLowerCase() + "%"
                        ));

                predicates.add(root.get("productId").in(productSubquery));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * 补充Credentials
     *
     * @param credential
     */
    private void complementCredentials(ConsumerCredential credential) {
        if (credential == null) {
            return;
        }

        // ApiKey
        if (credential.getApiKeyConfig() != null) {
            List<ApiKeyConfig.ApiKeyCredential> apiKeyCredentials = credential.getApiKeyConfig().getCredentials();
            if (apiKeyCredentials != null) {
                for (ApiKeyConfig.ApiKeyCredential cred : apiKeyCredentials) {
                    if (cred.getMode() == CredentialMode.SYSTEM && StrUtil.isBlank(cred.getApiKey())) {
                        cred.setApiKey(IdGenerator.genIdWithPrefix("apikey-"));
                    }
                }
            }
        }

        // HMAC
        if (credential.getHmacConfig() != null) {
            List<HmacConfig.HmacCredential> hmacCredentials = credential.getHmacConfig().getCredentials();
            if (hmacCredentials != null) {
                for (HmacConfig.HmacCredential cred : hmacCredentials) {
                    if (cred.getMode() == CredentialMode.SYSTEM &&
                            (StrUtil.isBlank(cred.getAk()) || StrUtil.isBlank(cred.getSk()))) {
                        cred.setAk(IdGenerator.genIdWithPrefix("ak-"));
                        cred.setSk(IdGenerator.genIdWithPrefix("sk-"));
                    }
                }
            }
        }
    }

    private ConsumerAuthConfig authorizeConsumer(Consumer consumer, ConsumerCredential credential, ProductRefResult productRef) {
        GatewayConfig gatewayConfig = gatewayService.getGatewayConfig(productRef.getGatewayId());

        // 检查是否在网关上有对应的Consumer
        ConsumerRef existingConsumerRef = matchConsumerRef(consumer.getConsumerId(), gatewayConfig);
        String gwConsumerId;
        
        if (existingConsumerRef != null) {
            // 如果存在ConsumerRef记录，需要检查实际网关中是否还存在该消费者
            gwConsumerId = existingConsumerRef.getGwConsumerId();
            
            // 检查实际网关中是否还存在该消费者
            if (!isConsumerExistsInGateway(gwConsumerId, gatewayConfig)) {
                log.warn("网关中的消费者已被删除，需要重新创建: gwConsumerId={}, gatewayType={}", 
                    gwConsumerId, gatewayConfig.getGatewayType());
                
                // 删除过期的ConsumerRef记录
                consumerRefRepository.delete(existingConsumerRef);
                
                // 重新创建消费者
                gwConsumerId = gatewayService.createConsumer(consumer, credential, gatewayConfig);
                consumerRefRepository.save(ConsumerRef.builder()
                        .consumerId(consumer.getConsumerId())
                        .gwConsumerId(gwConsumerId)
                        .gatewayType(gatewayConfig.getGatewayType())
                        .gatewayConfig(gatewayConfig)
                        .build());
            }
        } else {
            // 如果不存在ConsumerRef记录，直接创建新的消费者
            gwConsumerId = gatewayService.createConsumer(consumer, credential, gatewayConfig);
            consumerRefRepository.save(ConsumerRef.builder()
                    .consumerId(consumer.getConsumerId())
                    .gwConsumerId(gwConsumerId)
                    .gatewayType(gatewayConfig.getGatewayType())
                    .gatewayConfig(gatewayConfig)
                    .build());
        }

        // 授权
        return gatewayService.authorizeConsumer(productRef.getGatewayId(), gwConsumerId, productRef);
    }

    /**
     * 检查消费者是否在实际网关中存在
     */
    private boolean isConsumerExistsInGateway(String gwConsumerId, GatewayConfig gatewayConfig) {
        try {
            return gatewayService.isConsumerExists(gwConsumerId, gatewayConfig);
        } catch (Exception e) {
            log.warn("检查网关消费者存在性失败: gwConsumerId={}, gatewayType={}", 
                gwConsumerId, gatewayConfig.getGatewayType(), e);
            // 如果检查失败，默认认为存在，避免无谓的重新创建
            return true;
        }
    }

    @EventListener
    @Async("taskExecutor")
    public void handleDeveloperDeletion(DeveloperDeletingEvent event) {
        String developerId = event.getDeveloperId();
        log.info("Cleaning consumers for developer {}", developerId);

        List<Consumer> consumers = consumerRepository.findAllByDeveloperId(developerId);
        consumers.forEach(consumer -> {
            try {
                deleteConsumer(consumer.getConsumerId());
            } catch (Exception e) {
                log.error("Failed to delete consumer {}", consumer.getConsumerId(), e);
            }
        });
    }

    @EventListener
    @Async("taskExecutor")
    public void handleProductDeletion(ProductDeletingEvent event) {
        String productId = event.getProductId();
        log.info("Cleaning subscriptions for product {}", productId);

        subscriptionRepository.deleteAllByProductId(productId);

        List<ProductSubscription> subscriptions = subscriptionRepository.findAllByProductId(productId);

        subscriptions.forEach(subscription -> {
            try {
                unsubscribeProduct(subscription.getConsumerId(), subscription.getProductId());
            } catch (Exception e) {
                log.error("Failed to unsubscribe product {} for consumer {}", productId, subscription.getConsumerId(), e);
            }
        });
    }

    private ConsumerRef matchConsumerRef(String consumerId, GatewayConfig gatewayConfig) {
        List<ConsumerRef> consumeRefs = consumerRefRepository.findAllByConsumerIdAndGatewayType(consumerId, gatewayConfig.getGatewayType());
        if (consumeRefs.isEmpty()) {
            return null;
        }

        for (ConsumerRef ref : consumeRefs) {
            // 网关配置相同
            if (StrUtil.equals(JSONUtil.toJsonStr(ref.getGatewayConfig()), JSONUtil.toJsonStr(gatewayConfig))) {
                return ref;
            }
        }
        return null;
    }
}
