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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.alibaba.himarket.core.security.ContextHolder;
import com.alibaba.himarket.dto.params.product.CreateProductParam;
import com.alibaba.himarket.dto.result.airegistry.AiRegistryResult;
import com.alibaba.himarket.dto.result.product.ProductResult;
import com.alibaba.himarket.dto.result.setting.AdminSettingResult;
import com.alibaba.himarket.entity.Product;
import com.alibaba.himarket.repository.ApiDefinitionRepository;
import com.alibaba.himarket.repository.ConsumerRepository;
import com.alibaba.himarket.repository.ProductPublicationRepository;
import com.alibaba.himarket.repository.ProductRefRepository;
import com.alibaba.himarket.repository.ProductRepository;
import com.alibaba.himarket.repository.SubscriptionRepository;
import com.alibaba.himarket.service.AdminSettingService;
import com.alibaba.himarket.service.AiRegistryService;
import com.alibaba.himarket.service.GatewayService;
import com.alibaba.himarket.service.NacosService;
import com.alibaba.himarket.service.PortalService;
import com.alibaba.himarket.service.ProductCategoryService;
import com.alibaba.himarket.service.SkillService;
import com.alibaba.himarket.service.WorkerService;
import com.alibaba.himarket.service.hichat.manager.ToolManager;
import com.alibaba.himarket.support.enums.ProductType;
import com.alibaba.himarket.support.enums.SkillRegistryType;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class ProductServiceImplAiRegistryDefaultTest {

    private static final String ADMIN_ID = "admin-a";

    @Test
    void createAgentSkillUsesDefaultAiRegistryWhenConfigured() {
        ContextHolder contextHolder = mock(ContextHolder.class);
        ProductRepository productRepository = mock(ProductRepository.class);
        ProductRefRepository productRefRepository = mock(ProductRefRepository.class);
        ProductCategoryService productCategoryService = mock(ProductCategoryService.class);
        AdminSettingService adminSettingService = mock(AdminSettingService.class);
        AiRegistryService aiRegistryService = mock(AiRegistryService.class);
        AtomicReference<Product> savedProduct = new AtomicReference<>();

        when(contextHolder.getUser()).thenReturn(ADMIN_ID);
        when(contextHolder.isAdministrator()).thenReturn(true);
        when(productRepository.findByNameAndAdminId("skill-a", ADMIN_ID))
                .thenReturn(Optional.empty());
        when(productRepository.save(any(Product.class)))
                .thenAnswer(
                        invocation -> {
                            Product product = invocation.getArgument(0);
                            savedProduct.set(product);
                            return product;
                        });
        when(productRepository.findByProductId(any()))
                .thenAnswer(invocation -> Optional.ofNullable(savedProduct.get()));
        when(productRefRepository.findByProductId(any())).thenReturn(Optional.empty());
        when(productRefRepository.findByProductIdIn(anyList())).thenReturn(Collections.emptyList());
        when(productCategoryService.listCategoriesForProducts(anyList()))
                .thenReturn(Collections.emptyMap());
        when(adminSettingService.getSetting("defaultSkillRegistryType"))
                .thenReturn(
                        AdminSettingResult.builder()
                                .settingKey("defaultSkillRegistryType")
                                .settingValue("AIREGISTRY")
                                .build());
        AiRegistryResult defaultAiRegistry = new AiRegistryResult();
        defaultAiRegistry.setAiRegistryId("airegistry-prod");
        defaultAiRegistry.setNamespaceId("ns-prod");
        when(aiRegistryService.getDefaultAiRegistryInstance()).thenReturn(defaultAiRegistry);

        ProductServiceImpl service =
                new ProductServiceImpl(
                        contextHolder,
                        mock(PortalService.class),
                        mock(GatewayService.class),
                        productRepository,
                        productRefRepository,
                        mock(ApiDefinitionRepository.class),
                        mock(ProductPublicationRepository.class),
                        mock(SubscriptionRepository.class),
                        mock(ConsumerRepository.class),
                        mock(NacosService.class),
                        productCategoryService,
                        mock(ToolManager.class),
                        mock(WorkerService.class),
                        mock(SkillService.class),
                        adminSettingService,
                        aiRegistryService);

        ProductResult result =
                service.createProduct(
                        CreateProductParam.builder()
                                .name("skill-a")
                                .type(ProductType.AGENT_SKILL)
                                .build());

        assertEquals(SkillRegistryType.AIREGISTRY, result.getSkillConfig().getRegistryType());
        assertEquals("airegistry-prod", result.getSkillConfig().getAiRegistryId());
        assertEquals("ns-prod", result.getSkillConfig().getNamespace());
        assertNull(result.getSkillConfig().getNacosId());
    }
}
