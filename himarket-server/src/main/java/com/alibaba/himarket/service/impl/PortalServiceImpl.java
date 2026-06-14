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
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.extra.spring.SpringUtil;
import com.alibaba.himarket.core.constant.Resources;
import com.alibaba.himarket.core.event.PortalDeletingEvent;
import com.alibaba.himarket.core.exception.BusinessException;
import com.alibaba.himarket.core.exception.ErrorCode;
import com.alibaba.himarket.core.security.ContextHolder;
import com.alibaba.himarket.core.utils.IdGenerator;
import com.alibaba.himarket.dto.params.consumer.QuerySubscriptionParam;
import com.alibaba.himarket.dto.params.portal.BindDomainParam;
import com.alibaba.himarket.dto.params.portal.CreatePortalParam;
import com.alibaba.himarket.dto.params.portal.UpdatePortalParam;
import com.alibaba.himarket.dto.result.common.PageResult;
import com.alibaba.himarket.dto.result.portal.PortalProfileResult;
import com.alibaba.himarket.dto.result.portal.PortalResult;
import com.alibaba.himarket.dto.result.product.ProductPublicationResult;
import com.alibaba.himarket.dto.result.product.SubscriptionResult;
import com.alibaba.himarket.entity.Portal;
import com.alibaba.himarket.entity.PortalDomain;
import com.alibaba.himarket.entity.Product;
import com.alibaba.himarket.entity.ProductPublication;
import com.alibaba.himarket.entity.ProductSubscription;
import com.alibaba.himarket.repository.PortalDomainRepository;
import com.alibaba.himarket.repository.PortalRepository;
import com.alibaba.himarket.repository.ProductPublicationRepository;
import com.alibaba.himarket.repository.ProductRepository;
import com.alibaba.himarket.repository.SubscriptionRepository;
import com.alibaba.himarket.service.IdpService;
import com.alibaba.himarket.service.PortalService;
import com.alibaba.himarket.support.enums.DomainType;
import com.alibaba.himarket.support.portal.OidcConfig;
import com.alibaba.himarket.support.portal.PortalSettingConfig;
import com.alibaba.himarket.support.portal.PortalUiConfig;
import jakarta.persistence.criteria.Predicate;
import jakarta.transaction.Transactional;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional
public class PortalServiceImpl implements PortalService {

    private final PortalRepository portalRepository;

    private final PortalDomainRepository portalDomainRepository;

    private final SubscriptionRepository subscriptionRepository;

    private final ContextHolder contextHolder;

    private final IdpService idpService;

    private final String domainFormat = "%s.himarket.local";

    private final ProductPublicationRepository publicationRepository;

    private final ProductRepository productRepository;

    public PortalResult createPortal(CreatePortalParam param) {
        portalRepository
                .findByName(param.getName())
                .ifPresent(
                        portal -> {
                            throw new BusinessException(
                                    ErrorCode.CONFLICT,
                                    StrUtil.format(
                                            "Portal with name `{}` already exists",
                                            portal.getName()));
                        });

        String portalId = IdGenerator.genPortalId();
        Portal portal = param.convertTo();
        portal.setPortalId(portalId);
        portal.setAdminId(contextHolder.getUser());

        // Setting & Ui
        portal.setPortalSettingConfig(new PortalSettingConfig());
        portal.setPortalUiConfig(new PortalUiConfig());

        // Domain
        PortalDomain portalDomain = new PortalDomain();
        portalDomain.setDomain(String.format(domainFormat, portalId));
        portalDomain.setPortalId(portalId);

        portalDomainRepository.save(portalDomain);
        portalRepository.save(portal);

        return getPortal(portalId);
    }

    @Override
    public PortalResult getPortal(String portalId) {
        Portal portal = findPortal(portalId);
        List<PortalDomain> domains = portalDomainRepository.findAllByPortalId(portalId);
        portal.setPortalDomains(domains);

        return new PortalResult().convertFrom(portal);
    }

    @Override
    public PortalProfileResult getPortalProfile() {
        Portal portal = findPortal(contextHolder.getPortal());

        return new PortalProfileResult().convertFrom(portal);
    }

    @Override
    public void existsPortal(String portalId) {
        portalRepository
                .findByPortalId(portalId)
                .orElseThrow(
                        () ->
                                new BusinessException(
                                        ErrorCode.NOT_FOUND, Resources.PORTAL, portalId));
    }

    @Override
    public PageResult<PortalResult> listPortals(Pageable pageable) {
        Page<Portal> portals = portalRepository.findAll(pageable);

        // Fill domain
        if (portals.hasContent()) {
            List<String> portalIds =
                    portals.getContent().stream().map(Portal::getPortalId).toList();

            List<PortalDomain> allDomains = portalDomainRepository.findAllByPortalIdIn(portalIds);
            Map<String, List<PortalDomain>> portalDomains =
                    allDomains.stream().collect(Collectors.groupingBy(PortalDomain::getPortalId));

            for (Portal portal : portals.getContent()) {
                List<PortalDomain> domains =
                        portalDomains.getOrDefault(portal.getPortalId(), new ArrayList<>());
                portal.setPortalDomains(domains);
            }
        }

        return new PageResult<PortalResult>()
                .convertFrom(portals, portal -> new PortalResult().convertFrom(portal));
    }

    @Override
    public PortalResult updatePortal(String portalId, UpdatePortalParam param) {
        Portal portal = findPortal(portalId);

        String requestedName = param.getName();
        if (requestedName != null && !requestedName.equals(portal.getName())) {
            Portal existingPortal = portalRepository.findByName(requestedName).orElse(null);
            if (existingPortal != null) {
                throw new BusinessException(
                        ErrorCode.CONFLICT,
                        StrUtil.format("Portal with name `{}` already exists", requestedName));
            }
        }

        param.update(portal);

        PortalSettingConfig setting = portal.getPortalSettingConfig();

        // Verify OIDC configs
        if (CollUtil.isNotEmpty(setting.getOidcConfigs())) {
            idpService.validateOidcConfigs(setting.getOidcConfigs());
        }

        // Verify OAuth2 configs
        if (CollUtil.isNotEmpty(setting.getOauth2Configs())) {
            idpService.validateOAuth2Configs(setting.getOauth2Configs());
        }

        // At least keep one authentication method
        if (BooleanUtil.isFalse(setting.getBuiltinAuthEnabled())) {
            boolean enabledOidc = false;
            List<OidcConfig> oidcConfigs = setting.getOidcConfigs();
            if (CollUtil.isNotEmpty(oidcConfigs)) {
                for (OidcConfig oidcConfig : oidcConfigs) {
                    if (oidcConfig.isEnabled()) {
                        enabledOidc = true;
                        break;
                    }
                }
            }

            if (!enabledOidc) {
                throw new BusinessException(
                        ErrorCode.INVALID_REQUEST,
                        "At least one authentication method must be configured");
            }
        }
        portalRepository.saveAndFlush(portal);

        return getPortal(portal.getPortalId());
    }

    @Override
    public void deletePortal(String portalId) {
        Portal portal = findPortal(portalId);

        // Clean up domains
        portalDomainRepository.deleteAllByPortalId(portalId);

        // Asynchronously clean up portal resources
        SpringUtil.getApplicationContext().publishEvent(new PortalDeletingEvent(portalId));
        portalRepository.delete(portal);
    }

    @Override
    public String resolvePortal(String domain) {
        return portalDomainRepository
                .findByDomain(domain)
                .map(PortalDomain::getPortalId)
                .orElse(null);
    }

    @Override
    public PortalResult bindDomain(String portalId, BindDomainParam param) {
        existsPortal(portalId);
        portalDomainRepository
                .findByPortalIdAndDomain(portalId, param.getDomain())
                .ifPresent(
                        portalDomain -> {
                            throw new BusinessException(
                                    ErrorCode.CONFLICT,
                                    StrUtil.format(
                                            "Portal domain `{}` already exists",
                                            param.getDomain()));
                        });

        PortalDomain portalDomain = param.convertTo();
        portalDomain.setPortalId(portalId);

        portalDomainRepository.save(portalDomain);
        return getPortal(portalId);
    }

    @Override
    public PortalResult unbindDomain(String portalId, String domain) {
        portalDomainRepository
                .findByPortalIdAndDomain(portalId, domain)
                .ifPresent(
                        portalDomain -> {
                            // Default domain cannot be unbound
                            if (portalDomain.getType() == DomainType.DEFAULT) {
                                throw new BusinessException(
                                        ErrorCode.INVALID_REQUEST,
                                        "Default domain cannot be unbound");
                            }
                            portalDomainRepository.delete(portalDomain);
                        });
        return getPortal(portalId);
    }

    @Override
    public PageResult<SubscriptionResult> listSubscriptions(
            String portalId, QuerySubscriptionParam param, Pageable pageable) {
        // Ensure portal exists
        existsPortal(portalId);

        Specification<ProductSubscription> spec =
                (root, query, cb) -> {
                    List<Predicate> predicates = new ArrayList<>();
                    predicates.add(cb.equal(root.get("portalId"), portalId));
                    if (param != null && param.getStatus() != null) {
                        predicates.add(cb.equal(root.get("status"), param.getStatus()));
                    }
                    return cb.and(predicates.toArray(new Predicate[0]));
                };

        Page<ProductSubscription> page = subscriptionRepository.findAll(spec, pageable);
        return new PageResult<SubscriptionResult>()
                .convertFrom(page, s -> new SubscriptionResult().convertFrom(s));
    }

    @Override
    public PageResult<ProductPublicationResult> getPublications(
            String portalId, Pageable pageable) {
        existsPortal(portalId);

        Page<ProductPublication> publications =
                publicationRepository.findByPortalId(portalId, pageable);

        return new PageResult<ProductPublicationResult>()
                .convertFrom(
                        publications,
                        publication -> {
                            ProductPublicationResult publicationResult =
                                    new ProductPublicationResult().convertFrom(publication);

                            // Fill portal information
                            try {
                                Portal portal = findPortal(publication.getPortalId());
                                publicationResult.setPortalName(portal.getName());
                                publicationResult.setAutoApproveSubscriptions(
                                        portal.getPortalSettingConfig()
                                                .getAutoApproveSubscriptions());
                            } catch (Exception e) {
                                log.error(
                                        "Failed to get portal, portalId={}",
                                        publication.getPortalId(),
                                        e);
                            }

                            // Fill product information
                            try {
                                Product product =
                                        productRepository
                                                .findByProductId(publication.getProductId())
                                                .orElse(null);
                                if (product != null) {
                                    publicationResult.setProductId(product.getProductId());
                                    publicationResult.setProductName(product.getName());
                                    publicationResult.setProductType(product.getType().name());
                                    publicationResult.setDescription(product.getDescription());
                                }
                            } catch (Exception e) {
                                log.error(
                                        "Failed to get product, productId={}",
                                        publication.getProductId(),
                                        e);
                            }

                            return publicationResult;
                        });
    }

    @Override
    public String getDefaultPortal() {
        Portal portal = portalRepository.findFirstByOrderByIdAsc().orElse(null);
        if (portal == null) {
            return null;
        }
        return portal.getPortalId();
    }

    private Portal findPortal(String portalId) {
        return portalRepository
                .findByPortalId(portalId)
                .orElseThrow(
                        () ->
                                new BusinessException(
                                        ErrorCode.NOT_FOUND, Resources.PORTAL, portalId));
    }
}
