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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.alibaba.himarket.core.exception.BusinessException;
import com.alibaba.himarket.dto.result.airegistry.AiRegistrySkillResult;
import com.alibaba.himarket.entity.AiRegistryInstance;
import com.alibaba.himarket.repository.AiRegistryInstanceRepository;
import com.aliyun.airegistry20260317.Client;
import com.aliyun.airegistry20260317.models.GetSkillImportFileUrlRequest;
import com.aliyun.airegistry20260317.models.ListSkillsRequest;
import com.aliyun.airegistry20260317.models.ListSkillsResponse;
import com.aliyun.airegistry20260317.models.ListSkillsResponseBody;
import com.aliyun.tea.TeaException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AiRegistrySkillServiceImplTest {

    @Test
    void listSkillMetadataReadsEveryPageAndMapsLatestVersion() throws Exception {
        AiRegistryInstanceRepository repository = mock(AiRegistryInstanceRepository.class);
        AiRegistryInstance instance =
                AiRegistryInstance.builder().aiRegistryId("airegistry-1").build();
        Client client = mock(Client.class);
        AiRegistrySkillServiceImpl service = spy(new AiRegistrySkillServiceImpl(repository));
        List<ListSkillsResponseBody.ListSkillsResponseBodyDataPageItems> firstPage =
                new ArrayList<>();
        for (int index = 0; index < 100; index++) {
            firstPage.add(skill("skill-" + index, (long) index, Map.of()));
        }
        firstPage.set(0, skill("skill-0", 10L, Map.of("latest", "2.0.0")));
        List<ListSkillsResponseBody.ListSkillsResponseBodyDataPageItems> secondPage =
                List.of(skill("skill-100", null, null));

        when(repository.findByAiRegistryId("airegistry-1")).thenReturn(Optional.of(instance));
        doReturn(client).when(service).buildClient(instance);
        when(client.listSkills(any(ListSkillsRequest.class)))
                .thenReturn(response(firstPage), response(secondPage));

        Map<String, AiRegistrySkillResult> result =
                service.listSkillMetadata("airegistry-1", "namespace-1");

        assertEquals(101, result.size());
        assertEquals(10L, result.get("skill-0").getDownloadCount());
        assertEquals("2.0.0", result.get("skill-0").getLatestVersion());
        assertNull(result.get("skill-100").getDownloadCount());
        assertNull(result.get("skill-100").getLatestVersion());

        ArgumentCaptor<ListSkillsRequest> requestCaptor =
                ArgumentCaptor.forClass(ListSkillsRequest.class);
        verify(client, times(2)).listSkills(requestCaptor.capture());
        assertEquals(
                List.of(1, 2),
                requestCaptor.getAllValues().stream().map(ListSkillsRequest::getPageNo).toList());
        assertEquals(
                List.of(100, 100),
                requestCaptor.getAllValues().stream().map(ListSkillsRequest::getPageSize).toList());
    }

    @Test
    void uploadFromZipReturnsActionableTeaValidationMessage() throws Exception {
        AiRegistryInstanceRepository repository = mock(AiRegistryInstanceRepository.class);
        AiRegistryInstance instance =
                AiRegistryInstance.builder().aiRegistryId("airegistry-1").build();
        Client client = mock(Client.class);
        AiRegistrySkillServiceImpl service = spy(new AiRegistrySkillServiceImpl(repository));
        when(repository.findByAiRegistryId("airegistry-1")).thenReturn(Optional.of(instance));
        doReturn(client).when(service).buildClient(instance);

        TeaException teaException = new TeaException();
        teaException.setCode("InvalidParameter");
        teaException.setStatusCode(400);
        teaException.setMessage("Skill package manifest is invalid: name is required");
        doThrow(teaException)
                .when(client)
                .getSkillImportFileUrl(any(GetSkillImportFileUrlRequest.class));

        BusinessException exception =
                assertThrows(
                        BusinessException.class,
                        () ->
                                service.uploadFromZip(
                                        "airegistry-1",
                                        "namespace-1",
                                        new byte[] {1},
                                        "skill.zip",
                                        false));

        assertEquals("INVALID_PARAMETER", exception.getCode());
        assertEquals(
                "Invalid request parameter: Failed to upload AIRegistry Skill package: "
                        + "InvalidParameter: Skill package manifest is invalid: name is required",
                exception.getMessage());
    }

    @Test
    void uploadFromZipRedactsSensitiveTeaDetails() throws Exception {
        AiRegistryInstanceRepository repository = mock(AiRegistryInstanceRepository.class);
        AiRegistryInstance instance =
                AiRegistryInstance.builder().aiRegistryId("airegistry-1").build();
        Client client = mock(Client.class);
        AiRegistrySkillServiceImpl service = spy(new AiRegistrySkillServiceImpl(repository));
        when(repository.findByAiRegistryId("airegistry-1")).thenReturn(Optional.of(instance));
        doReturn(client).when(service).buildClient(instance);

        TeaException teaException = new TeaException();
        teaException.setCode("InvalidParameter");
        teaException.setStatusCode(400);
        teaException.setMessage(
                "Skill package is invalid;"
                        + " uploadUrl=https://oss.example.com/upload?token=secret-token,"
                        + " {\"token\":\"json-secret\",\"accessKeyId\":\"JSON-AKID\"},"
                        + " accessKeyId=AKIDEXAMPLE, accessKeySecret=secret-value");
        doThrow(teaException)
                .when(client)
                .getSkillImportFileUrl(any(GetSkillImportFileUrlRequest.class));

        BusinessException exception =
                assertThrows(
                        BusinessException.class,
                        () ->
                                service.uploadFromZip(
                                        "airegistry-1",
                                        "namespace-1",
                                        new byte[] {1},
                                        "skill.zip",
                                        false));

        assertEquals("INVALID_PARAMETER", exception.getCode());
        assertFalse(exception.getMessage().contains("https://oss.example.com"));
        assertFalse(exception.getMessage().contains("secret-token"));
        assertFalse(exception.getMessage().contains("json-secret"));
        assertFalse(exception.getMessage().contains("JSON-AKID"));
        assertFalse(exception.getMessage().contains("AKIDEXAMPLE"));
        assertFalse(exception.getMessage().contains("secret-value"));
    }

    @Test
    void uploadFromZipKeepsUnknownFailuresGeneric() throws Exception {
        AiRegistryInstanceRepository repository = mock(AiRegistryInstanceRepository.class);
        AiRegistryInstance instance =
                AiRegistryInstance.builder().aiRegistryId("airegistry-1").build();
        Client client = mock(Client.class);
        AiRegistrySkillServiceImpl service = spy(new AiRegistrySkillServiceImpl(repository));
        when(repository.findByAiRegistryId("airegistry-1")).thenReturn(Optional.of(instance));
        doReturn(client).when(service).buildClient(instance);
        doThrow(new IOException("connection details should not be exposed"))
                .when(client)
                .getSkillImportFileUrl(any(GetSkillImportFileUrlRequest.class));

        BusinessException exception =
                assertThrows(
                        BusinessException.class,
                        () ->
                                service.uploadFromZip(
                                        "airegistry-1",
                                        "namespace-1",
                                        new byte[] {1},
                                        "skill.zip",
                                        false));

        assertEquals("INTERNAL_ERROR", exception.getCode());
        assertEquals(
                "Internal server error: Failed to upload AIRegistry Skill package",
                exception.getMessage());
        assertFalse(exception.getMessage().contains("connection details"));
    }

    @Test
    void uploadFromZipKeepsUnknownTeaFailuresGeneric() throws Exception {
        AiRegistryInstanceRepository repository = mock(AiRegistryInstanceRepository.class);
        AiRegistryInstance instance =
                AiRegistryInstance.builder().aiRegistryId("airegistry-1").build();
        Client client = mock(Client.class);
        AiRegistrySkillServiceImpl service = spy(new AiRegistrySkillServiceImpl(repository));
        when(repository.findByAiRegistryId("airegistry-1")).thenReturn(Optional.of(instance));
        doReturn(client).when(service).buildClient(instance);

        TeaException teaException = new TeaException();
        teaException.setCode("InternalError");
        teaException.setStatusCode(500);
        teaException.setMessage("Internal storage endpoint=https://internal.example.invalid");
        doThrow(teaException)
                .when(client)
                .getSkillImportFileUrl(any(GetSkillImportFileUrlRequest.class));

        BusinessException exception =
                assertThrows(
                        BusinessException.class,
                        () ->
                                service.uploadFromZip(
                                        "airegistry-1",
                                        "namespace-1",
                                        new byte[] {1},
                                        "skill.zip",
                                        false));

        assertEquals("INTERNAL_ERROR", exception.getCode());
        assertEquals(
                "Internal server error: Failed to upload AIRegistry Skill package",
                exception.getMessage());
        assertFalse(exception.getMessage().contains("internal storage"));
        assertFalse(exception.getMessage().contains("internal.example.invalid"));
    }

    private ListSkillsResponseBody.ListSkillsResponseBodyDataPageItems skill(
            String name, Long downloadCount, Map<String, String> labels) {
        return new ListSkillsResponseBody.ListSkillsResponseBodyDataPageItems()
                .setName(name)
                .setDownloadCount(downloadCount)
                .setLabels(labels);
    }

    private ListSkillsResponse response(
            List<ListSkillsResponseBody.ListSkillsResponseBodyDataPageItems> items) {
        ListSkillsResponseBody.ListSkillsResponseBodyData data =
                new ListSkillsResponseBody.ListSkillsResponseBodyData().setPageItems(items);
        return new ListSkillsResponse().setBody(new ListSkillsResponseBody().setData(data));
    }
}
