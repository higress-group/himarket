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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.alibaba.himarket.core.exception.BusinessException;
import com.alibaba.himarket.core.security.ContextHolder;
import com.alibaba.himarket.entity.Product;
import com.alibaba.himarket.repository.ProductRepository;
import com.alibaba.himarket.service.AiRegistrySkillService;
import com.alibaba.himarket.service.NacosService;
import com.alibaba.himarket.support.enums.SkillRegistryType;
import com.alibaba.himarket.support.product.ProductFeature;
import com.alibaba.himarket.support.product.SkillConfig;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.web.multipart.MultipartFile;

class SkillServiceImplAiRegistryUploadTest {

    @Test
    void aiRegistryFirstUploadWritesReturnedSkillNameBackToProductConfig() throws Exception {
        ProductRepository productRepository = mock(ProductRepository.class);
        AiRegistrySkillService aiRegistrySkillService = mock(AiRegistrySkillService.class);
        Product product =
                Product.builder()
                        .productId("product-a")
                        .feature(
                                ProductFeature.builder()
                                        .skillConfig(
                                                SkillConfig.builder()
                                                        .registryType(SkillRegistryType.AIREGISTRY)
                                                        .aiRegistryId("airegistry-prod")
                                                        .namespace("ns-prod")
                                                        .build())
                                        .build())
                        .build();
        byte[] zipBytes = new byte[] {1, 2, 3};
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getSize()).thenReturn((long) zipBytes.length);
        when(file.getOriginalFilename()).thenReturn("skill.zip");
        when(file.getBytes()).thenReturn(zipBytes);
        when(productRepository.findByProductId("product-a")).thenReturn(Optional.of(product));
        when(aiRegistrySkillService.uploadFromZip(
                        "airegistry-prod", "ns-prod", zipBytes, "skill.zip", true))
                .thenReturn("skill-a");

        SkillServiceImpl service =
                new SkillServiceImpl(
                        mock(NacosService.class),
                        productRepository,
                        mock(ContextHolder.class),
                        aiRegistrySkillService);

        service.uploadPackage("product-a", file);

        assertEquals("skill-a", product.getFeature().getSkillConfig().getSkillName());
    }

    @Test
    void aiRegistrySubsequentUploadKeepsExistingProductSkillBinding() throws Exception {
        ProductRepository productRepository = mock(ProductRepository.class);
        AiRegistrySkillService aiRegistrySkillService = mock(AiRegistrySkillService.class);
        Product product =
                Product.builder()
                        .productId("product-a")
                        .name("web-search")
                        .feature(
                                ProductFeature.builder()
                                        .skillConfig(
                                                SkillConfig.builder()
                                                        .registryType(SkillRegistryType.AIREGISTRY)
                                                        .aiRegistryId("airegistry-prod")
                                                        .namespace("ns-prod")
                                                        .skillName("web-search")
                                                        .build())
                                        .build())
                        .build();
        byte[] zipBytes = new byte[] {1, 2, 3};
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getSize()).thenReturn((long) zipBytes.length);
        when(file.getOriginalFilename()).thenReturn("skill.zip");
        when(file.getBytes()).thenReturn(zipBytes);
        when(productRepository.findByProductId("product-a")).thenReturn(Optional.of(product));
        when(aiRegistrySkillService.uploadFromZip(
                        "airegistry-prod", "ns-prod", zipBytes, "skill.zip", true))
                .thenReturn("aone-authored-code-pr-tracker");

        SkillServiceImpl service =
                new SkillServiceImpl(
                        mock(NacosService.class),
                        productRepository,
                        mock(ContextHolder.class),
                        aiRegistrySkillService);

        service.uploadPackage("product-a", file);

        assertEquals("web-search", product.getFeature().getSkillConfig().getSkillName());
        assertEquals("web-search", product.getName());
    }

    @Test
    void uploadFailsWithBusinessExceptionWhenSkillRegistryIsNotConfigured() throws Exception {
        ProductRepository productRepository = mock(ProductRepository.class);
        Product product = Product.builder().productId("product-a").build();
        byte[] zipBytes = new byte[] {1, 2, 3};
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getSize()).thenReturn((long) zipBytes.length);
        when(file.getBytes()).thenReturn(zipBytes);
        when(productRepository.findByProductId("product-a")).thenReturn(Optional.of(product));

        SkillServiceImpl service =
                new SkillServiceImpl(
                        mock(NacosService.class),
                        productRepository,
                        mock(ContextHolder.class),
                        mock(AiRegistrySkillService.class));

        BusinessException exception =
                assertThrows(
                        BusinessException.class, () -> service.uploadPackage("product-a", file));

        assertEquals("INVALID_REQUEST", exception.getCode());
    }
}
