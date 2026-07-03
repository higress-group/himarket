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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.alibaba.himarket.dto.params.worker.CreateWorkerDraftParam;
import com.alibaba.himarket.dto.params.worker.UpdateWorkerDraftParam;
import com.alibaba.himarket.dto.params.worker.UpdateWorkerVersionParam;
import com.alibaba.himarket.service.WorkerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class WorkerControllerTest {

    private WorkerService workerService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        workerService = mock(WorkerService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new WorkerController(workerService)).build();
    }

    @Test
    void submitVersionReviewUsesPatchRoute() throws Exception {
        mockMvc.perform(
                        patch("/workers/product-a/versions/1.0.0")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"status\":\"reviewing\"}"))
                .andExpect(status().isOk());

        ArgumentCaptor<UpdateWorkerVersionParam> param =
                ArgumentCaptor.forClass(UpdateWorkerVersionParam.class);
        verify(workerService).updateVersion(eq("product-a"), eq("1.0.0"), param.capture());
        assertEquals("reviewing", param.getValue().getStatus());
        assertNull(param.getValue().getLatest());
    }

    @Test
    void setLatestVersionUsesPatchRoute() throws Exception {
        mockMvc.perform(
                        patch("/workers/product-a/versions/1.0.0")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"latest\":true}"))
                .andExpect(status().isOk());

        ArgumentCaptor<UpdateWorkerVersionParam> param =
                ArgumentCaptor.forClass(UpdateWorkerVersionParam.class);
        verify(workerService).updateVersion(eq("product-a"), eq("1.0.0"), param.capture());
        assertTrue(param.getValue().getLatest());
        assertNull(param.getValue().getStatus());
    }

    @Test
    void updateVersionAuthorUsesPatchRoute() throws Exception {
        mockMvc.perform(
                        patch("/workers/product-a/versions/1.0.0")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"author\":\"zhaoh\"}"))
                .andExpect(status().isOk());

        ArgumentCaptor<UpdateWorkerVersionParam> param =
                ArgumentCaptor.forClass(UpdateWorkerVersionParam.class);
        verify(workerService).updateVersion(eq("product-a"), eq("1.0.0"), param.capture());
        assertEquals("zhaoh", param.getValue().getAuthor());
        assertNull(param.getValue().getStatus());
        assertNull(param.getValue().getLatest());
    }

    @Test
    void deleteDraftKeepsSingleDraftResourceRoute() throws Exception {
        mockMvc.perform(delete("/workers/product-a/draft")).andExpect(status().isOk());

        verify(workerService).deleteDraft("product-a");
    }

    @Test
    void createDraftKeepsSingleDraftResourceRoute() throws Exception {
        mockMvc.perform(
                        post("/workers/product-a/draft")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"baseVersion\":\"1.0.0\"}"))
                .andExpect(status().isOk());

        ArgumentCaptor<CreateWorkerDraftParam> param =
                ArgumentCaptor.forClass(CreateWorkerDraftParam.class);
        verify(workerService).createDraft(eq("product-a"), param.capture());
        assertEquals("1.0.0", param.getValue().getBaseVersion());
    }

    @Test
    void getDraftUsesSingleDraftResourceRoute() throws Exception {
        mockMvc.perform(get("/workers/product-a/draft")).andExpect(status().isOk());

        verify(workerService).getDraft("product-a");
    }

    @Test
    void updateDraftUsesSingleDraftResourceRoute() throws Exception {
        mockMvc.perform(
                        put("/workers/product-a/draft")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"agentSpecCard\":{\"name\":\"worker-a\"}}"))
                .andExpect(status().isOk());

        ArgumentCaptor<UpdateWorkerDraftParam> param =
                ArgumentCaptor.forClass(UpdateWorkerDraftParam.class);
        verify(workerService).updateDraft(eq("product-a"), param.capture());
        assertEquals("worker-a", param.getValue().getAgentSpecCard().path("name").asText());
    }
}
