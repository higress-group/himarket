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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.alibaba.himarket.core.exception.BusinessException;
import com.alibaba.himarket.core.security.ContextHolder;
import com.alibaba.himarket.dto.params.skill.UpdateSkillVersionParam;
import com.alibaba.himarket.entity.AiRegistryInstance;
import com.alibaba.himarket.entity.Product;
import com.alibaba.himarket.repository.AiRegistryInstanceRepository;
import com.alibaba.himarket.repository.ProductRepository;
import com.alibaba.himarket.service.NacosService;
import com.alibaba.himarket.support.enums.ProductStatus;
import com.alibaba.himarket.support.enums.SkillRegistryType;
import com.alibaba.himarket.support.product.ProductFeature;
import com.alibaba.himarket.support.product.SkillConfig;
import com.aliyun.airegistry20260317.Client;
import com.aliyun.airegistry20260317.models.GetSkillDetailRequest;
import com.aliyun.airegistry20260317.models.GetSkillDetailResponse;
import com.aliyun.airegistry20260317.models.GetSkillDetailResponseBody;
import com.aliyun.airegistry20260317.models.GetSkillDetailResponseBody.GetSkillDetailResponseBodyDataVersions;
import com.aliyun.airegistry20260317.models.RedraftSkillVersionRequest;
import com.aliyun.tea.TeaException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

class SkillRedraftTest {

    private Client client;
    private SkillServiceImpl service;
    private Product product;
    private GetSkillDetailResponseBodyDataVersions remoteVersion;

    @BeforeEach
    void setUp() throws Exception {
        client = mock(Client.class);
        AiRegistryInstanceRepository registryRepository = mock(AiRegistryInstanceRepository.class);
        AiRegistryInstance instance =
                AiRegistryInstance.builder().aiRegistryId("registry-a").build();
        when(registryRepository.findByAiRegistryId("registry-a")).thenReturn(Optional.of(instance));
        AiRegistrySkillServiceImpl registry =
                spy(new AiRegistrySkillServiceImpl(registryRepository));
        doReturn(client).when(registry).buildClient(instance);
        product =
                Product.builder()
                        .productId("product-a")
                        .status(ProductStatus.PENDING)
                        .feature(
                                ProductFeature.builder()
                                        .skillConfig(
                                                SkillConfig.builder()
                                                        .registryType(SkillRegistryType.AIREGISTRY)
                                                        .aiRegistryId("registry-a")
                                                        .namespace("namespace-a")
                                                        .skillName("skill-a")
                                                        .build())
                                        .build())
                        .build();
        ProductRepository products = mock(ProductRepository.class);
        when(products.findByProductId("product-a")).thenReturn(Optional.of(product));
        ContextHolder context = mock(ContextHolder.class);
        when(context.isAdministrator()).thenReturn(true);
        service = new SkillServiceImpl(mock(NacosService.class), products, context, registry);
        remoteVersion =
                new GetSkillDetailResponseBodyDataVersions()
                        .setVersion("1.0.0")
                        .setStatus("reviewed");
        when(client.getSkillDetail(any(GetSkillDetailRequest.class)))
                .thenReturn(
                        new GetSkillDetailResponse()
                                .setBody(
                                        new GetSkillDetailResponseBody()
                                                .setData(
                                                        new GetSkillDetailResponseBody
                                                                        .GetSkillDetailResponseBodyData()
                                                                .setVersions(
                                                                        List.of(remoteVersion)))));
    }

    @ParameterizedTest
    @ValueSource(strings = {"APPROVED", "REJECTED"})
    void reviewedVersionCanReturnToDraftThroughSdk(String reviewStatus) throws Exception {
        remoteVersion.setPublishPipelineInfo("{\"status\":\"" + reviewStatus + "\"}");
        assertEquals(Boolean.TRUE, service.listVersions("product-a").get(0).getCanRedraft());
        when(client.redraftSkillVersion(any(RedraftSkillVersionRequest.class)))
                .thenAnswer(
                        call -> {
                            remoteVersion
                                    .setStatus("draft")
                                    .setPublishPipelineInfo(
                                            "{\"status\":\""
                                                    + reviewStatus
                                                    + "\",\"historical\":true}");
                            return null;
                        });

        redraft();

        ArgumentCaptor<RedraftSkillVersionRequest> request =
                ArgumentCaptor.forClass(RedraftSkillVersionRequest.class);
        verify(client).redraftSkillVersion(request.capture());
        assertEquals("namespace-a", request.getValue().getNamespaceId());
        assertEquals("skill-a", request.getValue().getSkillName());
        assertEquals("1.0.0", request.getValue().getSkillVersion());
        assertEquals("draft", service.listVersions("product-a").get(0).getStatus());
        assertEquals(Boolean.FALSE, service.listVersions("product-a").get(0).getCanRedraft());
    }

    @ParameterizedTest
    @ValueSource(strings = {"reviewing", "online", "offline"})
    void redraftRejectsVersionsOutsideReviewResultState(String status) throws Exception {
        remoteVersion.setStatus(status);
        assertEquals(Boolean.FALSE, service.listVersions("product-a").get(0).getCanRedraft());

        BusinessException exception = assertThrows(BusinessException.class, this::redraft);

        assertEquals("INVALID_REQUEST", exception.getCode());
        verify(client, never()).redraftSkillVersion(any(RedraftSkillVersionRequest.class));
    }

    @Test
    void legacyRejectedDraftDoesNotCallRedraft() throws Exception {
        remoteVersion.setStatus("draft").setPublishPipelineInfo("{\"status\":\"REJECTED\"}");
        assertEquals(Boolean.FALSE, service.listVersions("product-a").get(0).getCanRedraft());

        redraft();

        verify(client, never()).redraftSkillVersion(any(RedraftSkillVersionRequest.class));
        assertEquals("draft", service.listVersions("product-a").get(0).getStatus());
    }

    @ParameterizedTest
    @ValueSource(strings = {"APPROVED", "REJECTED"})
    void legacyReviewResultDoesNotCallRedraft(String reviewStatus) throws Exception {
        remoteVersion
                .setStatus("reviewing")
                .setPublishPipelineInfo("{\"status\":\"" + reviewStatus + "\"}");
        assertEquals(
                "APPROVED".equals(reviewStatus) ? "approved" : "rejected",
                service.listVersions("product-a").get(0).getStatus());
        assertEquals(Boolean.FALSE, service.listVersions("product-a").get(0).getCanRedraft());

        BusinessException exception = assertThrows(BusinessException.class, this::redraft);

        assertEquals("INVALID_REQUEST", exception.getCode());
        verify(client, never()).redraftSkillVersion(any(RedraftSkillVersionRequest.class));
    }

    @Test
    void reviewedVersionWithoutPipelineDetailsCanReturnToDraft() throws Exception {
        assertEquals(Boolean.TRUE, service.listVersions("product-a").get(0).getCanRedraft());
        redraft();

        verify(client).redraftSkillVersion(any(RedraftSkillVersionRequest.class));
    }

    @Test
    void missingVersionDoesNotCallRedraft() throws Exception {
        remoteVersion.setVersion("2.0.0");

        BusinessException exception = assertThrows(BusinessException.class, this::redraft);

        assertEquals("NOT_FOUND", exception.getCode());
        verify(client, never()).redraftSkillVersion(any(RedraftSkillVersionRequest.class));
    }

    @Test
    void remoteConflictIsPreservedAndVersionRemainsReviewed() throws Exception {
        TeaException conflict = new TeaException();
        conflict.setCode("ResourceAlreadyExist");
        conflict.setStatusCode(409);
        conflict.setMessage("There is already an editing version");
        when(client.redraftSkillVersion(any(RedraftSkillVersionRequest.class))).thenThrow(conflict);

        BusinessException exception = assertThrows(BusinessException.class, this::redraft);

        assertEquals("CONFLICT", exception.getCode());
        assertEquals("reviewed", service.listVersions("product-a").get(0).getStatus());
    }

    @Test
    void nacosDoesNotCallAiRegistryRedraft() throws Exception {
        product.getFeature().getSkillConfig().setRegistryType(SkillRegistryType.NACOS);

        BusinessException exception = assertThrows(BusinessException.class, this::redraft);

        assertEquals("INVALID_REQUEST", exception.getCode());
        verify(client, never()).redraftSkillVersion(any(RedraftSkillVersionRequest.class));
    }

    private void redraft() {
        UpdateSkillVersionParam param = new UpdateSkillVersionParam();
        param.setStatus("draft");
        service.updateVersion("product-a", "1.0.0", param);
    }
}
