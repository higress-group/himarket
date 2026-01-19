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

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.alibaba.himarket.core.constant.Resources;
import com.alibaba.himarket.core.event.DeveloperDeletingEvent;
import com.alibaba.himarket.core.event.ProductDeletingEvent;
import com.alibaba.himarket.core.exception.BusinessException;
import com.alibaba.himarket.core.exception.ErrorCode;
import com.alibaba.himarket.core.security.ContextHolder;
import com.alibaba.himarket.core.utils.IdGenerator;
import com.alibaba.himarket.dto.params.consumer.CreateConsumerParam;
import com.alibaba.himarket.dto.params.consumer.CreateCredentialParam;
import com.alibaba.himarket.dto.params.consumer.CreateSubscriptionParam;
import com.alibaba.himarket.dto.params.consumer.QueryConsumerParam;
import com.alibaba.himarket.dto.params.consumer.QuerySubscriptionParam;
import com.alibaba.himarket.dto.params.consumer.UpdateCredentialParam;
import com.alibaba.himarket.dto.result.common.PageResult;
import com.alibaba.himarket.dto.result.consumer.ConsumerCredentialResult;
import com.alibaba.himarket.dto.result.consumer.ConsumerResult;
import com.alibaba.himarket.dto.result.consumer.CredentialContext;
import com.alibaba.himarket.dto.result.portal.PortalResult;
import com.alibaba.himarket.dto.result.product.ProductRefResult;
import com.alibaba.himarket.dto.result.product.ProductResult;
import com.alibaba.himarket.dto.result.product.SubscriptionResult;
import com.alibaba.himarket.entity.*;
import com.alibaba.himarket.repository.ConsumerCredentialRepository;
import com.alibaba.himarket.repository.ConsumerRefRepository;
import com.alibaba.himarket.repository.ConsumerRepository;
import com.alibaba.himarket.repository.SubscriptionRepository;
import com.alibaba.himarket.service.ConsumerService;
import com.alibaba.himarket.service.GatewayService;
import com.alibaba.himarket.service.PortalService;
import com.alibaba.himarket.service.ProductService;
import com.alibaba.himarket.support.consumer.ApiKeyConfig;
import com.alibaba.himarket.support.consumer.ConsumerAuthConfig;
import com.alibaba.himarket.support.consumer.HmacConfig;
import com.alibaba.himarket.support.enums.CredentialMode;
import com.alibaba.himarket.support.enums.SourceType;
import com.alibaba.himarket.support.enums.SubscriptionStatus;
import com.alibaba.himarket.support.gateway.GatewayConfig;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import jakarta.transaction.Transactional;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

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
        // Get current user from SecurityContext
        String developerId = contextHolder.getUser();
        return doCreateConsumer(param, developerId);
    }

    @Override
    public void createConsumerInner(CreateConsumerParam param, String developerId) {
        doCreateConsumer(param, developerId);
    }

    private ConsumerResult doCreateConsumer(CreateConsumerParam param, String developerId) {
        PortalResult portal = portalService.getPortal(contextHolder.getPortal());

        String consumerId = IdGenerator.genConsumerId();
        Consumer consumer = param.convertTo();
        consumer.setConsumerId(consumerId);
        consumer.setDeveloperId(developerId);
        consumer.setPortalId(portal.getPortalId());

        consumerRepository.save(consumer);

        // Initialize credential
        ConsumerCredential credential = initCredential(consumerId);
        credentialRepository.save(credential);

        return getConsumer(consumerId);
    }

    @Override
    public PageResult<ConsumerResult> listConsumers(QueryConsumerParam param, Pageable pageable) {
        Page<Consumer> consumers = consumerRepository.findAll(buildConsumerSpec(param), pageable);

        return new PageResult<ConsumerResult>()
                .convertFrom(consumers, consumer -> new ConsumerResult().convertFrom(consumer));
    }

    @Override
    public ConsumerResult getConsumer(String consumerId) {
        Consumer consumer =
                contextHolder.isDeveloper()
                        ? findDevConsumer(consumerId)
                        : findConsumer(consumerId);

        return new ConsumerResult().convertFrom(consumer);
    }

    @Override
    public void deleteConsumer(String consumerId) {
        Consumer consumer =
                contextHolder.isDeveloper()
                        ? findDevConsumer(consumerId)
                        : findConsumer(consumerId);

        // 1. Remove subscriptions
        List<ProductSubscription> subscriptions =
                subscriptionRepository.findAllByConsumerId(consumerId);
        for (ProductSubscription subscription : subscriptions) {
            try {
                // If there is an authorization configuration, we need to cancel the authorization
                if (subscription.getConsumerAuthConfig() != null) {
                    ProductRefResult productRef =
                            productService.getProductRef(subscription.getProductId());
                    if (productRef != null) {
                        GatewayConfig gatewayConfig =
                                gatewayService.getGatewayConfig(productRef.getGatewayId());
                        ConsumerRef consumerRef = matchConsumerRef(consumerId, gatewayConfig);
                        if (consumerRef != null) {
                            gatewayService.revokeConsumerAuthorization(
                                    productRef.getGatewayId(),
                                    consumerRef.getGwConsumerId(),
                                    subscription.getConsumerAuthConfig());
                        }
                    }
                }
            } catch (Exception e) {
                log.error(
                        "revoke consumer authorization error, consumerId: {}, productId: {}",
                        consumerId,
                        subscription.getProductId(),
                        e);
            }
        }

        // 2. Delete subscriptions
        subscriptionRepository.deleteAllByConsumerId(consumerId);

        // 3. Delete credential
        credentialRepository.deleteAllByConsumerId(consumerId);

        // 4. Delete gateway consumer
        List<ConsumerRef> consumerRefs = consumerRefRepository.findAllByConsumerId(consumerId);
        for (ConsumerRef consumerRef : consumerRefs) {
            try {
                gatewayService.deleteConsumer(
                        consumerRef.getGwConsumerId(), consumerRef.getGatewayConfig());
            } catch (Exception e) {
                log.error(
                        "deleteConsumer gatewayConsumer error, gwConsumerId: {}",
                        consumerRef.getGwConsumerId(),
                        e);
            }
        }

        // 5. Delete consumer reference
        consumerRefRepository.deleteAll(consumerRefs);

        // 6. Delete consumer
        consumerRepository.delete(consumer);
    }

    @Override
    public void createCredential(String consumerId, CreateCredentialParam param) {
        existsConsumer(consumerId);
        // Consumer only has one credential
        credentialRepository
                .findByConsumerId(consumerId)
                .ifPresent(
                        c -> {
                            throw new BusinessException(
                                    ErrorCode.CONFLICT,
                                    StrUtil.format(
                                            "Credential of consumer `{}` already exists",
                                            consumerId));
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

        return credentialRepository
                .findByConsumerId(consumerId)
                .map(credential -> new ConsumerCredentialResult().convertFrom(credential))
                .orElse(new ConsumerCredentialResult());
    }

    @Override
    public void updateCredential(String consumerId, UpdateCredentialParam param) {
        ConsumerCredential credential = findCredential(consumerId);

        param.update(credential);

        List<ConsumerRef> consumerRefs = consumerRefRepository.findAllByConsumerId(consumerId);
        for (ConsumerRef consumerRef : consumerRefs) {
            try {
                gatewayService.updateConsumer(
                        consumerRef.getGwConsumerId(), credential, consumerRef.getGatewayConfig());
            } catch (Exception e) {
                log.error(
                        "Update gatewayConsumer error, gwConsumerId: {}",
                        consumerRef.getGwConsumerId(),
                        e);
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

        Consumer consumer =
                contextHolder.isDeveloper()
                        ? findDevConsumer(consumerId)
                        : findConsumer(consumerId);
        if (subscriptionRepository
                .findByConsumerIdAndProductId(consumerId, param.getProductId())
                .isPresent()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Duplicate subscription");
        }

        ProductResult product = productService.getProduct(param.getProductId());
        ProductRefResult productRef = productService.getProductRef(param.getProductId());
        if (productRef == null) {
            throw new BusinessException(
                    ErrorCode.INTERNAL_ERROR, "API product is not associated with any API");
        }

        // Only gateway source type is supported
        if (productRef.getSourceType() != SourceType.GATEWAY) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST, "API product does not support subscription");
        }

        ConsumerCredential credential = findCredential(consumerId);

        ProductSubscription subscription = param.convertTo();
        subscription.setSubscriptionId(IdGenerator.genSubscriptionId());
        subscription.setConsumerId(consumerId);

        boolean autoApprove;
        if (product.getAutoApprove() != null) {
            autoApprove = product.getAutoApprove();
        } else {
            PortalResult portal = portalService.getPortal(consumer.getPortalId());
            autoApprove =
                    portal.getPortalSettingConfig() != null
                            && BooleanUtil.isTrue(
                                    portal.getPortalSettingConfig().getAutoApproveSubscriptions());
        }

        // If autoApprove is true, immediately authorize and set status to APPROVED
        if (autoApprove) {
            ConsumerAuthConfig consumerAuthConfig =
                    authorizeConsumer(consumer, credential, productRef);
            subscription.setConsumerAuthConfig(consumerAuthConfig);
            subscription.setStatus(SubscriptionStatus.APPROVED);
        } else {
            subscription.setStatus(SubscriptionStatus.PENDING);
        }

        subscriptionRepository.save(subscription);

        SubscriptionResult r = new SubscriptionResult().convertFrom(subscription);
        r.setProductName(product.getName());
        r.setProductType(product.getType());

        return r;
    }

    @Override
    public void unsubscribeProduct(String consumerId, String subscriptionId) {
        existsConsumer(consumerId);

        ProductSubscription subscription =
                findBySubscriptionIdOrProductId(consumerId, subscriptionId);
        if (subscription == null) {
            return;
        }

        if (subscription.getConsumerAuthConfig() != null) {
            ProductRefResult productRef = productService.getProductRef(subscription.getProductId());
            if (productRef != null) {
                GatewayConfig gatewayConfig =
                        gatewayService.getGatewayConfig(productRef.getGatewayId());
                // Revoke the consumer's authorization configuration from the gateway
                Optional.ofNullable(matchConsumerRef(consumerId, gatewayConfig))
                        .ifPresent(
                                consumerRef ->
                                        gatewayService.revokeConsumerAuthorization(
                                                productRef.getGatewayId(),
                                                consumerRef.getGwConsumerId(),
                                                subscription.getConsumerAuthConfig()));
            }
        }

        subscriptionRepository.deleteByConsumerIdAndProductId(
                consumerId, subscription.getProductId());
    }

    private ProductSubscription findBySubscriptionIdOrProductId(
            String consumerId, String subscriptionIdOrProductId) {

        // Compatible with productId
        return subscriptionRepository
                .findBySubscriptionId(subscriptionIdOrProductId)
                .orElseGet(
                        () ->
                                subscriptionRepository
                                        .findByConsumerIdAndProductId(
                                                consumerId, subscriptionIdOrProductId)
                                        .orElse(null));
    }

    @Override
    public PageResult<SubscriptionResult> listSubscriptions(
            String consumerId, QuerySubscriptionParam param, Pageable pageable) {
        existsConsumer(consumerId);

        Page<ProductSubscription> subscriptions =
                subscriptionRepository.findAll(buildCredentialSpec(consumerId, param), pageable);

        List<String> productIds =
                subscriptions.getContent().stream()
                        .map(ProductSubscription::getProductId)
                        .collect(Collectors.toList());
        Map<String, ProductResult> products = productService.getProducts(productIds);
        return new PageResult<SubscriptionResult>()
                .convertFrom(
                        subscriptions,
                        s -> {
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
    public List<SubscriptionResult> listConsumerSubscriptions(String consumerId) {
        List<ProductSubscription> subscriptions =
                subscriptionRepository.findAllByConsumerId(consumerId);
        return subscriptions.stream()
                .map(subscription -> new SubscriptionResult().convertFrom(subscription))
                .collect(Collectors.toList());
    }

    @Override
    public SubscriptionResult approveSubscription(String consumerId, String subscriptionId) {
        existsConsumer(consumerId);

        ProductSubscription subscription =
                findBySubscriptionIdOrProductId(consumerId, subscriptionId);
        if (subscription == null) {
            throw new BusinessException(
                    ErrorCode.NOT_FOUND, Resources.SUBSCRIPTION, subscriptionId);
        }

        if (subscription.getStatus() != SubscriptionStatus.PENDING) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Subscription already approved");
        }

        // Get consumer and credential information
        Consumer consumer =
                contextHolder.isDeveloper()
                        ? findDevConsumer(consumerId)
                        : findConsumer(consumerId);
        ConsumerCredential credential = findCredential(consumerId);

        // Obtain product reference
        ProductRefResult productRef = productService.getProductRef(subscription.getProductId());
        if (productRef == null) {
            throw new BusinessException(
                    ErrorCode.INTERNAL_ERROR, "API product is not associated with any API");
        }

        // Authorize consumer in the gateway
        ConsumerAuthConfig consumerAuthConfig = authorizeConsumer(consumer, credential, productRef);

        subscription.setConsumerAuthConfig(consumerAuthConfig);
        subscription.setStatus(SubscriptionStatus.APPROVED);
        subscriptionRepository.saveAndFlush(subscription);

        ProductResult product = productService.getProduct(subscription.getProductId());
        SubscriptionResult result = new SubscriptionResult().convertFrom(subscription);
        if (product != null) {
            result.setProductName(product.getName());
            result.setProductType(product.getType());
        }
        return result;
    }

    private Consumer findConsumer(String consumerId) {
        return consumerRepository
                .findByConsumerId(consumerId)
                .orElseThrow(
                        () ->
                                new BusinessException(
                                        ErrorCode.NOT_FOUND, Resources.CONSUMER, consumerId));
    }

    private Consumer findDevConsumer(String consumerId) {
        return consumerRepository
                .findByDeveloperIdAndConsumerId(contextHolder.getUser(), consumerId)
                .orElseThrow(
                        () ->
                                new BusinessException(
                                        ErrorCode.NOT_FOUND, Resources.CONSUMER, consumerId));
    }

    private void existsConsumer(String consumerId) {
        (contextHolder.isDeveloper()
                        ? consumerRepository.findByDeveloperIdAndConsumerId(
                                contextHolder.getUser(), consumerId)
                        : consumerRepository.findByConsumerId(consumerId))
                .orElseThrow(
                        () ->
                                new BusinessException(
                                        ErrorCode.NOT_FOUND, Resources.CONSUMER, consumerId));
    }

    private ConsumerCredential findCredential(String consumerId) {
        return credentialRepository
                .findByConsumerId(consumerId)
                .orElseThrow(
                        () ->
                                new BusinessException(
                                        ErrorCode.NOT_FOUND,
                                        Resources.CONSUMER_CREDENTIAL,
                                        consumerId));
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

    private Specification<ProductSubscription> buildCredentialSpec(
            String consumerId, QuerySubscriptionParam param) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("consumerId"), consumerId));
            if (param.getStatus() != null) {
                predicates.add(cb.equal(root.get("status"), param.getStatus()));
            }
            if (StrUtil.isNotBlank(param.getProductName())) {
                Subquery<String> productSubquery = query.subquery(String.class);
                Root<Product> productRoot = productSubquery.from(Product.class);

                productSubquery
                        .select(productRoot.get("productId"))
                        .where(
                                cb.like(
                                        cb.lower(productRoot.get("name")),
                                        "%" + param.getProductName().toLowerCase() + "%"));

                predicates.add(root.get("productId").in(productSubquery));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private void complementCredentials(ConsumerCredential credential) {
        if (credential == null) {
            return;
        }

        // ApiKey
        if (credential.getApiKeyConfig() != null) {
            List<ApiKeyConfig.ApiKeyCredential> apiKeyCredentials =
                    credential.getApiKeyConfig().getCredentials();
            if (apiKeyCredentials != null) {
                for (ApiKeyConfig.ApiKeyCredential cred : apiKeyCredentials) {
                    if (cred.getMode() == CredentialMode.SYSTEM
                            && StrUtil.isBlank(cred.getApiKey())) {
                        cred.setApiKey(IdGenerator.genIdWithPrefix("apikey-"));
                    }
                }
            }
        }

        // HMAC
        if (credential.getHmacConfig() != null) {
            List<HmacConfig.HmacCredential> hmacCredentials =
                    credential.getHmacConfig().getCredentials();
            if (hmacCredentials != null) {
                for (HmacConfig.HmacCredential cred : hmacCredentials) {
                    if (cred.getMode() == CredentialMode.SYSTEM
                            && (StrUtil.isBlank(cred.getAk()) || StrUtil.isBlank(cred.getSk()))) {
                        cred.setAk(IdGenerator.genIdWithPrefix("ak-"));
                        cred.setSk(IdGenerator.genIdWithPrefix("sk-"));
                    }
                }
            }
        }
    }

    private ConsumerAuthConfig authorizeConsumer(
            Consumer consumer, ConsumerCredential credential, ProductRefResult productRef) {
        GatewayConfig gatewayConfig = gatewayService.getGatewayConfig(productRef.getGatewayId());

        // Check if consumer exists in gateway
        ConsumerRef existingConsumerRef = matchConsumerRef(consumer.getConsumerId(), gatewayConfig);
        String gwConsumerId;

        if (existingConsumerRef != null) {
            gwConsumerId = existingConsumerRef.getGwConsumerId();

            if (!isConsumerExistsInGateway(gwConsumerId, gatewayConfig)) {
                log.warn(
                        "Consumer in gateway was deleted, need to recreate consumer: gwConsumerId:"
                                + " {}, gatewayType: {}",
                        gwConsumerId,
                        gatewayConfig.getGatewayType());

                // Delete expired ConsumerRef record
                consumerRefRepository.delete(existingConsumerRef);

                // Recreate consumer
                gwConsumerId = gatewayService.createConsumer(consumer, credential, gatewayConfig);
                consumerRefRepository.save(
                        ConsumerRef.builder()
                                .consumerId(consumer.getConsumerId())
                                .gwConsumerId(gwConsumerId)
                                .gatewayType(gatewayConfig.getGatewayType())
                                .gatewayConfig(gatewayConfig)
                                .build());
            }
        } else {
            // If no ConsumerRef record exists, create new consumer directly
            gwConsumerId = gatewayService.createConsumer(consumer, credential, gatewayConfig);
            consumerRefRepository.save(
                    ConsumerRef.builder()
                            .consumerId(consumer.getConsumerId())
                            .gwConsumerId(gwConsumerId)
                            .gatewayType(gatewayConfig.getGatewayType())
                            .gatewayConfig(gatewayConfig)
                            .build());
        }

        // Authorize consumer
        return gatewayService.authorizeConsumer(
                productRef.getGatewayId(), gwConsumerId, productRef);
    }

    private boolean isConsumerExistsInGateway(String gwConsumerId, GatewayConfig gatewayConfig) {
        try {
            return gatewayService.isConsumerExists(gwConsumerId, gatewayConfig);
        } catch (Exception e) {
            log.warn(
                    "Failed to check consumer existence in gateway, gwConsumerId: {}, gatewayType:"
                            + " {}",
                    gwConsumerId,
                    gatewayConfig.getGatewayType(),
                    e);
            return true;
        }
    }

    @EventListener
    @Async("taskExecutor")
    public void onDeveloperDeletion(DeveloperDeletingEvent event) {
        String developerId = event.getDeveloperId();
        log.info("Cleaning consumers for developer {}", developerId);

        List<Consumer> consumers = consumerRepository.findAllByDeveloperId(developerId);
        consumers.forEach(
                consumer -> {
                    try {
                        deleteConsumer(consumer.getConsumerId());
                    } catch (Exception e) {
                        log.error("Failed to delete consumer {}", consumer.getConsumerId(), e);
                    }
                });
    }

    @EventListener
    @Async("taskExecutor")
    public void onProductDeletion(ProductDeletingEvent event) {
        String productId = event.getProductId();
        log.info("Cleaning subscriptions for product {}", productId);

        subscriptionRepository.deleteAllByProductId(productId);

        List<ProductSubscription> subscriptions =
                subscriptionRepository.findAllByProductId(productId);

        subscriptions.forEach(
                subscription -> {
                    try {
                        unsubscribeProduct(
                                subscription.getConsumerId(), subscription.getProductId());
                    } catch (Exception e) {
                        log.error(
                                "Failed to unsubscribe product {} for consumer {}",
                                productId,
                                subscription.getConsumerId(),
                                e);
                    }
                });
    }

    private ConsumerRef matchConsumerRef(String consumerId, GatewayConfig gatewayConfig) {
        List<ConsumerRef> consumeRefs =
                consumerRefRepository.findAllByConsumerIdAndGatewayType(
                        consumerId, gatewayConfig.getGatewayType());
        if (consumeRefs.isEmpty()) {
            return null;
        }

        for (ConsumerRef ref : consumeRefs) {
            // Check if the gateway config matches
            if (StrUtil.equals(
                    JSONUtil.toJsonStr(ref.getGatewayConfig()),
                    JSONUtil.toJsonStr(gatewayConfig))) {
                return ref;
            }
        }
        return null;
    }

    @Override
    public CredentialContext getDefaultCredential(String developerId) {
        try {
            ConsumerResult consumer = getPrimaryConsumer();

            return credentialRepository
                    .findByConsumerId(consumer.getConsumerId())
                    .map(this::buildAuthInfo)
                    .orElseGet(
                            () -> {
                                log.debug(
                                        "No credential found for consumer: {}",
                                        consumer.getConsumerId());
                                return CredentialContext.builder().build();
                            });
        } catch (BusinessException e) {
            log.debug("No consumer found for developer: {}", developerId);
            return CredentialContext.builder().build();
        }
    }

    @Override
    @Transactional
    public void setPrimaryConsumer(String consumerId) {
        Consumer consumer = findDevConsumer(consumerId);

        // Return if consumer is already primary
        if (BooleanUtil.isTrue(consumer.getIsPrimary())) {
            log.debug("Consumer already primary: consumerId={}", consumerId);
            return;
        }

        // Clear primary consumer for the developer
        consumerRepository.clearPrimary(contextHolder.getUser());

        consumer.setIsPrimary(true);
        consumerRepository.save(consumer);
    }

    @Override
    public ConsumerResult getPrimaryConsumer() {
        String developerId = contextHolder.getUser();
        return consumerRepository
                .findByDeveloperIdAndIsPrimary(developerId, true)
                .map(
                        consumer -> {
                            log.debug(
                                    "Found existing primary consumer: developerId={},"
                                            + " consumerId={}",
                                    developerId,
                                    consumer.getConsumerId());
                            return new ConsumerResult().convertFrom(consumer);
                        })
                // If no primary consumer found, set the first consumer as primary
                .orElseGet(
                        () -> {
                            Consumer firstConsumer =
                                    consumerRepository
                                            .findFirstByDeveloperId(
                                                    developerId,
                                                    Sort.by(Sort.Direction.ASC, "createAt"))
                                            .orElseThrow(
                                                    () ->
                                                            new BusinessException(
                                                                    ErrorCode.INVALID_REQUEST,
                                                                    "No consumer found for"
                                                                            + " developer: "
                                                                            + developerId));

                            firstConsumer.setIsPrimary(true);
                            consumerRepository.save(firstConsumer);

                            return new ConsumerResult().convertFrom(firstConsumer);
                        });
    }

    /**
     * Build authentication info from credential
     *
     * <p>Source types: - DEFAULT: Authorization: Bearer {apiKey} - Query: ?{key}={apiKey} - Header
     * (or others): {key}: {apiKey}
     *
     * @param credential consumer credential
     * @return authentication info (never null, but maps may be empty)
     */
    private CredentialContext buildAuthInfo(ConsumerCredential credential) {
        Map<String, String> headers = new HashMap<>();
        Map<String, String> queryParams = new HashMap<>();

        ApiKeyConfig config = credential.getApiKeyConfig();

        // Check if apiKey config exists and has credentials
        if (config == null
                || config.getCredentials() == null
                || config.getCredentials().isEmpty()) {
            log.debug("No API key configured for credential");
            return CredentialContext.builder().build();
        }

        // Use first credential
        String apiKey = config.getCredentials().get(0).getApiKey();
        String source = config.getSource();
        String key = config.getKey();

        // Add to headers or queryParams based on source
        if ("DEFAULT".equalsIgnoreCase(source)) {
            headers.put("Authorization", "Bearer " + apiKey);
        } else if ("QueryString".equalsIgnoreCase(source)) {
            queryParams.put(key, apiKey);
        } else {
            // Header or other values
            headers.put(key, apiKey);
        }

        return CredentialContext.builder()
                .apiKey(apiKey)
                .headers(headers)
                .queryParams(queryParams)
                .build();
    }
}
