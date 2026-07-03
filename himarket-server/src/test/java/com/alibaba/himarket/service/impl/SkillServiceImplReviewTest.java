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
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.alibaba.himarket.core.exception.BusinessException;
import com.alibaba.himarket.core.security.ContextHolder;
import com.alibaba.himarket.dto.params.skill.CreateSkillDraftParam;
import com.alibaba.himarket.dto.params.skill.UpdateSkillDraftParam;
import com.alibaba.himarket.dto.params.skill.UpdateSkillVersionParam;
import com.alibaba.himarket.dto.result.common.SkillDraftResult;
import com.alibaba.himarket.dto.result.common.VersionResult;
import com.alibaba.himarket.entity.Product;
import com.alibaba.himarket.repository.ProductRepository;
import com.alibaba.himarket.service.AiRegistrySkillService;
import com.alibaba.himarket.service.NacosService;
import com.alibaba.himarket.support.enums.ProductStatus;
import com.alibaba.himarket.support.enums.SkillRegistryType;
import com.alibaba.himarket.support.product.ProductFeature;
import com.alibaba.himarket.support.product.SkillConfig;
import com.alibaba.himarket.support.product.VersionInfo;
import com.alibaba.himarket.utils.JsonUtil;
import com.alibaba.nacos.api.ai.model.skills.Skill;
import com.alibaba.nacos.api.ai.model.skills.SkillMeta;
import com.alibaba.nacos.maintainer.client.ai.AiMaintainerService;
import com.alibaba.nacos.maintainer.client.ai.SkillMaintainerService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class SkillServiceImplReviewTest {

    private static final String PRODUCT_ID = "product-a";
    private static final String VERSION = "1.0.0";
    private static final String NAMESPACE = "ns-prod";
    private static final String SKILL_NAME = "skill-a";

    private NacosService nacosService;
    private ProductRepository productRepository;
    private ContextHolder contextHolder;
    private AiRegistrySkillService aiRegistrySkillService;
    private SkillServiceImpl service;

    @BeforeEach
    void setUp() {
        nacosService = mock(NacosService.class);
        productRepository = mock(ProductRepository.class);
        contextHolder = mock(ContextHolder.class);
        aiRegistrySkillService = mock(AiRegistrySkillService.class);
        service =
                new SkillServiceImpl(
                        nacosService, productRepository, contextHolder, aiRegistrySkillService);
        when(contextHolder.isAdministrator()).thenReturn(true);
    }

    @Test
    void updateVersionWhenAiRegistryTargetIsReviewingSubmitsOnly() {
        Product product = aiRegistryProduct();
        when(productRepository.findByProductId(PRODUCT_ID)).thenReturn(Optional.of(product));
        when(aiRegistrySkillService.submit("airegistry-prod", NAMESPACE, SKILL_NAME, VERSION))
                .thenReturn(VERSION);

        service.updateVersion(PRODUCT_ID, VERSION, statusUpdate("reviewing"));

        verify(aiRegistrySkillService).submit("airegistry-prod", NAMESPACE, SKILL_NAME, VERSION);
        verify(aiRegistrySkillService, never())
                .publish(anyString(), anyString(), anyString(), anyString(), any());
        verify(aiRegistrySkillService, never())
                .forcePublish(anyString(), anyString(), anyString(), anyString(), any());
    }

    @Test
    void updateVersionWhenNacosTargetIsReviewingSubmitsOnly() throws Exception {
        Product product = nacosProduct();
        SkillMaintainerService skillMaintainerService = mockNacosSkillMaintainer();
        when(productRepository.findByProductId(PRODUCT_ID)).thenReturn(Optional.of(product));
        when(skillMaintainerService.submit(NAMESPACE, SKILL_NAME, VERSION)).thenReturn(VERSION);

        service.updateVersion(PRODUCT_ID, VERSION, statusUpdate("reviewing"));

        verify(skillMaintainerService).submit(NAMESPACE, SKILL_NAME, VERSION);
        verify(skillMaintainerService, never())
                .publish(anyString(), anyString(), anyString(), anyBoolean());
        verify(skillMaintainerService, never())
                .forcePublish(anyString(), anyString(), anyString(), any());
    }

    @Test
    void updateVersionWhenApprovedVersionTargetsOnlinePublishes() throws Exception {
        Product product = nacosProduct();
        SkillMaintainerService skillMaintainerService = mockNacosSkillMaintainer();
        SkillMeta meta = skillMeta(version(VERSION, "reviewing", "{\"status\":\"APPROVED\"}"));
        when(productRepository.findByProductId(PRODUCT_ID)).thenReturn(Optional.of(product));
        when(skillMaintainerService.getSkillMeta(NAMESPACE, SKILL_NAME)).thenReturn(meta);
        when(skillMaintainerService.publish(NAMESPACE, SKILL_NAME, VERSION, true)).thenReturn(true);

        service.updateVersion(PRODUCT_ID, VERSION, statusUpdate("online"));

        verify(skillMaintainerService).publish(NAMESPACE, SKILL_NAME, VERSION, true);
        verify(skillMaintainerService, never())
                .changeOnlineStatus(
                        anyString(), anyString(), anyString(), anyString(), anyBoolean());
    }

    @Test
    void updateVersionWhenOfflineVersionTargetsOnlineChangesOnlineStatus() throws Exception {
        Product product = nacosProduct();
        SkillMaintainerService skillMaintainerService = mockNacosSkillMaintainer();
        SkillMeta meta = skillMeta(version(VERSION, "offline", null));
        when(productRepository.findByProductId(PRODUCT_ID)).thenReturn(Optional.of(product));
        when(skillMaintainerService.getSkillMeta(NAMESPACE, SKILL_NAME)).thenReturn(meta);

        service.updateVersion(PRODUCT_ID, VERSION, statusUpdate("online"));

        verify(skillMaintainerService).changeOnlineStatus(NAMESPACE, SKILL_NAME, "", VERSION, true);
        verify(skillMaintainerService, never())
                .publish(anyString(), anyString(), anyString(), anyBoolean());
    }

    @Test
    void updateVersionWhenTargetIsOfflineChangesOnlineStatus() throws Exception {
        Product product = nacosProduct();
        SkillMaintainerService skillMaintainerService = mockNacosSkillMaintainer();
        when(productRepository.findByProductId(PRODUCT_ID)).thenReturn(Optional.of(product));
        when(skillMaintainerService.getSkillMeta(NAMESPACE, SKILL_NAME))
                .thenReturn(skillMeta(version(VERSION, "online", null)));

        service.updateVersion(PRODUCT_ID, VERSION, statusUpdate("offline"));

        verify(skillMaintainerService)
                .changeOnlineStatus(NAMESPACE, SKILL_NAME, "", VERSION, false);
    }

    @Test
    void updateVersionWhenTargetIsLatestUpdatesLatestLabel() throws Exception {
        Product product = nacosProduct();
        SkillMaintainerService skillMaintainerService = mockNacosSkillMaintainer();
        when(productRepository.findByProductId(PRODUCT_ID)).thenReturn(Optional.of(product));
        when(skillMaintainerService.getSkillMeta(NAMESPACE, SKILL_NAME))
                .thenReturn(skillMeta(version(VERSION, "online", null)));

        service.updateVersion(PRODUCT_ID, VERSION, latestUpdate());

        verify(skillMaintainerService)
                .updateLabels(NAMESPACE, SKILL_NAME, "{\"latest\":\"1.0.0\"}");
    }

    @Test
    void updateVersionWhenTargetIsForcedOnlineForcePublishes() throws Exception {
        Product product = nacosProduct();
        SkillMaintainerService skillMaintainerService = mockNacosSkillMaintainer();
        when(productRepository.findByProductId(PRODUCT_ID)).thenReturn(Optional.of(product));

        service.updateVersion(PRODUCT_ID, VERSION, forceOnlineUpdate(false));

        verify(skillMaintainerService).forcePublish(NAMESPACE, SKILL_NAME, VERSION, false);
        verify(skillMaintainerService, never())
                .publish(anyString(), anyString(), anyString(), anyBoolean());
        verify(skillMaintainerService, never())
                .changeOnlineStatus(
                        anyString(), anyString(), anyString(), anyString(), anyBoolean());
    }

    @Test
    void updateVersionRejectsUnsupportedUpdate() {
        BusinessException exception =
                assertThrows(
                        BusinessException.class,
                        () ->
                                service.updateVersion(
                                        PRODUCT_ID, VERSION, new UpdateSkillVersionParam()));

        assertEquals("INVALID_PARAMETER", exception.getCode());
    }

    @Test
    void updateVersionWhenAuthorSpecifiedStoresLocalVersionInfo() throws Exception {
        Product product = nacosProduct();
        SkillMaintainerService skillMaintainerService = mockNacosSkillMaintainer();
        when(productRepository.findByProductId(PRODUCT_ID)).thenReturn(Optional.of(product));
        when(skillMaintainerService.getSkillMeta(NAMESPACE, SKILL_NAME))
                .thenReturn(skillMeta(version(VERSION, "online", null)));

        service.updateVersion(PRODUCT_ID, VERSION, authorUpdate("Ada"));

        assertEquals(
                "Ada",
                product.getFeature().getSkillConfig().getVersionInfos().get(VERSION).getAuthor());
        verify(productRepository).save(product);
    }

    @Test
    void createDraftWhenBaseVersionIsOnlineCopiesVersionInNacos() throws Exception {
        Product product = nacosProduct();
        SkillMaintainerService skillMaintainerService = mockNacosSkillMaintainer();
        SkillMeta meta = skillMeta(version(VERSION, "online", null));
        when(productRepository.findByProductId(PRODUCT_ID)).thenReturn(Optional.of(product));
        when(skillMaintainerService.getSkillMeta(NAMESPACE, SKILL_NAME)).thenReturn(meta);
        when(skillMaintainerService.createDraft(NAMESPACE, SKILL_NAME, VERSION, "1.0.1"))
                .thenReturn("1.0.1");

        service.createDraft(PRODUCT_ID, createDraft(VERSION, "1.0.1"));

        verify(skillMaintainerService).createDraft(NAMESPACE, SKILL_NAME, VERSION, "1.0.1");
    }

    @Test
    void createDraftWhenAiRegistryBaseVersionIsOnlineCreatesDraft() {
        Product product = aiRegistryProduct();
        when(productRepository.findByProductId(PRODUCT_ID)).thenReturn(Optional.of(product));
        when(aiRegistrySkillService.listVersions("airegistry-prod", NAMESPACE, SKILL_NAME))
                .thenReturn(
                        List.of(VersionResult.builder().version(VERSION).status("online").build()));
        when(aiRegistrySkillService.createDraft(
                        "airegistry-prod", NAMESPACE, SKILL_NAME, VERSION, "1.0.1"))
                .thenReturn("1.0.1");

        service.createDraft(PRODUCT_ID, createDraft(VERSION, "1.0.1"));

        verify(aiRegistrySkillService)
                .createDraft("airegistry-prod", NAMESPACE, SKILL_NAME, VERSION, "1.0.1");
    }

    @Test
    void createDraftRejectsExistingDraftVersion() throws Exception {
        Product product = nacosProduct();
        SkillMaintainerService skillMaintainerService = mockNacosSkillMaintainer();
        SkillMeta meta =
                skillMeta(version(VERSION, "online", null), version("1.0.1", "draft", null));
        when(productRepository.findByProductId(PRODUCT_ID)).thenReturn(Optional.of(product));
        when(skillMaintainerService.getSkillMeta(NAMESPACE, SKILL_NAME)).thenReturn(meta);

        BusinessException exception =
                assertThrows(
                        BusinessException.class,
                        () -> service.createDraft(PRODUCT_ID, createDraft(VERSION, "1.0.2")));

        assertEquals("CONFLICT", exception.getCode());
    }

    @Test
    void createDraftRejectsDuplicateTargetVersion() throws Exception {
        Product product = nacosProduct();
        SkillMaintainerService skillMaintainerService = mockNacosSkillMaintainer();
        SkillMeta meta = skillMeta(version(VERSION, "online", null));
        when(productRepository.findByProductId(PRODUCT_ID)).thenReturn(Optional.of(product));
        when(skillMaintainerService.getSkillMeta(NAMESPACE, SKILL_NAME)).thenReturn(meta);

        BusinessException exception =
                assertThrows(
                        BusinessException.class,
                        () -> service.createDraft(PRODUCT_ID, createDraft(VERSION, VERSION)));

        assertEquals("CONFLICT", exception.getCode());
    }

    @Test
    void getDraftReturnsCurrentDraftSkillCard() throws Exception {
        Product product = nacosProduct();
        SkillMaintainerService skillMaintainerService = mockNacosSkillMaintainer();
        SkillMeta meta =
                skillMeta(version(VERSION, "online", null), version("1.0.1", "draft", null));
        when(productRepository.findByProductId(PRODUCT_ID)).thenReturn(Optional.of(product));
        when(skillMaintainerService.getSkillMeta(NAMESPACE, SKILL_NAME)).thenReturn(meta);
        when(skillMaintainerService.getSkillVersionDetail(NAMESPACE, SKILL_NAME, "1.0.1"))
                .thenReturn(skill(SKILL_NAME, "draft content"));

        SkillDraftResult result = service.getDraft(PRODUCT_ID);

        assertEquals("1.0.1", result.getVersion());
        assertEquals(SKILL_NAME, result.getSkillCard().path("name").asText());
        assertEquals("draft content", result.getSkillCard().path("skillMd").asText());
    }

    @Test
    void updateDraftUpdatesCurrentDraftSkillCardInNacos() throws Exception {
        Product product = nacosProduct();
        SkillMaintainerService skillMaintainerService = mockNacosSkillMaintainer();
        SkillMeta meta =
                skillMeta(version(VERSION, "online", null), version("1.0.1", "draft", null));
        when(productRepository.findByProductId(PRODUCT_ID)).thenReturn(Optional.of(product));
        when(skillMaintainerService.getSkillMeta(NAMESPACE, SKILL_NAME)).thenReturn(meta);
        when(skillMaintainerService.updateDraft(anyString(), anyString(), any())).thenReturn(true);

        service.updateDraft(PRODUCT_ID, updateDraft(SKILL_NAME, "draft content"));

        ArgumentCaptor<String> skillCard = ArgumentCaptor.forClass(String.class);
        verify(skillMaintainerService).updateDraft(eq(NAMESPACE), skillCard.capture(), eq(false));
        JsonNode node = JsonUtil.readTree(skillCard.getValue());
        assertEquals(SKILL_NAME, node.path("name").asText());
        assertEquals("draft content", node.path("skillMd").asText());
    }

    @Test
    void updateDraftRejectsSkillCardNameMismatch() throws Exception {
        Product product = nacosProduct();
        SkillMaintainerService skillMaintainerService = mockNacosSkillMaintainer();
        SkillMeta meta =
                skillMeta(version(VERSION, "online", null), version("1.0.1", "draft", null));
        when(productRepository.findByProductId(PRODUCT_ID)).thenReturn(Optional.of(product));
        when(skillMaintainerService.getSkillMeta(NAMESPACE, SKILL_NAME)).thenReturn(meta);

        BusinessException exception =
                assertThrows(
                        BusinessException.class,
                        () ->
                                service.updateDraft(
                                        PRODUCT_ID, updateDraft("other-skill", "draft content")));

        assertEquals("INVALID_PARAMETER", exception.getCode());
        verify(skillMaintainerService, never()).updateDraft(anyString(), anyString(), any());
    }

    @Test
    void listVersionsDoesNotExposeNacosDefaultAuthor() throws Exception {
        Product product = nacosProduct();
        SkillMaintainerService skillMaintainerService = mockNacosSkillMaintainer();
        SkillMeta.SkillVersionSummary summary = version(VERSION, "online", null);
        summary.setAuthor("nacos");
        when(productRepository.findByProductId(PRODUCT_ID)).thenReturn(Optional.of(product));
        when(skillMaintainerService.getSkillMeta(NAMESPACE, SKILL_NAME))
                .thenReturn(skillMeta(summary));

        assertNull(service.listVersions(PRODUCT_ID).get(0).getAuthor());
    }

    @Test
    void listVersionsUsesConfiguredVersionAuthor() throws Exception {
        Product product = nacosProduct();
        product.getFeature()
                .getSkillConfig()
                .setVersionInfos(Map.of(VERSION, VersionInfo.builder().author("Ada").build()));
        SkillMaintainerService skillMaintainerService = mockNacosSkillMaintainer();
        SkillMeta.SkillVersionSummary summary = version(VERSION, "online", null);
        summary.setAuthor("nacos");
        when(productRepository.findByProductId(PRODUCT_ID)).thenReturn(Optional.of(product));
        when(skillMaintainerService.getSkillMeta(NAMESPACE, SKILL_NAME))
                .thenReturn(skillMeta(summary));

        assertEquals("Ada", service.listVersions(PRODUCT_ID).get(0).getAuthor());
    }

    @Test
    void listVersionsSyncsLatestVersionLabel() throws Exception {
        Product product = nacosProduct();
        SkillMaintainerService skillMaintainerService = mockNacosSkillMaintainer();
        SkillMeta meta = skillMeta(version(VERSION, "online", null));
        meta.setLabels(Map.of("latest", VERSION));
        when(productRepository.findByProductId(PRODUCT_ID)).thenReturn(Optional.of(product));
        when(skillMaintainerService.getSkillMeta(NAMESPACE, SKILL_NAME)).thenReturn(meta);

        service.listVersions(PRODUCT_ID);

        assertEquals(VERSION, product.getFeature().getSkillConfig().getLatestVersion());
    }

    @Test
    void downloadPackageWithoutVersionRequiresLatestVersion() throws Exception {
        Product product = nacosProduct();
        SkillMaintainerService skillMaintainerService = mockNacosSkillMaintainer();
        when(productRepository.findByProductId(PRODUCT_ID)).thenReturn(Optional.of(product));
        when(skillMaintainerService.getSkillMeta(NAMESPACE, SKILL_NAME))
                .thenReturn(skillMeta(version(VERSION, "online", null)));

        BusinessException exception =
                assertThrows(
                        BusinessException.class,
                        () ->
                                service.downloadPackage(
                                        PRODUCT_ID, null, mock(HttpServletResponse.class)));

        assertEquals("INVALID_PARAMETER", exception.getCode());
    }

    private UpdateSkillVersionParam statusUpdate(String status) {
        UpdateSkillVersionParam param = new UpdateSkillVersionParam();
        param.setStatus(status);
        return param;
    }

    private UpdateSkillVersionParam latestUpdate() {
        UpdateSkillVersionParam param = new UpdateSkillVersionParam();
        param.setLatest(true);
        return param;
    }

    private UpdateSkillVersionParam forceOnlineUpdate(Boolean updateLatestLabel) {
        UpdateSkillVersionParam param = statusUpdate("online");
        param.setForce(true);
        param.setUpdateLatestLabel(updateLatestLabel);
        return param;
    }

    private UpdateSkillVersionParam authorUpdate(String author) {
        UpdateSkillVersionParam param = new UpdateSkillVersionParam();
        param.setAuthor(author);
        return param;
    }

    private CreateSkillDraftParam createDraft(String baseVersion, String version) {
        CreateSkillDraftParam param = new CreateSkillDraftParam();
        param.setBaseVersion(baseVersion);
        param.setVersion(version);
        return param;
    }

    private UpdateSkillDraftParam updateDraft(String name, String skillMd) {
        ObjectNode skillCard = JsonUtil.createObjectNode();
        skillCard.put("name", name);
        skillCard.put("description", "desc");
        skillCard.put("skillMd", skillMd);
        skillCard.set("resource", JsonUtil.createObjectNode());

        UpdateSkillDraftParam param = new UpdateSkillDraftParam();
        param.setSkillCard(skillCard);
        return param;
    }

    private Skill skill(String name, String skillMd) {
        Skill skill = new Skill();
        skill.setName(name);
        skill.setDescription("desc");
        skill.setSkillMd(skillMd);
        return skill;
    }

    private SkillMaintainerService mockNacosSkillMaintainer() {
        AiMaintainerService aiMaintainerService = mock(AiMaintainerService.class);
        SkillMaintainerService skillMaintainerService = mock(SkillMaintainerService.class);
        when(nacosService.getAiMaintainerService("nacos-prod")).thenReturn(aiMaintainerService);
        when(aiMaintainerService.skill()).thenReturn(skillMaintainerService);
        return skillMaintainerService;
    }

    private Product aiRegistryProduct() {
        return Product.builder()
                .productId(PRODUCT_ID)
                .status(ProductStatus.READY)
                .feature(
                        ProductFeature.builder()
                                .skillConfig(
                                        SkillConfig.builder()
                                                .registryType(SkillRegistryType.AIREGISTRY)
                                                .aiRegistryId("airegistry-prod")
                                                .namespace(NAMESPACE)
                                                .skillName(SKILL_NAME)
                                                .build())
                                .build())
                .build();
    }

    private Product nacosProduct() {
        return Product.builder()
                .productId(PRODUCT_ID)
                .status(ProductStatus.READY)
                .feature(
                        ProductFeature.builder()
                                .skillConfig(
                                        SkillConfig.builder()
                                                .registryType(SkillRegistryType.NACOS)
                                                .nacosId("nacos-prod")
                                                .namespace(NAMESPACE)
                                                .skillName(SKILL_NAME)
                                                .build())
                                .build())
                .build();
    }

    private SkillMeta skillMeta(SkillMeta.SkillVersionSummary... versions) {
        SkillMeta meta = new SkillMeta();
        meta.setVersions(List.of(versions));
        meta.setLabels(Map.of());
        return meta;
    }

    private SkillMeta.SkillVersionSummary version(
            String version, String status, String publishPipelineInfo) {
        SkillMeta.SkillVersionSummary summary = new SkillMeta.SkillVersionSummary();
        summary.setVersion(version);
        summary.setStatus(status);
        summary.setPublishPipelineInfo(publishPipelineInfo);
        summary.setCreateTime(1L);
        return summary;
    }
}
