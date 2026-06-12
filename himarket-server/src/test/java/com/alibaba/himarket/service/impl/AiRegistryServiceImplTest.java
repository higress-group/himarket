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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.alibaba.himarket.core.exception.BusinessException;
import com.alibaba.himarket.core.security.ContextHolder;
import com.alibaba.himarket.dto.params.airegistry.CreateAiRegistryParam;
import com.alibaba.himarket.dto.result.airegistry.AiRegistryResult;
import com.alibaba.himarket.dto.result.common.PageResult;
import com.alibaba.himarket.entity.AiRegistryInstance;
import com.alibaba.himarket.entity.Product;
import com.alibaba.himarket.repository.AiRegistryInstanceRepository;
import com.alibaba.himarket.repository.ProductRepository;
import com.alibaba.himarket.service.AiRegistrySkillService;
import com.alibaba.himarket.support.enums.ProductType;
import com.alibaba.himarket.support.enums.SkillRegistryType;
import com.alibaba.himarket.support.product.ProductFeature;
import com.alibaba.himarket.support.product.SkillConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

class AiRegistryServiceImplTest {

    private static final String ADMIN_ID = "admin-a";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void adminCanCreateAndListAiRegistryConfigWithoutExposingSecret() throws Exception {
        AiRegistryInstanceRepository repository = mock(AiRegistryInstanceRepository.class);
        ProductRepository productRepository = mock(ProductRepository.class);
        ContextHolder contextHolder = mock(ContextHolder.class);
        AtomicReference<AiRegistryInstance> savedInstance = new AtomicReference<>();
        when(contextHolder.getUser()).thenReturn(ADMIN_ID);
        when(repository.findByNameAndAdminId("prod-registry", ADMIN_ID))
                .thenReturn(Optional.empty());
        when(repository.findByIsDefaultTrueAndAdminId(ADMIN_ID)).thenReturn(Optional.empty());
        when(repository.save(any(AiRegistryInstance.class)))
                .thenAnswer(
                        invocation -> {
                            AiRegistryInstance instance = invocation.getArgument(0);
                            savedInstance.set(instance);
                            return instance;
                        });
        when(repository.findByAdminId(ADMIN_ID, PageRequest.of(0, 10)))
                .thenAnswer(invocation -> new PageImpl<>(java.util.List.of(savedInstance.get())));

        AiRegistryServiceImpl service =
                new AiRegistryServiceImpl(
                        repository,
                        productRepository,
                        mock(AiRegistrySkillService.class),
                        contextHolder);
        CreateAiRegistryParam param = new CreateAiRegistryParam();
        param.setName("prod-registry");
        param.setRegionId("cn-hangzhou");
        param.setNamespaceId("ns-prod");
        param.setAccessKeyId("LTAIExample");
        param.setAccessKeySecret("secret-value");
        param.setSecurityToken("token-value");

        service.createAiRegistryInstance(param);
        PageResult<AiRegistryResult> result =
                service.listAiRegistryInstances(PageRequest.of(0, 10));

        String json = objectMapper.writeValueAsString(result);
        assertTrue(json.contains("prod-registry"));
        assertFalse(json.contains("secret-value"));
        assertFalse(json.contains("token-value"));
        assertFalse(json.contains("LTAIExample"));
        assertFalse(json.contains("accessKeySecret"));
        assertFalse(json.contains("securityToken"));
    }

    @Test
    void createAiRegistryConfigTrimsCredentialFields() {
        AiRegistryInstanceRepository repository = mock(AiRegistryInstanceRepository.class);
        ProductRepository productRepository = mock(ProductRepository.class);
        ContextHolder contextHolder = mock(ContextHolder.class);
        AtomicReference<AiRegistryInstance> savedInstance = new AtomicReference<>();
        when(contextHolder.getUser()).thenReturn(ADMIN_ID);
        when(repository.findByNameAndAdminId("prod-registry", ADMIN_ID))
                .thenReturn(Optional.empty());
        when(repository.findByIsDefaultTrueAndAdminId(ADMIN_ID)).thenReturn(Optional.empty());
        when(repository.save(any(AiRegistryInstance.class)))
                .thenAnswer(
                        invocation -> {
                            AiRegistryInstance instance = invocation.getArgument(0);
                            savedInstance.set(instance);
                            return instance;
                        });

        AiRegistryServiceImpl service =
                new AiRegistryServiceImpl(
                        repository,
                        productRepository,
                        mock(AiRegistrySkillService.class),
                        contextHolder);
        CreateAiRegistryParam param = new CreateAiRegistryParam();
        param.setName(" prod-registry ");
        param.setRegionId(" cn-hangzhou ");
        param.setEndpoint(" ");
        param.setNamespaceId(" ns-prod ");
        param.setAccessKeyId(" LTAIExample ");
        param.setAccessKeySecret(" secret-value ");
        param.setSecurityToken(" token-value ");

        service.createAiRegistryInstance(param);

        AiRegistryInstance instance = savedInstance.get();
        assertEquals("prod-registry", instance.getName());
        assertEquals("cn-hangzhou", instance.getRegionId());
        assertNull(instance.getEndpoint());
        assertEquals("ns-prod", instance.getNamespaceId());
        assertEquals("LTAIExample", instance.getAccessKeyId());
        assertEquals("secret-value", instance.getAccessKeySecret());
        assertEquals("token-value", instance.getSecurityToken());
    }

    @Test
    void adminCannotDeleteAiRegistryConfigReferencedBySkillProduct() {
        AiRegistryInstanceRepository repository = mock(AiRegistryInstanceRepository.class);
        ProductRepository productRepository = mock(ProductRepository.class);
        ContextHolder contextHolder = mock(ContextHolder.class);
        when(contextHolder.getUser()).thenReturn(ADMIN_ID);
        when(repository.findByAiRegistryIdAndAdminId("airegistry-1", ADMIN_ID))
                .thenReturn(
                        Optional.of(
                                AiRegistryInstance.builder()
                                        .aiRegistryId("airegistry-1")
                                        .adminId(ADMIN_ID)
                                        .isDefault(false)
                                        .build()));
        Product product =
                Product.builder()
                        .adminId(ADMIN_ID)
                        .name("weather-skill")
                        .type(ProductType.AGENT_SKILL)
                        .feature(
                                ProductFeature.builder()
                                        .skillConfig(
                                                SkillConfig.builder()
                                                        .registryType(SkillRegistryType.AIREGISTRY)
                                                        .aiRegistryId("airegistry-1")
                                                        .namespace("ns-prod")
                                                        .skillName("weather-skill")
                                                        .build())
                                        .build())
                        .build();
        when(productRepository.findAllByType(ProductType.AGENT_SKILL)).thenReturn(List.of(product));

        AiRegistryServiceImpl service =
                new AiRegistryServiceImpl(
                        repository,
                        productRepository,
                        mock(AiRegistrySkillService.class),
                        contextHolder);

        assertThrows(
                BusinessException.class, () -> service.deleteAiRegistryInstance("airegistry-1"));
    }
}
