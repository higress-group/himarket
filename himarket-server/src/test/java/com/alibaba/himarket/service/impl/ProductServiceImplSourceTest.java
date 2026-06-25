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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.alibaba.himarket.core.exception.BusinessException;
import com.alibaba.himarket.core.security.ContextHolder;
import com.alibaba.himarket.dto.params.product.UpdateProductSourceParam;
import com.alibaba.himarket.dto.result.airegistry.AiRegistryResult;
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
import com.alibaba.himarket.service.McpToolService;
import com.alibaba.himarket.service.NacosService;
import com.alibaba.himarket.service.PortalService;
import com.alibaba.himarket.service.ProductCategoryService;
import com.alibaba.himarket.service.SkillService;
import com.alibaba.himarket.service.WorkerService;
import com.alibaba.himarket.support.enums.ProductType;
import com.alibaba.himarket.support.enums.SkillRegistryType;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class ProductServiceImplSourceTest {

    @Test
    void agentSkillCanSwitchToAiRegistrySource() {
        ProductRepository productRepository = mock(ProductRepository.class);
        AiRegistryService aiRegistryService = mock(AiRegistryService.class);
        Product product =
                Product.builder().productId("product-a").type(ProductType.AGENT_SKILL).build();
        when(productRepository.findByProductId("product-a")).thenReturn(Optional.of(product));
        when(productRepository.save(any(Product.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        AiRegistryResult aiRegistry = new AiRegistryResult();
        aiRegistry.setAiRegistryId("airegistry-prod");
        when(aiRegistryService.getAiRegistryInstance("airegistry-prod")).thenReturn(aiRegistry);

        ProductServiceImpl service = newService(productRepository, aiRegistryService);
        UpdateProductSourceParam param = new UpdateProductSourceParam();
        param.setRegistryType(SkillRegistryType.AIREGISTRY);
        param.setAiRegistryId("airegistry-prod");
        param.setNamespace("ns-prod");

        service.updateProductSource("product-a", param);

        assertEquals(
                SkillRegistryType.AIREGISTRY,
                product.getFeature().getSkillConfig().getRegistryType());
        assertEquals("airegistry-prod", product.getFeature().getSkillConfig().getAiRegistryId());
        assertEquals("ns-prod", product.getFeature().getSkillConfig().getNamespace());
        assertNull(product.getFeature().getSkillConfig().getNacosId());
    }

    @Test
    void workerCannotSwitchToAiRegistrySource() {
        ProductRepository productRepository = mock(ProductRepository.class);
        Product product = Product.builder().productId("worker-a").type(ProductType.WORKER).build();
        when(productRepository.findByProductId("worker-a")).thenReturn(Optional.of(product));
        ProductServiceImpl service = newService(productRepository, mock(AiRegistryService.class));
        UpdateProductSourceParam param = new UpdateProductSourceParam();
        param.setRegistryType(SkillRegistryType.AIREGISTRY);
        param.setAiRegistryId("airegistry-prod");
        param.setNamespace("ns-prod");

        assertThrows(BusinessException.class, () -> service.updateProductSource("worker-a", param));
    }

    private ProductServiceImpl newService(
            ProductRepository productRepository, AiRegistryService aiRegistryService) {
        return new ProductServiceImpl(
                mock(ContextHolder.class),
                mock(PortalService.class),
                mock(GatewayService.class),
                productRepository,
                mock(ProductRefRepository.class),
                mock(ApiDefinitionRepository.class),
                mock(ProductPublicationRepository.class),
                mock(SubscriptionRepository.class),
                mock(ConsumerRepository.class),
                mock(NacosService.class),
                mock(ProductCategoryService.class),
                mock(McpToolService.class),
                mock(WorkerService.class),
                mock(SkillService.class),
                mock(AdminSettingService.class),
                aiRegistryService,
                mock(ApplicationEventPublisher.class));
    }
}
