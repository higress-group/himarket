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

package com.alibaba.himarket.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.alibaba.himarket.dto.params.skill.CreateSkillDraftParam;
import com.alibaba.himarket.dto.params.skill.UpdateSkillDraftParam;
import com.alibaba.himarket.dto.params.skill.UpdateSkillVersionParam;
import com.alibaba.himarket.dto.result.common.SkillDraftResult;
import com.alibaba.himarket.service.SkillService;
import com.alibaba.himarket.utils.JsonUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.multipart.MultipartFile;

class SkillControllerTest {

    private SkillService skillService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        skillService = mock(SkillService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new SkillController(skillService)).build();
    }

    @Test
    void submitVersionReviewUsesPatchRoute() throws Exception {
        mockMvc.perform(
                        patch("/skills/product-a/versions/1.0.0")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"status\":\"reviewing\"}"))
                .andExpect(status().isOk());

        ArgumentCaptor<UpdateSkillVersionParam> param =
                ArgumentCaptor.forClass(UpdateSkillVersionParam.class);
        verify(skillService).updateVersion(eq("product-a"), eq("1.0.0"), param.capture());
        assertEquals("reviewing", param.getValue().getStatus());
        assertNull(param.getValue().getLatest());
    }

    @Test
    void setLatestVersionUsesPatchRoute() throws Exception {
        mockMvc.perform(
                        patch("/skills/product-a/versions/1.0.0")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"latest\":true}"))
                .andExpect(status().isOk());

        ArgumentCaptor<UpdateSkillVersionParam> param =
                ArgumentCaptor.forClass(UpdateSkillVersionParam.class);
        verify(skillService).updateVersion(eq("product-a"), eq("1.0.0"), param.capture());
        assertTrue(param.getValue().getLatest());
        assertNull(param.getValue().getStatus());
    }

    @Test
    void forcePublishVersionUsesPatchRoute() throws Exception {
        mockMvc.perform(
                        patch("/skills/product-a/versions/1.0.0")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"status\":\"online\",\"force\":true,"
                                                + "\"updateLatestLabel\":false}"))
                .andExpect(status().isOk());

        ArgumentCaptor<UpdateSkillVersionParam> param =
                ArgumentCaptor.forClass(UpdateSkillVersionParam.class);
        verify(skillService).updateVersion(eq("product-a"), eq("1.0.0"), param.capture());
        assertEquals("online", param.getValue().getStatus());
        assertTrue(param.getValue().getForce());
        assertFalse(param.getValue().getUpdateLatestLabel());
    }

    @Test
    void deleteDraftKeepsSingleDraftResourceRoute() throws Exception {
        mockMvc.perform(delete("/skills/product-a/draft")).andExpect(status().isOk());

        verify(skillService).deleteDraft("product-a");
    }

    @Test
    void createDraftKeepsSingleDraftResourceRoute() throws Exception {
        mockMvc.perform(
                        post("/skills/product-a/draft")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"baseVersion\":\"1.0.0\",\"version\":\"1.0.1\"}"))
                .andExpect(status().isOk());

        ArgumentCaptor<CreateSkillDraftParam> param =
                ArgumentCaptor.forClass(CreateSkillDraftParam.class);
        verify(skillService).createDraft(eq("product-a"), param.capture());
        assertEquals("1.0.0", param.getValue().getBaseVersion());
        assertEquals("1.0.1", param.getValue().getVersion());
    }

    @Test
    void getDraftKeepsSingleDraftResourceRoute() throws Exception {
        when(skillService.getDraft("product-a"))
                .thenReturn(
                        SkillDraftResult.builder()
                                .version("1.0.1")
                                .skillCard(JsonUtil.readTree("{\"name\":\"skill-a\"}"))
                                .build());

        mockMvc.perform(get("/skills/product-a/draft")).andExpect(status().isOk());

        verify(skillService).getDraft("product-a");
    }

    @Test
    void updateDraftKeepsSingleDraftResourceRoute() throws Exception {
        mockMvc.perform(
                        put("/skills/product-a/draft")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"skillCard\":{\"name\":\"skill-a\","
                                                + "\"description\":\"desc\","
                                                + "\"skillMd\":\"content\"}}"))
                .andExpect(status().isOk());

        ArgumentCaptor<UpdateSkillDraftParam> param =
                ArgumentCaptor.forClass(UpdateSkillDraftParam.class);
        verify(skillService).updateDraft(eq("product-a"), param.capture());
        assertEquals("skill-a", param.getValue().getSkillCard().path("name").asText());
        assertEquals("content", param.getValue().getSkillCard().path("skillMd").asText());
    }

    @Test
    void updateVersionAuthorUsesPatchRoute() throws Exception {
        mockMvc.perform(
                        patch("/skills/product-a/versions/1.0.0")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"author\":\"Ada\"}"))
                .andExpect(status().isOk());

        ArgumentCaptor<UpdateSkillVersionParam> param =
                ArgumentCaptor.forClass(UpdateSkillVersionParam.class);
        verify(skillService).updateVersion(eq("product-a"), eq("1.0.0"), param.capture());
        assertEquals("Ada", param.getValue().getAuthor());
        assertNull(param.getValue().getStatus());
        assertNull(param.getValue().getLatest());
    }

    @Test
    void uploadPackagePassesOnlyFileToService() throws Exception {
        MockMultipartFile file =
                new MockMultipartFile("file", "skill.zip", "application/zip", new byte[] {1, 2, 3});

        mockMvc.perform(multipart("/skills/product-a/package").file(file))
                .andExpect(status().isOk());

        verify(skillService).uploadPackage(eq("product-a"), any(MultipartFile.class));
    }
}
