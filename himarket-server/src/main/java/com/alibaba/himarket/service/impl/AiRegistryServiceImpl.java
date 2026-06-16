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

import com.alibaba.himarket.core.exception.BusinessException;
import com.alibaba.himarket.core.exception.ErrorCode;
import com.alibaba.himarket.core.security.ContextHolder;
import com.alibaba.himarket.core.utils.IdGenerator;
import com.alibaba.himarket.dto.params.airegistry.CreateAiRegistryParam;
import com.alibaba.himarket.dto.params.airegistry.UpdateAiRegistryParam;
import com.alibaba.himarket.dto.result.airegistry.AiRegistryResult;
import com.alibaba.himarket.dto.result.common.PageResult;
import com.alibaba.himarket.entity.AiRegistryInstance;
import com.alibaba.himarket.entity.Product;
import com.alibaba.himarket.repository.AiRegistryInstanceRepository;
import com.alibaba.himarket.repository.ProductRepository;
import com.alibaba.himarket.service.AiRegistryService;
import com.alibaba.himarket.service.AiRegistrySkillService;
import com.alibaba.himarket.support.common.Strings;
import com.alibaba.himarket.support.enums.ProductType;
import com.alibaba.himarket.support.enums.SkillRegistryType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
public class AiRegistryServiceImpl implements AiRegistryService {

    private static final String AIREGISTRY_RESOURCE = "AiRegistryInstance";

    private final AiRegistryInstanceRepository aiRegistryInstanceRepository;

    private final ProductRepository productRepository;

    private final AiRegistrySkillService aiRegistrySkillService;

    private final ContextHolder contextHolder;

    @Override
    public PageResult<AiRegistryResult> listAiRegistryInstances(Pageable pageable) {
        Page<AiRegistryInstance> instances =
                aiRegistryInstanceRepository.findByAdminId(currentAdmin(), pageable);
        return new PageResult<AiRegistryResult>()
                .convertFrom(instances, instance -> new AiRegistryResult().convertFrom(instance));
    }

    @Override
    public AiRegistryResult getAiRegistryInstance(String airegistryId) {
        return new AiRegistryResult().convertFrom(findAiRegistryInstance(airegistryId));
    }

    @Override
    public AiRegistryResult getDefaultAiRegistryInstance() {
        return aiRegistryInstanceRepository
                .findByIsDefaultTrueAndAdminId(currentAdmin())
                .map(instance -> new AiRegistryResult().convertFrom(instance))
                .orElse(null);
    }

    @Override
    @Transactional
    public void createAiRegistryInstance(CreateAiRegistryParam param) {
        normalize(param);
        String adminId = currentAdmin();
        aiRegistryInstanceRepository
                .findByNameAndAdminId(param.getName(), adminId)
                .ifPresent(
                        instance -> {
                            throw new BusinessException(
                                    ErrorCode.CONFLICT,
                                    AIREGISTRY_RESOURCE
                                            + " already exists, name="
                                            + param.getName());
                        });

        AiRegistryInstance instance = param.convertTo();
        instance.setAiRegistryId(IdGenerator.genAiRegistryId());
        instance.setAdminId(adminId);
        if (aiRegistryInstanceRepository.findByIsDefaultTrueAndAdminId(adminId).isEmpty()) {
            instance.setIsDefault(true);
        }
        aiRegistryInstanceRepository.save(instance);
        log.info("Created AIRegistry config, aiRegistryId={}", instance.getAiRegistryId());
    }

    @Override
    @Transactional
    public void updateAiRegistryInstance(String airegistryId, UpdateAiRegistryParam param) {
        normalize(param);
        AiRegistryInstance instance = findAiRegistryInstance(airegistryId);
        String requestedName = param.getName();
        if (requestedName != null && !requestedName.equals(instance.getName())) {
            AiRegistryInstance existingInstance =
                    aiRegistryInstanceRepository
                            .findByNameAndAdminId(requestedName, currentAdmin())
                            .orElse(null);
            if (existingInstance != null) {
                throw new BusinessException(
                        ErrorCode.CONFLICT,
                        AIREGISTRY_RESOURCE + " already exists, name=" + requestedName);
            }
        }
        param.update(instance);
        aiRegistryInstanceRepository.saveAndFlush(instance);
    }

    @Override
    @Transactional
    public void deleteAiRegistryInstance(String airegistryId) {
        AiRegistryInstance instance = findAiRegistryInstance(airegistryId);
        if (Boolean.TRUE.equals(instance.getIsDefault())) {
            throw new BusinessException(
                    ErrorCode.INVALID_PARAMETER,
                    "Default AIRegistry config cannot be deleted before switching default config");
        }
        long referencedCount = countReferencedSkillProducts(airegistryId);
        if (referencedCount > 0) {
            throw new BusinessException(
                    ErrorCode.INVALID_PARAMETER,
                    "AIRegistry config is referenced by " + referencedCount + " skill product(s)");
        }
        aiRegistryInstanceRepository.delete(instance);
    }

    @Override
    @Transactional
    public void setDefaultAiRegistry(String airegistryId, String namespaceId) {
        String adminId = currentAdmin();
        AiRegistryInstance instance = findAiRegistryInstance(airegistryId);
        aiRegistryInstanceRepository
                .findByIsDefaultTrueAndAdminId(adminId)
                .filter(defaultInstance -> !defaultInstance.getAiRegistryId().equals(airegistryId))
                .ifPresent(
                        defaultInstance -> {
                            defaultInstance.setIsDefault(false);
                            aiRegistryInstanceRepository.save(defaultInstance);
                        });
        if (Strings.isNotBlank(namespaceId)) {
            instance.setNamespaceId(namespaceId);
        }
        instance.setIsDefault(true);
        aiRegistryInstanceRepository.save(instance);
    }

    @Override
    public void validateAiRegistry(String airegistryId, String namespaceId) {
        AiRegistryInstance instance = findAiRegistryInstance(airegistryId);
        String targetNamespace = Strings.blankToDefault(namespaceId, instance.getNamespaceId());
        aiRegistrySkillService.validateNamespace(airegistryId, targetNamespace);
    }

    private AiRegistryInstance findAiRegistryInstance(String airegistryId) {
        return aiRegistryInstanceRepository
                .findByAiRegistryIdAndAdminId(airegistryId, currentAdmin())
                .orElseThrow(
                        () ->
                                new BusinessException(
                                        ErrorCode.NOT_FOUND, AIREGISTRY_RESOURCE, airegistryId));
    }

    private long countReferencedSkillProducts(String airegistryId) {
        String adminId = currentAdmin();
        return productRepository.findAllByType(ProductType.AGENT_SKILL).stream()
                .filter(product -> adminId.equals(product.getAdminId()))
                .filter(product -> isReferencedBy(product, airegistryId))
                .count();
    }

    private boolean isReferencedBy(Product product, String airegistryId) {
        if (product.getFeature() == null || product.getFeature().getSkillConfig() == null) {
            return false;
        }
        return product.getFeature().getSkillConfig().getRegistryType()
                        == SkillRegistryType.AIREGISTRY
                && airegistryId.equals(product.getFeature().getSkillConfig().getAiRegistryId());
    }

    private String currentAdmin() {
        return contextHolder.getUser();
    }

    private void normalize(CreateAiRegistryParam param) {
        param.setName(trimToNull(param.getName()));
        param.setRegionId(trimToNull(param.getRegionId()));
        param.setEndpoint(trimToNull(param.getEndpoint()));
        param.setNamespaceId(trimToNull(param.getNamespaceId()));
        param.setAccessKeyId(trimToNull(param.getAccessKeyId()));
        param.setAccessKeySecret(trimToNull(param.getAccessKeySecret()));
        param.setSecurityToken(trimToNull(param.getSecurityToken()));
        param.setDescription(trimToNull(param.getDescription()));
    }

    private void normalize(UpdateAiRegistryParam param) {
        param.setName(trimToNull(param.getName()));
        param.setRegionId(trimToNull(param.getRegionId()));
        param.setEndpoint(trimToNull(param.getEndpoint()));
        param.setNamespaceId(trimToNull(param.getNamespaceId()));
        param.setAccessKeyId(trimToNull(param.getAccessKeyId()));
        param.setAccessKeySecret(trimToNull(param.getAccessKeySecret()));
        param.setSecurityToken(trimToNull(param.getSecurityToken()));
        param.setDescription(trimToNull(param.getDescription()));
    }

    private String trimToNull(String value) {
        String trimmed = value == null ? null : value.trim();
        return Strings.isBlank(trimmed) ? null : trimmed;
    }
}
