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
import com.alibaba.himarket.core.constant.Resources;
import com.alibaba.himarket.core.event.DeveloperDeletingEvent;
import com.alibaba.himarket.core.event.PortalDeletingEvent;
import com.alibaba.himarket.core.utils.TokenUtil;
import com.alibaba.himarket.dto.params.consumer.CreateConsumerParam;
import com.alibaba.himarket.dto.params.developer.CreateDeveloperParam;
import com.alibaba.himarket.dto.params.developer.CreateExternalDeveloperParam;
import com.alibaba.himarket.dto.params.developer.QueryDeveloperParam;
import com.alibaba.himarket.dto.params.developer.UpdateDeveloperParam;
import com.alibaba.himarket.dto.result.common.AuthResult;
import com.alibaba.himarket.dto.result.developer.DeveloperResult;
import com.alibaba.himarket.dto.result.common.PageResult;
import com.alibaba.himarket.entity.Developer;
import com.alibaba.himarket.entity.Portal;
import com.alibaba.himarket.repository.DeveloperRepository;
import com.alibaba.himarket.repository.PortalRepository;
import com.alibaba.himarket.service.ConsumerService;
import com.alibaba.himarket.service.DeveloperService;
import com.alibaba.himarket.core.utils.PasswordHasher;
import com.alibaba.himarket.core.utils.IdGenerator;
import com.alibaba.himarket.repository.DeveloperExternalIdentityRepository;
import com.alibaba.himarket.entity.DeveloperExternalIdentity;
import com.alibaba.himarket.support.enums.DeveloperAuthType;
import com.alibaba.himarket.support.enums.DeveloperStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.alibaba.himarket.core.exception.BusinessException;
import com.alibaba.himarket.core.exception.ErrorCode;
import com.alibaba.himarket.core.security.ContextHolder;

import jakarta.persistence.criteria.Predicate;
import java.util.*;
import jakarta.servlet.http.HttpServletRequest;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class DeveloperServiceImpl implements DeveloperService {

    private final DeveloperRepository developerRepository;

    private final DeveloperExternalIdentityRepository externalRepository;

    private final PortalRepository portalRepository;

    private final ContextHolder contextHolder;

    private final ApplicationEventPublisher eventPublisher;

    private final ConsumerService consumerService;

    @Override
    public AuthResult registerDeveloper(CreateDeveloperParam param) {
        DeveloperResult developer = createDeveloper(param);

        // Allocate default consumer
        createDefaultConsumer(developer.getDeveloperId());
        // Check if auto approve
        String portalId = contextHolder.getPortal();
        Portal portal = findPortal(portalId);
        boolean autoApprove = portal.getPortalSettingConfig() != null
                && BooleanUtil.isTrue(portal.getPortalSettingConfig().getAutoApproveDevelopers());

        if (autoApprove) {
            String token = generateToken(developer.getDeveloperId());
            return AuthResult.of(token, TokenUtil.getTokenExpiresIn());
        }
        return null;
    }

    @Override
    public DeveloperResult createDeveloper(CreateDeveloperParam param) {
        String portalId = contextHolder.getPortal();
        developerRepository.findByPortalIdAndUsername(portalId, param.getUsername()).ifPresent(developer -> {
            throw new BusinessException(ErrorCode.CONFLICT, StrUtil.format("Developer with name `{}` already exists", developer.getUsername()));
        });

        Developer developer = param.convertTo();
        developer.setDeveloperId(generateDeveloperId());
        developer.setPortalId(portalId);
        developer.setPasswordHash(PasswordHasher.hash(param.getPassword()));

        Portal portal = findPortal(portalId);
        boolean autoApprove = portal.getPortalSettingConfig() != null
                && BooleanUtil.isTrue(portal.getPortalSettingConfig().getAutoApproveDevelopers());
        developer.setStatus(autoApprove ? DeveloperStatus.APPROVED : DeveloperStatus.PENDING);
        developer.setAuthType(DeveloperAuthType.BUILTIN);

        developerRepository.save(developer);
        return new DeveloperResult().convertFrom(developer);
    }

    @Override
    public AuthResult login(String username, String password) {
        String portalId = contextHolder.getPortal();
        Developer developer = developerRepository.findByPortalIdAndUsername(portalId, username)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, Resources.DEVELOPER, username));

        if (!DeveloperStatus.APPROVED.equals(developer.getStatus())) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Your account is pending approval");
        }

        if (!PasswordHasher.verify(password, developer.getPasswordHash())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "The username or password you entered is incorrect");
        }

        String token = generateToken(developer.getDeveloperId());
        return AuthResult.builder()
                .accessToken(token)
                .expiresIn(TokenUtil.getTokenExpiresIn())
                .build();
    }

    @Override
    public void existsDeveloper(String developerId) {
        developerRepository.findByDeveloperId(developerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, Resources.DEVELOPER, developerId));
    }

    @Override
    public DeveloperResult createExternalDeveloper(CreateExternalDeveloperParam param) {
        Developer developer = Developer.builder()
                .developerId(IdGenerator.genDeveloperId())
                .portalId(contextHolder.getPortal())
                .username(buildExternalName(param.getProvider(), param.getDisplayName()))
                .email(param.getEmail())
                // Default APPROVED
                .status(DeveloperStatus.APPROVED)
                .build();

        DeveloperExternalIdentity externalIdentity = DeveloperExternalIdentity.builder()
                .provider(param.getProvider())
                .subject(param.getSubject())
                .displayName(param.getDisplayName())
                .authType(param.getAuthType())
                .developer(developer)
                .build();

        developerRepository.save(developer);
        externalRepository.save(externalIdentity);
        createDefaultConsumer(developer.getDeveloperId());

        return new DeveloperResult().convertFrom(developer);
    }

    @Override
    public DeveloperResult getExternalDeveloper(String provider, String subject) {
        return externalRepository.findByProviderAndSubject(provider, subject)
                .map(o -> new DeveloperResult().convertFrom(o.getDeveloper()))
                .orElse(null);
    }

    private String buildExternalName(String provider, String subject) {
        return StrUtil.format("{}_{}", provider, subject);
    }

    @Override
    public void deleteDeveloper(String developerId) {
        eventPublisher.publishEvent(new DeveloperDeletingEvent(developerId));
        externalRepository.deleteByDeveloper_DeveloperId(developerId);
        developerRepository.findByDeveloperId(developerId).ifPresent(developerRepository::delete);
    }

    @Override
    public DeveloperResult getDeveloper(String developerId) {
        Developer developer = findDeveloper(developerId);
        return new DeveloperResult().convertFrom(developer);
    }

    @Override
    public PageResult<DeveloperResult> listDevelopers(QueryDeveloperParam param, Pageable pageable) {
        if (contextHolder.isDeveloper()) {
            param.setPortalId(contextHolder.getPortal());
        }
        Page<Developer> developers = developerRepository.findAll(buildSpecification(param), pageable);
        return new PageResult<DeveloperResult>().convertFrom(developers, developer -> new DeveloperResult().convertFrom(developer));
    }

    @Override
    public void setDeveloperStatus(String developerId, DeveloperStatus status) {
        Developer developer = findDeveloper(developerId);
        developer.setStatus(status);
        developerRepository.save(developer);
    }

    @Override
    public boolean resetPassword(String developerId, String oldPassword, String newPassword) {
        Developer developer = findDeveloper(developerId);

        if (!PasswordHasher.verify(oldPassword, developer.getPasswordHash())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "The username or password you entered is incorrect");
        }

        developer.setPasswordHash(PasswordHasher.hash(newPassword));
        developerRepository.save(developer);
        return true;
    }

    @Override
    public void updateProfile(UpdateDeveloperParam param) {
        Developer developer = findDeveloper(contextHolder.getUser());

        String username = param.getUsername();
        if (username != null && !username.equals(developer.getUsername())) {
            if (developerRepository.findByPortalIdAndUsername(developer.getPortalId(), username).isPresent()) {
                throw new BusinessException(ErrorCode.CONFLICT, StrUtil.format("Developer with name `{}` already exists", username));
            }
        }
        param.update(developer);

        developerRepository.save(developer);
    }

    @EventListener
    @Async("taskExecutor")
    public void handlePortalDeletion(PortalDeletingEvent event) {
        String portalId = event.getPortalId();
        List<Developer> developers = developerRepository.findByPortalId(portalId);
        developers.forEach(developer -> deleteDeveloper(developer.getDeveloperId()));
    }

    private String generateToken(String developerId) {
        return TokenUtil.generateDeveloperToken(developerId);
    }

    private String generateDeveloperId() {
        return IdGenerator.genDeveloperId();
    }

    private Developer findDeveloper(String developerId) {
        return developerRepository.findByDeveloperId(developerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, Resources.DEVELOPER, developerId));
    }

    private Portal findPortal(String portalId) {
        return portalRepository.findByPortalId(portalId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, Resources.PORTAL, portalId));
    }

    private Specification<Developer> buildSpecification(QueryDeveloperParam param) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (StrUtil.isNotBlank(param.getPortalId())) {
                predicates.add(cb.equal(root.get("portalId"), param.getPortalId()));
            }
            if (StrUtil.isNotBlank(param.getUsername())) {
                String likePattern = "%" + param.getUsername() + "%";
                predicates.add(cb.like(root.get("username"), likePattern));
            }
            if (param.getStatus() != null) {
                predicates.add(cb.equal(root.get("status"), param.getStatus()));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    @Override
    public void logout(HttpServletRequest request) {
        TokenUtil.revokeToken(request);
    }

    @Override
    public DeveloperResult getCurrentDeveloperInfo() {
        String currentUserId = contextHolder.getUser();
        Developer developer = findDeveloper(currentUserId);
        return new DeveloperResult().convertFrom(developer);
    }

    @Override
    public boolean changeCurrentDeveloperPassword(String oldPassword, String newPassword) {
        String currentUserId = contextHolder.getUser();
        return resetPassword(currentUserId, oldPassword, newPassword);
    }

    private void createDefaultConsumer(String developerId) {
        consumerService.createConsumerInner(
                CreateConsumerParam.builder()
                        .name("primary-consumer")
                        .description("Developer's primary consumer")
                        .build(),
                developerId
        );
    }
}