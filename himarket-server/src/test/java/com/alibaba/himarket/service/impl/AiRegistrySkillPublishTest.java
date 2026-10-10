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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
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
import com.aliyun.airegistry20260317.models.ForcePublishSkillVersionRequest;
import com.aliyun.airegistry20260317.models.GetSkillDetailRequest;
import com.aliyun.airegistry20260317.models.GetSkillDetailResponse;
import com.aliyun.airegistry20260317.models.GetSkillDetailResponseBody;
import com.aliyun.airegistry20260317.models.GetSkillDetailResponseBody.GetSkillDetailResponseBodyData;
import com.aliyun.airegistry20260317.models.GetSkillDetailResponseBody.GetSkillDetailResponseBodyDataVersions;
import com.aliyun.airegistry20260317.models.PublishSkillVersionRequest;
import com.aliyun.airegistry20260317.models.SubmitSkillVersionRequest;
import com.aliyun.airegistry20260317.models.SubmitSkillVersionResponse;
import com.aliyun.airegistry20260317.models.SubmitSkillVersionResponseBody;
import com.aliyun.airegistry20260317.models.UpdateSkillScopeRequest;
import com.aliyun.tea.TeaException;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

class AiRegistrySkillPublishTest {

    private Client client;
    private AiRegistrySkillServiceImpl service;

    @BeforeEach
    void setUp() throws Exception {
        AiRegistryInstanceRepository repository = mock(AiRegistryInstanceRepository.class);
        AiRegistryInstance instance =
                AiRegistryInstance.builder().aiRegistryId("airegistry-1").build();
        client = mock(Client.class);
        service = spy(new AiRegistrySkillServiceImpl(repository));
        when(repository.findByAiRegistryId("airegistry-1")).thenReturn(Optional.of(instance));
        doReturn(client).when(service).buildClient(instance);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void publishesVersionBeforeSettingSkillPublic(boolean force) throws Exception {
        publish(force);

        InOrder order = inOrder(client);
        if (force) {
            ArgumentCaptor<ForcePublishSkillVersionRequest> request =
                    ArgumentCaptor.forClass(ForcePublishSkillVersionRequest.class);
            order.verify(client).forcePublishSkillVersion(request.capture());
            assertEquals("namespace-1", request.getValue().getNamespaceId());
            assertEquals("test-skill", request.getValue().getSkillName());
            assertEquals("1.0.0", request.getValue().getSkillVersion());
            assertEquals(false, request.getValue().getUpdateLatestLabel());
        } else {
            ArgumentCaptor<PublishSkillVersionRequest> request =
                    ArgumentCaptor.forClass(PublishSkillVersionRequest.class);
            order.verify(client).publishSkillVersion(request.capture());
            assertEquals("namespace-1", request.getValue().getNamespaceId());
            assertEquals("test-skill", request.getValue().getSkillName());
            assertEquals("1.0.0", request.getValue().getSkillVersion());
            assertEquals(false, request.getValue().getUpdateLatestLabel());
        }
        ArgumentCaptor<UpdateSkillScopeRequest> scope =
                ArgumentCaptor.forClass(UpdateSkillScopeRequest.class);
        order.verify(client).updateSkillScope(scope.capture());
        assertEquals("namespace-1", scope.getValue().getNamespaceId());
        assertEquals("test-skill", scope.getValue().getSkillName());
        assertEquals("PUBLIC", scope.getValue().getScope());
        order.verifyNoMoreInteractions();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void publishFailureDoesNotChangeScope(boolean force) throws Exception {
        if (force) {
            doThrow(new IllegalStateException("Publish failed"))
                    .when(client)
                    .forcePublishSkillVersion(any(ForcePublishSkillVersionRequest.class));
        } else {
            doThrow(new IllegalStateException("Publish failed"))
                    .when(client)
                    .publishSkillVersion(any(PublishSkillVersionRequest.class));
        }

        BusinessException exception = assertThrows(BusinessException.class, () -> publish(force));

        assertEquals("INTERNAL_ERROR", exception.getCode());
        assertTrue(exception.getMessage().contains("Failed to"));
        verify(client, never()).updateSkillScope(any(UpdateSkillScopeRequest.class));
    }

    @ParameterizedTest
    @CsvSource({"false,403", "true,403", "false,500", "true,500", "false,0", "true,0"})
    void scopeFailureDoesNotFailPublishing(boolean force, int status) throws Exception {
        TeaException error = new TeaException();
        error.setCode(status == 403 ? "NoPermission" : "InternalError");
        error.setStatusCode(status);
        error.setMessage("Scope update failed");
        doThrow(status == 0 ? new IOException("Connection timed out") : error)
                .when(client)
                .updateSkillScope(any(UpdateSkillScopeRequest.class));

        assertDoesNotThrow(() -> publish(force));
        InOrder order = inOrder(client);
        if (force) {
            order.verify(client)
                    .forcePublishSkillVersion(any(ForcePublishSkillVersionRequest.class));
        } else {
            order.verify(client).publishSkillVersion(any(PublishSkillVersionRequest.class));
        }
        order.verify(client).updateSkillScope(any(UpdateSkillScopeRequest.class));
        order.verifyNoMoreInteractions();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void scopeFailureDoesNotPreventPublishedProductStatusSync(boolean force) throws Exception {
        SkillConfig config =
                SkillConfig.builder()
                        .registryType(SkillRegistryType.AIREGISTRY)
                        .aiRegistryId("airegistry-1")
                        .namespace("namespace-1")
                        .skillName("test-skill")
                        .build();
        Product product =
                Product.builder()
                        .productId("product-1")
                        .status(ProductStatus.PENDING)
                        .feature(ProductFeature.builder().skillConfig(config).build())
                        .build();
        ProductRepository products = mock(ProductRepository.class);
        when(products.findByProductId("product-1")).thenReturn(Optional.of(product));
        ContextHolder context = mock(ContextHolder.class);
        when(context.isAdministrator()).thenReturn(true);
        SkillServiceImpl skills =
                new SkillServiceImpl(mock(NacosService.class), products, context, service);
        GetSkillDetailResponseBodyDataVersions remoteVersion =
                new GetSkillDetailResponseBodyDataVersions()
                        .setVersion("1.0.0")
                        .setStatus("reviewed")
                        .setPublishPipelineInfo(
                                force ? "{\"status\":\"REJECTED\"}" : "{\"status\":\"APPROVED\"}");
        when(client.getSkillDetail(any(GetSkillDetailRequest.class)))
                .thenReturn(
                        new GetSkillDetailResponse()
                                .setBody(
                                        new GetSkillDetailResponseBody()
                                                .setData(
                                                        new GetSkillDetailResponseBodyData()
                                                                .setVersions(
                                                                        List.of(remoteVersion)))));
        if (force) {
            when(client.forcePublishSkillVersion(any(ForcePublishSkillVersionRequest.class)))
                    .thenAnswer(
                            call -> {
                                remoteVersion.setStatus("online");
                                return null;
                            });
        } else {
            when(client.publishSkillVersion(any(PublishSkillVersionRequest.class)))
                    .thenAnswer(
                            call -> {
                                remoteVersion.setStatus("online");
                                return null;
                            });
        }
        TeaException error = new TeaException();
        error.setCode("NoPermission");
        error.setStatusCode(403);
        doThrow(error).when(client).updateSkillScope(any(UpdateSkillScopeRequest.class));
        UpdateSkillVersionParam param = new UpdateSkillVersionParam();
        param.setStatus("online");
        param.setForce(force);

        skills.updateVersion("product-1", "1.0.0", param);

        assertEquals(ProductStatus.READY, product.getStatus());
        verify(products).save(product);
        verify(client).updateSkillScope(any(UpdateSkillScopeRequest.class));
    }

    @Test
    void otherLifecycleOperationsDoNotChangeScope() throws Exception {
        when(client.submitSkillVersion(any(SubmitSkillVersionRequest.class)))
                .thenReturn(
                        new SubmitSkillVersionResponse()
                                .setBody(
                                        new SubmitSkillVersionResponseBody().setData("reviewing")));

        service.submit("airegistry-1", "namespace-1", "test-skill", "1.0.0");
        service.redraft("airegistry-1", "namespace-1", "test-skill", "1.0.0");
        service.changeVersionStatus("airegistry-1", "namespace-1", "test-skill", "1.0.0", true);
        service.changeVersionStatus("airegistry-1", "namespace-1", "test-skill", "1.0.0", false);

        verify(client, never()).updateSkillScope(any(UpdateSkillScopeRequest.class));
    }

    private void publish(boolean force) {
        if (force) {
            service.forcePublish("airegistry-1", "namespace-1", "test-skill", "1.0.0", false);
        } else {
            service.publish("airegistry-1", "namespace-1", "test-skill", "1.0.0", false);
        }
    }
}
