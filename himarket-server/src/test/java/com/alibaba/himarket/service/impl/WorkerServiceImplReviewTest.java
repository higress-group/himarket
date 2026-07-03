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
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.alibaba.himarket.core.exception.BusinessException;
import com.alibaba.himarket.core.security.ContextHolder;
import com.alibaba.himarket.dto.params.worker.CreateWorkerDraftParam;
import com.alibaba.himarket.dto.params.worker.UpdateWorkerDraftParam;
import com.alibaba.himarket.dto.params.worker.UpdateWorkerVersionParam;
import com.alibaba.himarket.dto.result.common.VersionResult;
import com.alibaba.himarket.dto.result.common.WorkerDraftResult;
import com.alibaba.himarket.entity.Product;
import com.alibaba.himarket.repository.ProductRepository;
import com.alibaba.himarket.service.NacosService;
import com.alibaba.himarket.support.product.ProductFeature;
import com.alibaba.himarket.support.product.WorkerConfig;
import com.alibaba.himarket.utils.JsonUtil;
import com.alibaba.nacos.api.ai.model.agentspecs.AgentSpec;
import com.alibaba.nacos.api.ai.model.agentspecs.AgentSpecMeta;
import com.alibaba.nacos.maintainer.client.ai.AgentSpecMaintainerService;
import com.alibaba.nacos.maintainer.client.ai.AiMaintainerService;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class WorkerServiceImplReviewTest {

    private static final String PRODUCT_ID = "product-a";
    private static final String VERSION = "1.0.0";
    private static final String NAMESPACE = "ns-prod";
    private static final String AGENT_SPEC_NAME = "worker-a";

    private NacosService nacosService;
    private ProductRepository productRepository;
    private ContextHolder contextHolder;
    private WorkerServiceImpl service;

    @BeforeEach
    void setUp() {
        nacosService = mock(NacosService.class);
        productRepository = mock(ProductRepository.class);
        contextHolder = mock(ContextHolder.class);
        service = new WorkerServiceImpl(nacosService, productRepository, contextHolder);
        when(contextHolder.isAdministrator()).thenReturn(true);
    }

    @Test
    void updateVersionWhenTargetIsReviewingSubmitsOnly() throws Exception {
        Product product = workerProduct();
        AgentSpecMaintainerService agentSpecMaintainerService = mockAgentSpecMaintainer();
        when(productRepository.findByProductId(PRODUCT_ID)).thenReturn(Optional.of(product));
        when(agentSpecMaintainerService.submit(NAMESPACE, AGENT_SPEC_NAME, VERSION))
                .thenReturn(VERSION);

        service.updateVersion(PRODUCT_ID, VERSION, statusUpdate("reviewing"));

        verify(agentSpecMaintainerService).submit(NAMESPACE, AGENT_SPEC_NAME, VERSION);
        verify(agentSpecMaintainerService, never())
                .publish(anyString(), anyString(), anyString(), anyBoolean());
        verify(agentSpecMaintainerService, never())
                .changeOnlineStatus(
                        anyString(), anyString(), anyString(), anyString(), anyBoolean());
    }

    @Test
    void updateVersionWhenApprovedVersionTargetsOnlinePublishes() throws Exception {
        Product product = workerProduct();
        AgentSpecMaintainerService agentSpecMaintainerService = mockAgentSpecMaintainer();
        AgentSpecMeta meta =
                agentSpecMeta(version(VERSION, "reviewing", "{\"status\":\"APPROVED\"}"));
        when(productRepository.findByProductId(PRODUCT_ID)).thenReturn(Optional.of(product));
        when(agentSpecMaintainerService.getAgentSpecAdminDetail(NAMESPACE, AGENT_SPEC_NAME))
                .thenReturn(meta);
        when(agentSpecMaintainerService.publish(NAMESPACE, AGENT_SPEC_NAME, VERSION, true))
                .thenReturn(true);

        service.updateVersion(PRODUCT_ID, VERSION, statusUpdate("online"));

        verify(agentSpecMaintainerService).publish(NAMESPACE, AGENT_SPEC_NAME, VERSION, true);
        verify(agentSpecMaintainerService, never())
                .changeOnlineStatus(
                        anyString(), anyString(), anyString(), anyString(), anyBoolean());
    }

    @Test
    void updateVersionWhenOfflineVersionTargetsOnlineChangesOnlineStatus() throws Exception {
        Product product = workerProduct();
        AgentSpecMaintainerService agentSpecMaintainerService = mockAgentSpecMaintainer();
        when(productRepository.findByProductId(PRODUCT_ID)).thenReturn(Optional.of(product));
        when(agentSpecMaintainerService.getAgentSpecAdminDetail(NAMESPACE, AGENT_SPEC_NAME))
                .thenReturn(agentSpecMeta(version(VERSION, "offline", null)));

        service.updateVersion(PRODUCT_ID, VERSION, statusUpdate("online"));

        verify(agentSpecMaintainerService)
                .changeOnlineStatus(NAMESPACE, AGENT_SPEC_NAME, "", VERSION, true);
        verify(agentSpecMaintainerService, never())
                .publish(anyString(), anyString(), anyString(), anyBoolean());
    }

    @Test
    void updateVersionWhenTargetIsOfflineChangesOnlineStatus() throws Exception {
        Product product = workerProduct();
        AgentSpecMaintainerService agentSpecMaintainerService = mockAgentSpecMaintainer();
        when(productRepository.findByProductId(PRODUCT_ID)).thenReturn(Optional.of(product));
        when(agentSpecMaintainerService.getAgentSpecAdminDetail(NAMESPACE, AGENT_SPEC_NAME))
                .thenReturn(agentSpecMeta(version(VERSION, "online", null)));

        service.updateVersion(PRODUCT_ID, VERSION, statusUpdate("offline"));

        verify(agentSpecMaintainerService)
                .changeOnlineStatus(NAMESPACE, AGENT_SPEC_NAME, "", VERSION, false);
    }

    @Test
    void updateVersionWhenTargetIsLatestUpdatesLatestLabel() throws Exception {
        Product product = workerProduct();
        AgentSpecMaintainerService agentSpecMaintainerService = mockAgentSpecMaintainer();
        when(productRepository.findByProductId(PRODUCT_ID)).thenReturn(Optional.of(product));
        when(agentSpecMaintainerService.getAgentSpecAdminDetail(NAMESPACE, AGENT_SPEC_NAME))
                .thenReturn(agentSpecMeta(version(VERSION, "online", null)));

        service.updateVersion(PRODUCT_ID, VERSION, latestUpdate());

        verify(agentSpecMaintainerService)
                .updateLabels(NAMESPACE, AGENT_SPEC_NAME, "{\"latest\":\"1.0.0\"}");
    }

    @Test
    void updateVersionRejectsUnsupportedUpdate() {
        BusinessException exception =
                assertThrows(
                        BusinessException.class,
                        () ->
                                service.updateVersion(
                                        PRODUCT_ID, VERSION, new UpdateWorkerVersionParam()));

        assertEquals("INVALID_PARAMETER", exception.getCode());
    }

    @Test
    void createDraftWhenBaseVersionExistsCreatesDraftInNacos() throws Exception {
        Product product = workerProduct();
        AgentSpecMaintainerService agentSpecMaintainerService = mockAgentSpecMaintainer();
        when(productRepository.findByProductId(PRODUCT_ID)).thenReturn(Optional.of(product));
        when(agentSpecMaintainerService.getAgentSpecAdminDetail(NAMESPACE, AGENT_SPEC_NAME))
                .thenReturn(agentSpecMeta(version(VERSION, "online", null)));
        when(agentSpecMaintainerService.createDraft(NAMESPACE, AGENT_SPEC_NAME, VERSION))
                .thenReturn("1.0.1");

        service.createDraft(PRODUCT_ID, createDraft(VERSION));

        verify(agentSpecMaintainerService).createDraft(NAMESPACE, AGENT_SPEC_NAME, VERSION);
    }

    @Test
    void createDraftRejectsExistingDraftVersion() throws Exception {
        Product product = workerProduct();
        AgentSpecMaintainerService agentSpecMaintainerService = mockAgentSpecMaintainer();
        when(productRepository.findByProductId(PRODUCT_ID)).thenReturn(Optional.of(product));
        when(agentSpecMaintainerService.getAgentSpecAdminDetail(NAMESPACE, AGENT_SPEC_NAME))
                .thenReturn(
                        agentSpecMeta(
                                version(VERSION, "online", null), version("1.0.1", "draft", null)));

        BusinessException exception =
                assertThrows(
                        BusinessException.class,
                        () -> service.createDraft(PRODUCT_ID, createDraft(VERSION)));

        assertEquals("CONFLICT", exception.getCode());
        verify(agentSpecMaintainerService, never())
                .createDraft(anyString(), anyString(), anyString());
    }

    @Test
    void createDraftAllowsExistingOtherVersion() throws Exception {
        Product product = workerProduct();
        AgentSpecMaintainerService agentSpecMaintainerService = mockAgentSpecMaintainer();
        when(productRepository.findByProductId(PRODUCT_ID)).thenReturn(Optional.of(product));
        when(agentSpecMaintainerService.getAgentSpecAdminDetail(NAMESPACE, AGENT_SPEC_NAME))
                .thenReturn(
                        agentSpecMeta(
                                version(VERSION, "online", null),
                                version("1.0.1", "online", null)));
        when(agentSpecMaintainerService.createDraft(NAMESPACE, AGENT_SPEC_NAME, VERSION))
                .thenReturn("1.0.2");

        service.createDraft(PRODUCT_ID, createDraft(VERSION));

        verify(agentSpecMaintainerService).createDraft(NAMESPACE, AGENT_SPEC_NAME, VERSION);
    }

    @Test
    void getDraftReturnsCurrentDraftAgentSpecCard() throws Exception {
        Product product = workerProduct();
        AgentSpecMaintainerService agentSpecMaintainerService = mockAgentSpecMaintainer();
        when(productRepository.findByProductId(PRODUCT_ID)).thenReturn(Optional.of(product));
        when(agentSpecMaintainerService.getAgentSpecAdminDetail(NAMESPACE, AGENT_SPEC_NAME))
                .thenReturn(agentSpecMeta(version("1.0.1", "draft", null)));
        when(agentSpecMaintainerService.getAgentSpecVersionDetail(
                        NAMESPACE, AGENT_SPEC_NAME, "1.0.1"))
                .thenReturn(agentSpec(AGENT_SPEC_NAME));

        WorkerDraftResult result = service.getDraft(PRODUCT_ID);

        assertEquals("1.0.1", result.getVersion());
        assertEquals(AGENT_SPEC_NAME, result.getAgentSpecCard().path("name").asText());
    }

    @Test
    void updateDraftUpdatesCurrentDraftInNacos() throws Exception {
        Product product = workerProduct();
        AgentSpecMaintainerService agentSpecMaintainerService = mockAgentSpecMaintainer();
        when(productRepository.findByProductId(PRODUCT_ID)).thenReturn(Optional.of(product));
        when(agentSpecMaintainerService.getAgentSpecAdminDetail(NAMESPACE, AGENT_SPEC_NAME))
                .thenReturn(agentSpecMeta(version("1.0.1", "draft", null)));
        when(agentSpecMaintainerService.updateDraft(
                        NAMESPACE, agentSpecJson(AGENT_SPEC_NAME), false))
                .thenReturn(true);

        service.updateDraft(PRODUCT_ID, updateDraft(AGENT_SPEC_NAME));

        verify(agentSpecMaintainerService)
                .updateDraft(NAMESPACE, agentSpecJson(AGENT_SPEC_NAME), false);
    }

    @Test
    void updateDraftRejectsMismatchedAgentSpecName() {
        Product product = workerProduct();
        mockAgentSpecMaintainer();
        when(productRepository.findByProductId(PRODUCT_ID)).thenReturn(Optional.of(product));

        BusinessException exception =
                assertThrows(
                        BusinessException.class,
                        () -> service.updateDraft(PRODUCT_ID, updateDraft("another-worker")));

        assertEquals("INVALID_PARAMETER", exception.getCode());
    }

    @Test
    void updateVersionAuthorStoresAuthorInWorkerConfig() throws Exception {
        Product product = workerProduct();
        AgentSpecMaintainerService agentSpecMaintainerService = mockAgentSpecMaintainer();
        when(productRepository.findByProductId(PRODUCT_ID)).thenReturn(Optional.of(product));
        when(agentSpecMaintainerService.getAgentSpecAdminDetail(NAMESPACE, AGENT_SPEC_NAME))
                .thenReturn(agentSpecMeta(version(VERSION, "online", null)));

        UpdateWorkerVersionParam param = new UpdateWorkerVersionParam();
        param.setAuthor("zhaoh");
        service.updateVersion(PRODUCT_ID, VERSION, param);

        assertEquals(
                "zhaoh",
                product.getFeature().getWorkerConfig().getVersionInfos().get(VERSION).getAuthor());
    }

    @Test
    void listVersionsReturnsVersionAuthor() throws Exception {
        Product product = workerProduct();
        AgentSpecMaintainerService agentSpecMaintainerService = mockAgentSpecMaintainer();
        when(productRepository.findByProductId(PRODUCT_ID)).thenReturn(Optional.of(product));
        when(agentSpecMaintainerService.getAgentSpecAdminDetail(NAMESPACE, AGENT_SPEC_NAME))
                .thenReturn(agentSpecMeta(version(VERSION, "online", null)));

        UpdateWorkerVersionParam param = new UpdateWorkerVersionParam();
        param.setAuthor("zhaoh");
        service.updateVersion(PRODUCT_ID, VERSION, param);

        List<VersionResult> versions = service.listVersions(PRODUCT_ID);

        assertEquals("zhaoh", versions.get(0).getAuthor());
    }

    @Test
    void listVersionsSyncsLatestVersionLabel() throws Exception {
        Product product = workerProduct();
        AgentSpecMaintainerService agentSpecMaintainerService = mockAgentSpecMaintainer();
        AgentSpecMeta meta = agentSpecMeta(version(VERSION, "online", null));
        meta.setLabels(Map.of("latest", VERSION));
        when(productRepository.findByProductId(PRODUCT_ID)).thenReturn(Optional.of(product));
        when(agentSpecMaintainerService.getAgentSpecAdminDetail(NAMESPACE, AGENT_SPEC_NAME))
                .thenReturn(meta);

        service.listVersions(PRODUCT_ID);

        assertEquals(VERSION, product.getFeature().getWorkerConfig().getLatestVersion());
    }

    @Test
    void downloadPackageWithoutVersionRequiresLatestVersion() throws Exception {
        Product product = workerProduct();
        AgentSpecMaintainerService agentSpecMaintainerService = mockAgentSpecMaintainer();
        when(productRepository.findByProductId(PRODUCT_ID)).thenReturn(Optional.of(product));
        when(agentSpecMaintainerService.getAgentSpecAdminDetail(NAMESPACE, AGENT_SPEC_NAME))
                .thenReturn(agentSpecMeta(version(VERSION, "online", null)));

        BusinessException exception =
                assertThrows(
                        BusinessException.class,
                        () ->
                                service.downloadPackage(
                                        PRODUCT_ID, null, mock(HttpServletResponse.class)));

        assertEquals("INVALID_PARAMETER", exception.getCode());
    }

    private UpdateWorkerVersionParam statusUpdate(String status) {
        UpdateWorkerVersionParam param = new UpdateWorkerVersionParam();
        param.setStatus(status);
        return param;
    }

    private UpdateWorkerVersionParam latestUpdate() {
        UpdateWorkerVersionParam param = new UpdateWorkerVersionParam();
        param.setLatest(true);
        return param;
    }

    private CreateWorkerDraftParam createDraft(String baseVersion) {
        CreateWorkerDraftParam param = new CreateWorkerDraftParam();
        param.setBaseVersion(baseVersion);
        return param;
    }

    private UpdateWorkerDraftParam updateDraft(String agentSpecName) {
        UpdateWorkerDraftParam param = new UpdateWorkerDraftParam();
        param.setAgentSpecCard(JsonUtil.convert(agentSpec(agentSpecName), JsonNode.class));
        return param;
    }

    private AgentSpec agentSpec(String name) {
        AgentSpec agentSpec = new AgentSpec();
        agentSpec.setNamespaceId(NAMESPACE);
        agentSpec.setName(name);
        agentSpec.setContent("{\"worker\":{\"suggested_name\":\"" + name + "\"}}");
        return agentSpec;
    }

    private String agentSpecJson(String name) {
        return JsonUtil.toJson(JsonUtil.convert(agentSpec(name), JsonNode.class));
    }

    private AgentSpecMaintainerService mockAgentSpecMaintainer() {
        AiMaintainerService aiMaintainerService = mock(AiMaintainerService.class);
        AgentSpecMaintainerService agentSpecMaintainerService =
                mock(AgentSpecMaintainerService.class);
        when(nacosService.getAiMaintainerService("nacos-prod")).thenReturn(aiMaintainerService);
        when(aiMaintainerService.agentSpec()).thenReturn(agentSpecMaintainerService);
        return agentSpecMaintainerService;
    }

    private Product workerProduct() {
        return Product.builder()
                .productId(PRODUCT_ID)
                .feature(
                        ProductFeature.builder()
                                .workerConfig(
                                        WorkerConfig.builder()
                                                .nacosId("nacos-prod")
                                                .namespace(NAMESPACE)
                                                .agentSpecName(AGENT_SPEC_NAME)
                                                .build())
                                .build())
                .build();
    }

    private AgentSpecMeta agentSpecMeta(AgentSpecMeta.AgentSpecVersionSummary... versions) {
        AgentSpecMeta meta = new AgentSpecMeta();
        meta.setVersions(List.of(versions));
        meta.setLabels(Map.of());
        return meta;
    }

    private AgentSpecMeta.AgentSpecVersionSummary version(
            String version, String status, String publishPipelineInfo) {
        AgentSpecMeta.AgentSpecVersionSummary summary = new AgentSpecMeta.AgentSpecVersionSummary();
        summary.setVersion(version);
        summary.setStatus(status);
        summary.setPublishPipelineInfo(publishPipelineInfo);
        summary.setCreateTime(1L);
        return summary;
    }
}
