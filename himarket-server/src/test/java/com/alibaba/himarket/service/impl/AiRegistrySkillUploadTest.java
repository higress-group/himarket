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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
import com.alibaba.himarket.entity.AiRegistryInstance;
import com.alibaba.himarket.entity.Product;
import com.alibaba.himarket.repository.AiRegistryInstanceRepository;
import com.alibaba.himarket.repository.ProductRepository;
import com.alibaba.himarket.service.NacosService;
import com.alibaba.himarket.support.enums.ProductType;
import com.alibaba.himarket.support.enums.SkillRegistryType;
import com.alibaba.himarket.support.product.ProductFeature;
import com.alibaba.himarket.support.product.SkillConfig;
import com.aliyun.airegistry20260317.Client;
import com.aliyun.airegistry20260317.models.GetSkillImportFileUrlRequest;
import com.aliyun.airegistry20260317.models.GetSkillImportFileUrlResponse;
import com.aliyun.airegistry20260317.models.GetSkillImportFileUrlResponseBody;
import com.aliyun.airegistry20260317.models.GetSkillImportFileUrlResponseBody.GetSkillImportFileUrlResponseBodyData;
import com.aliyun.airegistry20260317.models.UpdateSkillScopeRequest;
import com.aliyun.airegistry20260317.models.UploadSkillViaOssRequest;
import com.aliyun.airegistry20260317.models.UploadSkillViaOssResponse;
import com.aliyun.airegistry20260317.models.UploadSkillViaOssResponseBody;
import com.aliyun.tea.TeaException;
import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.mock.web.MockMultipartFile;

class AiRegistrySkillUploadTest {

    private Client client;
    private AiRegistrySkillServiceImpl service;
    private HttpServer oss;
    private final AtomicReference<byte[]> uploadedBytes = new AtomicReference<>();
    private final AtomicInteger ossStatus = new AtomicInteger(200);

    @BeforeEach
    void setUp() throws Exception {
        oss = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        oss.createContext(
                "/upload",
                exchange -> {
                    uploadedBytes.set(exchange.getRequestBody().readAllBytes());
                    exchange.sendResponseHeaders(ossStatus.get(), -1);
                    exchange.close();
                });
        oss.start();

        AiRegistryInstanceRepository repository = mock(AiRegistryInstanceRepository.class);
        AiRegistryInstance instance =
                AiRegistryInstance.builder().aiRegistryId("airegistry-1").build();
        client = mock(Client.class);
        service = spy(new AiRegistrySkillServiceImpl(repository));
        when(repository.findByAiRegistryId("airegistry-1")).thenReturn(Optional.of(instance));
        doReturn(client).when(service).buildClient(instance);
        when(client.getSkillImportFileUrl(any(GetSkillImportFileUrlRequest.class)))
                .thenReturn(
                        new GetSkillImportFileUrlResponse()
                                .setBody(
                                        new GetSkillImportFileUrlResponseBody()
                                                .setData(
                                                        new GetSkillImportFileUrlResponseBodyData()
                                                                .setUploadUrl(
                                                                        "http://127.0.0.1:"
                                                                                + oss.getAddress()
                                                                                        .getPort()
                                                                                + "/upload")
                                                                .setOssObjectName(
                                                                        "imports/skill.zip"))));
    }

    @AfterEach
    void tearDown() {
        oss.stop(0);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void uploadSetsReturnedSkillPublicAfterImport(boolean overwrite) throws Exception {
        when(client.uploadSkillViaOss(any(UploadSkillViaOssRequest.class)))
                .thenReturn(
                        new UploadSkillViaOssResponse()
                                .setBody(
                                        new UploadSkillViaOssResponseBody()
                                                .setData("returned-skill")));
        byte[] zip = {1, 2, 3};

        String skillName =
                service.uploadFromZip("airegistry-1", "namespace-1", zip, "other.zip", overwrite);

        assertEquals("returned-skill", skillName);
        assertArrayEquals(zip, uploadedBytes.get());
        InOrder order = inOrder(client);
        order.verify(client).getSkillImportFileUrl(any(GetSkillImportFileUrlRequest.class));
        ArgumentCaptor<UploadSkillViaOssRequest> upload =
                ArgumentCaptor.forClass(UploadSkillViaOssRequest.class);
        order.verify(client).uploadSkillViaOss(upload.capture());
        assertEquals("namespace-1", upload.getValue().getNamespaceId());
        assertEquals("imports/skill.zip", upload.getValue().getOssObjectName());
        assertEquals(overwrite, upload.getValue().getOverwrite());
        ArgumentCaptor<UpdateSkillScopeRequest> scope =
                ArgumentCaptor.forClass(UpdateSkillScopeRequest.class);
        order.verify(client).updateSkillScope(scope.capture());
        assertEquals("namespace-1", scope.getValue().getNamespaceId());
        assertEquals("returned-skill", scope.getValue().getSkillName());
        assertEquals("PUBLIC", scope.getValue().getScope());
        order.verifyNoMoreInteractions();
    }

    @Test
    void ossUploadFailureDoesNotImportOrChangeScope() throws Exception {
        ossStatus.set(500);

        assertThrows(
                BusinessException.class,
                () ->
                        service.uploadFromZip(
                                "airegistry-1", "namespace-1", new byte[] {1}, "skill.zip", false));

        verify(client, never()).uploadSkillViaOss(any(UploadSkillViaOssRequest.class));
        verify(client, never()).updateSkillScope(any(UpdateSkillScopeRequest.class));
    }

    @Test
    void importFailureDoesNotChangeScope() throws Exception {
        TeaException error = new TeaException();
        error.setCode("ResourceAlreadyExist");
        error.setStatusCode(409);
        error.setMessage("There is already a reviewing version");
        doThrow(error).when(client).uploadSkillViaOss(any(UploadSkillViaOssRequest.class));

        BusinessException exception =
                assertThrows(
                        BusinessException.class,
                        () ->
                                service.uploadFromZip(
                                        "airegistry-1",
                                        "namespace-1",
                                        new byte[] {1},
                                        "skill.zip",
                                        true));

        assertEquals("CONFLICT", exception.getCode());
        verify(client, never()).updateSkillScope(any(UpdateSkillScopeRequest.class));
    }

    @Test
    void scopeFailureDoesNotPreventFirstUploadBinding() throws Exception {
        when(client.uploadSkillViaOss(any(UploadSkillViaOssRequest.class)))
                .thenReturn(
                        new UploadSkillViaOssResponse()
                                .setBody(
                                        new UploadSkillViaOssResponseBody()
                                                .setData("returned-skill")));
        TeaException error = new TeaException();
        error.setCode("NoPermission");
        error.setStatusCode(403);
        doThrow(error).when(client).updateSkillScope(any(UpdateSkillScopeRequest.class));
        SkillConfig config =
                SkillConfig.builder()
                        .registryType(SkillRegistryType.AIREGISTRY)
                        .aiRegistryId("airegistry-1")
                        .namespace("namespace-1")
                        .build();
        Product product =
                Product.builder()
                        .productId("product-1")
                        .type(ProductType.AGENT_SKILL)
                        .feature(ProductFeature.builder().skillConfig(config).build())
                        .build();
        ProductRepository products = mock(ProductRepository.class);
        when(products.findByProductId("product-1")).thenReturn(Optional.of(product));
        when(products.findAllByType(ProductType.AGENT_SKILL)).thenReturn(List.of(product));
        SkillServiceImpl skills =
                new SkillServiceImpl(
                        mock(NacosService.class), products, mock(ContextHolder.class), service);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry("SKILL.md"));
            zip.write("---\nname: returned-skill\n---\n# Skill".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }

        skills.uploadPackage(
                "product-1",
                new MockMultipartFile("file", "skill.zip", "application/zip", bytes.toByteArray()));

        assertEquals("returned-skill", config.getSkillName());
        verify(products).save(product);
        verify(client).updateSkillScope(any(UpdateSkillScopeRequest.class));
    }

    @ParameterizedTest
    @ValueSource(ints = {403, 500, 0})
    void scopeFailureStillReturnsUploadedSkillName(int status) throws Exception {
        when(client.uploadSkillViaOss(any(UploadSkillViaOssRequest.class)))
                .thenReturn(
                        new UploadSkillViaOssResponse()
                                .setBody(
                                        new UploadSkillViaOssResponseBody()
                                                .setData("returned-skill")));
        TeaException error = new TeaException();
        error.setCode(status == 403 ? "NoPermission" : "InternalError");
        error.setStatusCode(status);
        error.setMessage("Scope update failed");
        doThrow(status == 0 ? new IOException("Connection timed out") : error)
                .when(client)
                .updateSkillScope(any(UpdateSkillScopeRequest.class));

        String skillName =
                service.uploadFromZip(
                        "airegistry-1", "namespace-1", new byte[] {1}, "skill.zip", false);

        assertEquals("returned-skill", skillName);
        InOrder order = inOrder(client);
        order.verify(client).getSkillImportFileUrl(any(GetSkillImportFileUrlRequest.class));
        order.verify(client).uploadSkillViaOss(any(UploadSkillViaOssRequest.class));
        order.verify(client).updateSkillScope(any(UpdateSkillScopeRequest.class));
        order.verifyNoMoreInteractions();
    }
}
