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
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.apiopenplatform.core.constant.Resources;
import com.alibaba.apiopenplatform.core.event.PortalDeletingEvent;
import com.alibaba.apiopenplatform.core.exception.BusinessException;
import com.alibaba.apiopenplatform.core.exception.ErrorCode;
import com.alibaba.apiopenplatform.core.security.ContextHolder;
import com.alibaba.apiopenplatform.core.utils.IdGenerator;
import com.alibaba.apiopenplatform.dto.params.consumer.QuerySubscriptionParam;
import com.alibaba.apiopenplatform.dto.params.portal.*;
import com.alibaba.apiopenplatform.dto.result.common.PageResult;
import com.alibaba.apiopenplatform.dto.result.portal.PortalResult;
import com.alibaba.apiopenplatform.dto.result.product.SubscriptionResult;
import com.alibaba.apiopenplatform.entity.Portal;
import com.alibaba.apiopenplatform.entity.PortalDomain;
import com.alibaba.apiopenplatform.entity.ProductSubscription;
import com.alibaba.apiopenplatform.repository.PortalDomainRepository;
import com.alibaba.apiopenplatform.repository.PortalRepository;
import com.alibaba.apiopenplatform.repository.SubscriptionRepository;
import com.alibaba.apiopenplatform.repository.ProductPublicationRepository;
import com.alibaba.apiopenplatform.repository.ProductRefRepository;
import com.alibaba.apiopenplatform.service.GatewayService;
import com.alibaba.apiopenplatform.entity.ProductPublication;
import com.alibaba.apiopenplatform.entity.ProductRef;
import org.springframework.data.domain.PageRequest;
import com.alibaba.apiopenplatform.service.IdpService;
import com.alibaba.apiopenplatform.service.PortalService;
import com.alibaba.apiopenplatform.support.enums.DomainType;
import com.alibaba.apiopenplatform.support.enums.SearchEngineType;
import com.alibaba.apiopenplatform.support.portal.OidcConfig;
import com.alibaba.apiopenplatform.support.portal.PortalSettingConfig;
import com.alibaba.apiopenplatform.support.portal.PortalUiConfig;
import com.alibaba.apiopenplatform.support.portal.SearchEngineConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import jakarta.persistence.criteria.Predicate;
import jakarta.transaction.Transactional;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional
public class PortalServiceImpl implements PortalService {

    private final PortalRepository portalRepository;

    private final PortalDomainRepository portalDomainRepository;

    private final ApplicationEventPublisher eventPublisher;

    private final SubscriptionRepository subscriptionRepository;

    private final ContextHolder contextHolder;

    private final IdpService idpService;

    private final String domainFormat = "%s.api.portal.local";

    private final ProductPublicationRepository publicationRepository;

    private final ProductRefRepository productRefRepository;

    private final GatewayService gatewayService;

    public PortalResult createPortal(CreatePortalParam param) {
        portalRepository.findByName(param.getName())
                .ifPresent(portal -> {
                    throw new BusinessException(ErrorCode.CONFLICT, StrUtil.format("{}:{}已存在", Resources.PORTAL, portal.getName()));
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
    public void existsPortal(String portalId) {
        portalRepository.findByPortalId(portalId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, Resources.PORTAL, portalId));
    }

    @Override
    public PageResult<PortalResult> listPortals(Pageable pageable) {
        Page<Portal> portals = portalRepository.findAll(pageable);

        // 填充Domain
        if (portals.hasContent()) {
            List<String> portalIds = portals.getContent().stream()
                    .map(Portal::getPortalId)
                    .collect(Collectors.toList());

            List<PortalDomain> allDomains = portalDomainRepository.findAllByPortalIdIn(portalIds);
            Map<String, List<PortalDomain>> portalDomains = allDomains.stream()
                    .collect(Collectors.groupingBy(PortalDomain::getPortalId));

            portals.getContent().forEach(portal -> {
                List<PortalDomain> domains = portalDomains.getOrDefault(portal.getPortalId(), new ArrayList<>());
                portal.setPortalDomains(domains);
            });
        }

        return new PageResult<PortalResult>().convertFrom(portals, portal -> new PortalResult().convertFrom(portal));
    }

    @Override
    public PortalResult updatePortal(String portalId, UpdatePortalParam param) {
        Portal portal = findPortal(portalId);

        Optional.ofNullable(param.getName())
                .filter(name -> !name.equals(portal.getName()))
                .flatMap(portalRepository::findByName)
                .ifPresent(p -> {
                    throw new BusinessException(ErrorCode.CONFLICT, StrUtil.format("{}:{}已存在", Resources.PORTAL, portal.getName()));
                });

        param.update(portal);
        
        // 验证配置
        PortalSettingConfig setting = portal.getPortalSettingConfig();
        
        // 验证OIDC配置
        if (CollUtil.isNotEmpty(setting.getOidcConfigs())) {
            idpService.validateOidcConfigs(setting.getOidcConfigs());
        }

        // 验证OAuth2配置
        if (CollUtil.isNotEmpty(setting.getOauth2Configs())) {
            idpService.validateOAuth2Configs(setting.getOauth2Configs());
        }

        // 验证搜索引擎配置（新增）
        if (setting.getSearchEngineConfig() != null) {
            validateSearchEngineConfig(setting.getSearchEngineConfig());
        }

        // 至少保留一种认证方式
        if (BooleanUtil.isFalse(setting.getBuiltinAuthEnabled())) {
            boolean enabledOidc = Optional.ofNullable(setting.getOidcConfigs())
                    .filter(CollUtil::isNotEmpty)
                    .map(configs -> configs.stream().anyMatch(OidcConfig::isEnabled))
                    .orElse(false);

            if (!enabledOidc) {
                throw new BusinessException(ErrorCode.INVALID_REQUEST, "至少配置一种认证方式");
            }
        }
        portalRepository.saveAndFlush(portal);

        return getPortal(portal.getPortalId());
    }

    @Override
    public void deletePortal(String portalId) {
        Portal portal = findPortal(portalId);

        // 清理Domain
        portalDomainRepository.deleteAllByPortalId(portalId);

        // 异步清理门户资源
        eventPublisher.publishEvent(new PortalDeletingEvent(portalId));
        portalRepository.delete(portal);
    }

    @Override
    public String resolvePortal(String domain) {
        return portalDomainRepository.findByDomain(domain)
                .map(PortalDomain::getPortalId)
                .orElse(null);
    }

    @Override
    public PortalResult bindDomain(String portalId, BindDomainParam param) {
        existsPortal(portalId);
        portalDomainRepository.findByPortalIdAndDomain(portalId, param.getDomain())
                .ifPresent(portalDomain -> {
                    throw new BusinessException(ErrorCode.CONFLICT, StrUtil.format("{}:{}已存在", Resources.PORTAL_DOMAIN, portalDomain.getDomain()));
                });

        PortalDomain portalDomain = param.convertTo();
        portalDomain.setPortalId(portalId);

        portalDomainRepository.save(portalDomain);
        return getPortal(portalId);
    }

    @Override
    public PortalResult unbindDomain(String portalId, String domain) {
        portalDomainRepository.findByPortalIdAndDomain(portalId, domain)
                .ifPresent(portalDomain -> {
                    // 默认域名不允许解绑
                    if (portalDomain.getType() == DomainType.DEFAULT) {
                        throw new BusinessException(ErrorCode.INVALID_REQUEST, "默认域名不允许解绑");
                    }
                    portalDomainRepository.delete(portalDomain);
                });
        return getPortal(portalId);
    }

    @Override
    public PageResult<SubscriptionResult> listSubscriptions(String portalId, QuerySubscriptionParam param, Pageable pageable) {
        // Ensure portal exists
        existsPortal(portalId);

        Specification<ProductSubscription> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("portalId"), portalId));
            if (param != null && param.getStatus() != null) {
                predicates.add(cb.equal(root.get("status"), param.getStatus()));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<ProductSubscription> page = subscriptionRepository.findAll(spec, pageable);
        return new PageResult<SubscriptionResult>().convertFrom(page, s -> new SubscriptionResult().convertFrom(s));
    }

    @Override
    public String getDefaultPortal() {
        Portal portal = portalRepository.findFirstByOrderByIdAsc().orElse(null);
        if (portal == null) {
            return null;
        }
        return portal.getPortalId();
    }

    @Override
    public String getDashboard(String portalId) {
        existsPortal(portalId);

        // 找到该门户下任一已发布产品（取第一页第一条）
        ProductPublication pub = publicationRepository.findByPortalId(portalId, PageRequest.of(0, 1))
                .stream()
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, Resources.PORTAL, portalId));

        // 取产品的网关引用
        ProductRef productRef = productRefRepository.findFirstByProductId(pub.getProductId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, Resources.PRODUCT, pub.getProductId()));

        if (productRef.getGatewayId() == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "网关", "该门户下的产品尚未关联网关服务");
        }

        // 复用网关的Dashboard能力
        return gatewayService.getDashboard(productRef.getGatewayId(),"Portal");
    }

    private Portal findPortal(String portalId) {
        return portalRepository.findByPortalId(portalId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, Resources.PORTAL, portalId));
    }

    // ========== 搜索引擎配置查询实现 ==========

    /**
     * 核心方法：根据引擎类型获取 API Key
     * 供 TalkSearchAbilityServiceGoogleImpl 等搜索能力调用
     */
    @Override
    public String getSearchEngineApiKey(String portalId, SearchEngineType engineType) {
        Portal portal = findPortal(portalId);
        PortalSettingConfig settings = portal.getPortalSettingConfig();

        if (settings == null || settings.getSearchEngineConfig() == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND,
                    StrUtil.format("Portal {} 未配置搜索引擎", portalId));
        }

        SearchEngineConfig config = settings.getSearchEngineConfig();

        // 检查引擎类型是否匹配
        if (config.getEngineType() != engineType) {
            throw new BusinessException(ErrorCode.NOT_FOUND,
                    StrUtil.format("Portal {} 配置的搜索引擎类型是 {}，不是 {}",
                            portalId, config.getEngineType(), engineType));
        }

        // 检查是否启用
        if (!config.isEnabled()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST,
                    StrUtil.format("Portal {} 的搜索引擎已禁用", portalId));
        }

        return config.getApiKey(); // API Key 会自动解密（通过 @Encrypted 注解）
    }

    @Override
    public SearchEngineConfig getSearchEngineConfig(String portalId) {
        Portal portal = findPortal(portalId);
        PortalSettingConfig settings = portal.getPortalSettingConfig();

        if (settings == null) {
            return null;
        }

        return settings.getSearchEngineConfig();
    }

    // ========== 私有辅助方法 ==========

    /**
     * 验证搜索引擎配置
     */
    private void validateSearchEngineConfig(SearchEngineConfig config) {
        if (config == null) {
            return;
        }

        // 验证引擎类型是否支持
        if (config.getEngineType() == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "搜索引擎类型不能为空");
        }

        if (!SearchEngineType.isSupported(config.getEngineType())) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST,
                    StrUtil.format("不支持的搜索引擎类型: {}，当前仅支持: {}",
                            config.getEngineType(),
                            SearchEngineType.getSupportedTypes()));
        }

        // 验证必填字段
        if (StrUtil.isBlank(config.getEngineName())) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "搜索引擎名称不能为空");
        }

        if (StrUtil.isBlank(config.getApiKey())) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "API Key不能为空");
        }

        log.info("Validated search engine config: type={}, name={}",
                config.getEngineType(), config.getEngineName());
    }
}
