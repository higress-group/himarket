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

import cn.hutool.core.util.StrUtil;
import com.alibaba.himarket.core.exception.BusinessException;
import com.alibaba.himarket.core.exception.ErrorCode;
import com.alibaba.himarket.core.utils.IdGenerator;
import com.alibaba.himarket.dto.params.skill.CreateDeveloperSkillParam;
import com.alibaba.himarket.dto.params.skill.UpdateDeveloperSkillParam;
import com.alibaba.himarket.dto.result.skill.DeveloperSkillResult;
import com.alibaba.himarket.entity.Developer;
import com.alibaba.himarket.entity.NacosInstance;
import com.alibaba.himarket.entity.Product;
import com.alibaba.himarket.repository.DeveloperRepository;
import com.alibaba.himarket.repository.NacosInstanceRepository;
import com.alibaba.himarket.repository.ProductRepository;
import com.alibaba.himarket.service.DeveloperSkillService;
import com.alibaba.himarket.service.PortalService;
import com.alibaba.himarket.service.ProductService;
import com.alibaba.himarket.service.SkillService;
import com.alibaba.himarket.support.enums.ProductStatus;
import com.alibaba.himarket.support.enums.ProductType;
import com.alibaba.himarket.support.product.ProductFeature;
import com.alibaba.himarket.support.product.SkillConfig;
import java.io.IOException;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeveloperSkillServiceImpl implements DeveloperSkillService {

    private final ProductRepository productRepository;
    private final NacosInstanceRepository nacosInstanceRepository;
    private final DeveloperRepository developerRepository;
    private final SkillService skillService;
    private final PortalService portalService;
    private final ProductService productService;

    @Override
    public List<DeveloperSkillResult> listSkills(String developerId, String type, String tag) {
        List<Product> products = queryByType(developerId, type);
        if (StrUtil.isNotBlank(tag)) {
            products =
                    products.stream().filter(p -> containsTag(p, tag)).collect(Collectors.toList());
        }
        return products.stream().map(p -> toResult(p, developerId)).collect(Collectors.toList());
    }

    @Override
    public DeveloperSkillResult createSkill(String developerId, CreateDeveloperSkillParam param) {
        if (productRepository.existsByNameAndType(param.getName(), ProductType.AGENT_SKILL)) {
            throw new BusinessException(ErrorCode.CONFLICT, "Skill 名称已存在: " + param.getName());
        }

        List<String> tags =
                param.getTags() != null ? new ArrayList<>(param.getTags()) : new ArrayList<>();

        Product product =
                Product.builder()
                        .productId(IdGenerator.genApiProductId())
                        .name(param.getName())
                        .description(param.getDescription())
                        .type(ProductType.AGENT_SKILL)
                        .status(ProductStatus.PENDING)
                        .developerId(developerId)
                        .feature(
                                ProductFeature.builder()
                                        .skillConfig(
                                                SkillConfig.builder()
                                                        .skillTags(tags)
                                                        .downloadCount(0L)
                                                        .build())
                                        .build())
                        .build();

        productRepository.save(product);
        return toResult(product, developerId);
    }

    @Override
    public DeveloperSkillResult getSkill(String developerId, String productId) {
        Product product = findSkillOrThrow(productId);
        checkReadAccess(product, developerId);
        return toResult(product, developerId);
    }

    @Override
    public DeveloperSkillResult updateSkill(
            String developerId, String productId, UpdateDeveloperSkillParam param) {
        Product product = findSkillOrThrow(productId);
        checkOwnership(product, developerId);

        if (StrUtil.isNotBlank(param.getName())) {
            product.setName(param.getName());
        }
        if (param.getDescription() != null) {
            product.setDescription(param.getDescription());
        }
        if (param.getTags() != null) {
            SkillConfig config = getOrCreateSkillConfig(product);
            config.setSkillTags(new ArrayList<>(param.getTags()));
            product.getFeature().setSkillConfig(config);
        }

        productRepository.save(product);
        return toResult(product, developerId);
    }

    @Override
    public void deleteSkill(String developerId, String productId) {
        Product product = findSkillOrThrow(productId);
        checkOwnership(product, developerId);
        productRepository.delete(product);
    }

    @Override
    public void uploadPackage(String developerId, String productId, MultipartFile file)
            throws IOException {
        Product product = findSkillOrThrow(productId);
        checkOwnership(product, developerId);

        // 确保 SkillConfig 中有默认 Nacos 实例信息
        SkillConfig config = getOrCreateSkillConfig(product);
        if (StrUtil.isBlank(config.getNacosId())) {
            NacosInstance defaultNacos =
                    nacosInstanceRepository
                            .findByIsDefaultTrue()
                            .orElseThrow(
                                    () ->
                                            new BusinessException(
                                                    ErrorCode.INVALID_REQUEST,
                                                    "No default Nacos instance configured"));
            config.setNacosId(defaultNacos.getNacosId());
            config.setNamespace(defaultNacos.getDefaultNamespace());
            product.getFeature().setSkillConfig(config);
            productRepository.save(product);
        }

        skillService.uploadPackage(productId, file);

        // 上传后自动发布到 online 状态，个人 Skill 无需管理员审核
        skillService.autoPublishLatest(productId);

        // 同步 product 状态为 PUBLISHED
        product = findSkillOrThrow(productId);
        product.setStatus(ProductStatus.PUBLISHED);
        productRepository.save(product);

        // 自动发布到默认门户
        String defaultPortalId = portalService.getDefaultPortal();
        if (defaultPortalId != null) {
            productService.publishProduct(productId, defaultPortalId);
        }
    }

    private List<Product> queryByType(String developerId, String type) {
        if ("personal".equals(type)) {
            return productRepository.findByDeveloperIdAndType(developerId, ProductType.AGENT_SKILL);
        }
        if ("official".equals(type)) {
            return productRepository.findByTypeAndDeveloperIdIsNull(ProductType.AGENT_SKILL);
        }
        // all: official + own + other personal
        List<Product> result = new ArrayList<>();
        result.addAll(productRepository.findByTypeAndDeveloperIdIsNull(ProductType.AGENT_SKILL));
        result.addAll(
                productRepository.findByDeveloperIdAndType(developerId, ProductType.AGENT_SKILL));
        List<Product> others =
                productRepository.findByTypeAndDeveloperIdIsNotNull(ProductType.AGENT_SKILL);
        others.stream().filter(p -> !developerId.equals(p.getDeveloperId())).forEach(result::add);
        return result;
    }

    private boolean containsTag(Product product, String tag) {
        if (product.getFeature() == null
                || product.getFeature().getSkillConfig() == null
                || product.getFeature().getSkillConfig().getSkillTags() == null) {
            return false;
        }
        return product.getFeature().getSkillConfig().getSkillTags().contains(tag);
    }

    private Product findSkillOrThrow(String productId) {
        return productRepository
                .findByProductId(productId)
                .filter(p -> ProductType.AGENT_SKILL.equals(p.getType()))
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Skill", productId));
    }

    private void checkReadAccess(Product product, String developerId) {
        if (product.getDeveloperId() == null) {
            return; // official skill, always readable
        }
        if (developerId.equals(product.getDeveloperId())) {
            return; // owner
        }
        // All personal skills are readable
    }

    private void checkOwnership(Product product, String developerId) {
        if (product.getDeveloperId() == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Cannot modify official skill");
        }
        if (!developerId.equals(product.getDeveloperId())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Access denied");
        }
    }

    private SkillConfig getOrCreateSkillConfig(Product product) {
        if (product.getFeature() == null) {
            product.setFeature(ProductFeature.builder().build());
        }
        if (product.getFeature().getSkillConfig() == null) {
            product.getFeature().setSkillConfig(SkillConfig.builder().build());
        }
        return product.getFeature().getSkillConfig();
    }

    private DeveloperSkillResult toResult(Product product, String currentDeveloperId) {
        List<String> tags = new ArrayList<>();
        if (product.getFeature() != null
                && product.getFeature().getSkillConfig() != null
                && product.getFeature().getSkillConfig().getSkillTags() != null) {
            tags = product.getFeature().getSkillConfig().getSkillTags();
        }

        Long createdAt = null;
        if (product.getCreateAt() != null) {
            createdAt = product.getCreateAt().toInstant(ZoneOffset.UTC).toEpochMilli();
        }

        String developerUsername = null;
        if (product.getDeveloperId() != null) {
            developerUsername =
                    developerRepository
                            .findByDeveloperId(product.getDeveloperId())
                            .map(Developer::getUsername)
                            .orElse(null);
        }

        return DeveloperSkillResult.builder()
                .productId(product.getProductId())
                .name(product.getName())
                .description(product.getDescription())
                .tags(tags)
                .isOwner(currentDeveloperId.equals(product.getDeveloperId()))
                .isOfficial(product.getDeveloperId() == null)
                .status(product.getStatus() != null ? product.getStatus().name() : null)
                .createdAt(createdAt)
                .developerUsername(developerUsername)
                .build();
    }
}
