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

import com.alibaba.himarket.core.constant.Resources;
import com.alibaba.himarket.core.exception.BusinessException;
import com.alibaba.himarket.core.exception.ErrorCode;
import com.alibaba.himarket.entity.Product;
import com.alibaba.himarket.repository.ProductPublicationRepository;
import com.alibaba.himarket.repository.ProductRepository;
import com.alibaba.himarket.service.SkillService;
import com.alibaba.himarket.support.enums.ProductType;
import com.alibaba.himarket.support.product.ProductFeature;
import com.alibaba.himarket.support.product.SkillConfig;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional
public class SkillServiceImpl implements SkillService {

    private final ProductRepository productRepository;

    private final ProductPublicationRepository publicationRepository;

    @Override
    public String downloadSkill(String productId) {
        // 查询产品
        Product product =
                productRepository
                        .findByProductId(productId)
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                ErrorCode.NOT_FOUND, Resources.PRODUCT, productId));

        // 校验产品类型为 AGENT_SKILL
        if (product.getType() != ProductType.AGENT_SKILL) {
            throw new BusinessException(ErrorCode.NOT_FOUND, Resources.PRODUCT, productId);
        }

        // 校验产品已发布（存在至少一条发布记录）
        if (!publicationRepository.existsByProductId(productId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, Resources.PRODUCT, productId);
        }

        // 递增下载计数
        incrementDownloadCount(product);

        return product.getDocument();
    }

    private void incrementDownloadCount(Product product) {
        ProductFeature feature = product.getFeature();
        if (feature == null) {
            feature = new ProductFeature();
        }

        SkillConfig skillConfig = feature.getSkillConfig();
        if (skillConfig == null) {
            skillConfig = new SkillConfig();
        }

        Long currentCount = skillConfig.getDownloadCount();
        skillConfig.setDownloadCount(currentCount == null ? 1L : currentCount + 1);

        feature.setSkillConfig(skillConfig);
        product.setFeature(feature);
        productRepository.save(product);
    }
}
