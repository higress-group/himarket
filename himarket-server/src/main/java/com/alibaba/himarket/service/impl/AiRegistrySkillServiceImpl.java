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

import cn.hutool.core.util.StrUtil;
import com.alibaba.himarket.core.exception.BusinessException;
import com.alibaba.himarket.core.exception.ErrorCode;
import com.alibaba.himarket.dto.result.airegistry.AiRegistrySkillResult;
import com.alibaba.himarket.dto.result.common.PageResult;
import com.alibaba.himarket.dto.result.common.VersionResult;
import com.alibaba.himarket.entity.AiRegistryInstance;
import com.alibaba.himarket.repository.AiRegistryInstanceRepository;
import com.alibaba.himarket.service.AiRegistrySkillService;
import com.alibaba.nacos.api.ai.model.skills.Skill;
import com.alibaba.nacos.api.ai.model.skills.SkillResource;
import com.aliyun.airegistry20260317.Client;
import com.aliyun.airegistry20260317.models.DataResourceValue;
import com.aliyun.airegistry20260317.models.DeleteSkillRequest;
import com.aliyun.airegistry20260317.models.DownloadSkillVersionViaOssRequest;
import com.aliyun.airegistry20260317.models.ForcePublishSkillVersionRequest;
import com.aliyun.airegistry20260317.models.GetSkillDetailRequest;
import com.aliyun.airegistry20260317.models.GetSkillDetailResponseBody;
import com.aliyun.airegistry20260317.models.GetSkillImportFileUrlRequest;
import com.aliyun.airegistry20260317.models.GetSkillImportFileUrlResponseBody;
import com.aliyun.airegistry20260317.models.GetSkillVersionDetailRequest;
import com.aliyun.airegistry20260317.models.GetSkillVersionDetailResponseBody;
import com.aliyun.airegistry20260317.models.ListSkillsRequest;
import com.aliyun.airegistry20260317.models.ListSkillsResponseBody;
import com.aliyun.airegistry20260317.models.OfflineSkillRequest;
import com.aliyun.airegistry20260317.models.OnlineSkillRequest;
import com.aliyun.airegistry20260317.models.PublishSkillVersionRequest;
import com.aliyun.airegistry20260317.models.SubmitSkillVersionRequest;
import com.aliyun.airegistry20260317.models.UpdateSkillLabelsRequest;
import com.aliyun.airegistry20260317.models.UploadSkillViaOssRequest;
import com.aliyun.tea.TeaException;
import com.aliyun.teaopenapi.models.Config;
import java.io.IOException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class AiRegistrySkillServiceImpl implements AiRegistrySkillService {

    private static final String DEFAULT_CONTENT_TYPE = "application/zip";
    private static final int PAGE_SIZE = 100;
    private static final String DEFAULT_ENDPOINT_TEMPLATE = "airegistry.{}.aliyuncs.com";

    private final AiRegistryInstanceRepository aiRegistryInstanceRepository;

    private final OkHttpClient httpClient =
            new OkHttpClient.Builder()
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(90, TimeUnit.SECONDS)
                    .writeTimeout(90, TimeUnit.SECONDS)
                    .build();

    @Override
    public String uploadFromZip(
            String aiRegistryId,
            String namespaceId,
            byte[] zipBytes,
            String fileName,
            boolean overwrite) {
        AiRegistryInstance instance = findInstance(aiRegistryId);
        try {
            Client client = buildClient(instance);
            GetSkillImportFileUrlResponseBody.GetSkillImportFileUrlResponseBodyData uploadInfo =
                    client.getSkillImportFileUrl(
                                    new GetSkillImportFileUrlRequest()
                                            .setNamespaceId(namespaceId)
                                            .setContentType(DEFAULT_CONTENT_TYPE))
                            .getBody()
                            .getData();
            validateUploadInfo(uploadInfo, zipBytes.length, aiRegistryId);
            putZip(uploadInfo.getUploadUrl(), uploadInfo.getContentType(), zipBytes, aiRegistryId);
            return client.uploadSkillViaOss(
                            new UploadSkillViaOssRequest()
                                    .setNamespaceId(namespaceId)
                                    .setOssObjectName(uploadInfo.getOssObjectName())
                                    .setOverwrite(overwrite)
                                    .setCommitMsg("Upload from Himarket"))
                    .getBody()
                    .getData();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn(
                    "Failed to upload AIRegistry Skill package, aiRegistryId={}, namespaceId={}",
                    aiRegistryId,
                    namespaceId,
                    e);
            throw new BusinessException(
                    ErrorCode.INTERNAL_ERROR, "Failed to upload AIRegistry Skill package");
        }
    }

    @Override
    public void deleteSkill(String aiRegistryId, String namespaceId, String skillName) {
        AiRegistryInstance instance = findInstance(aiRegistryId);
        try {
            buildClient(instance)
                    .deleteSkill(
                            new DeleteSkillRequest()
                                    .setNamespaceId(namespaceId)
                                    .setSkillName(skillName));
        } catch (Exception e) {
            throw toAiRegistryException("Failed to delete AIRegistry Skill", e);
        }
    }

    @Override
    public String submit(
            String aiRegistryId, String namespaceId, String skillName, String version) {
        AiRegistryInstance instance = findInstance(aiRegistryId);
        try {
            return buildClient(instance)
                    .submitSkillVersion(
                            new SubmitSkillVersionRequest()
                                    .setNamespaceId(namespaceId)
                                    .setSkillName(skillName)
                                    .setSkillVersion(version))
                    .getBody()
                    .getData();
        } catch (Exception e) {
            throw toAiRegistryException("Failed to submit AIRegistry Skill version", e);
        }
    }

    @Override
    public void forcePublish(
            String aiRegistryId,
            String namespaceId,
            String skillName,
            String version,
            Boolean updateLatestLabel) {
        AiRegistryInstance instance = findInstance(aiRegistryId);
        try {
            buildClient(instance)
                    .forcePublishSkillVersion(
                            new ForcePublishSkillVersionRequest()
                                    .setNamespaceId(namespaceId)
                                    .setSkillName(skillName)
                                    .setSkillVersion(version)
                                    .setUpdateLatestLabel(updateLatestLabel));
        } catch (Exception e) {
            throw toAiRegistryException("Failed to force publish AIRegistry Skill version", e);
        }
    }

    @Override
    public void publish(
            String aiRegistryId,
            String namespaceId,
            String skillName,
            String version,
            Boolean updateLatestLabel) {
        AiRegistryInstance instance = findInstance(aiRegistryId);
        try {
            buildClient(instance)
                    .publishSkillVersion(
                            new PublishSkillVersionRequest()
                                    .setNamespaceId(namespaceId)
                                    .setSkillName(skillName)
                                    .setSkillVersion(version)
                                    .setUpdateLatestLabel(updateLatestLabel));
        } catch (Exception e) {
            throw toAiRegistryException("Failed to publish AIRegistry Skill version", e);
        }
    }

    @Override
    public void changeVersionStatus(
            String aiRegistryId,
            String namespaceId,
            String skillName,
            String version,
            boolean online) {
        AiRegistryInstance instance = findInstance(aiRegistryId);
        try {
            if (online) {
                buildClient(instance)
                        .onlineSkill(
                                new OnlineSkillRequest()
                                        .setNamespaceId(namespaceId)
                                        .setSkillName(skillName)
                                        .setSkillVersion(version)
                                        .setScope("version"));
            } else {
                buildClient(instance)
                        .offlineSkill(
                                new OfflineSkillRequest()
                                        .setNamespaceId(namespaceId)
                                        .setSkillName(skillName)
                                        .setSkillVersion(version)
                                        .setScope("version"));
            }
        } catch (Exception e) {
            throw toAiRegistryException("Failed to change AIRegistry Skill version status", e);
        }
    }

    @Override
    public void setLatestVersion(
            String aiRegistryId, String namespaceId, String skillName, String version) {
        AiRegistryInstance instance = findInstance(aiRegistryId);
        try {
            buildClient(instance)
                    .updateSkillLabels(
                            new UpdateSkillLabelsRequest()
                                    .setNamespaceId(namespaceId)
                                    .setSkillName(skillName)
                                    .setLabels("{\"latest\":\"" + version + "\"}"));
        } catch (Exception e) {
            throw toAiRegistryException("Failed to update AIRegistry Skill labels", e);
        }
    }

    @Override
    public Skill getSkillVersion(
            String aiRegistryId, String namespaceId, String skillName, String version) {
        AiRegistryInstance instance = findInstance(aiRegistryId);
        try {
            Client client = buildClient(instance);
            String targetVersion =
                    StrUtil.isBlank(version)
                            ? resolveLatestVersion(client, namespaceId, skillName)
                            : version;
            GetSkillVersionDetailResponseBody.GetSkillVersionDetailResponseBodyData data =
                    client.getSkillVersionDetail(
                                    new GetSkillVersionDetailRequest()
                                            .setNamespaceId(namespaceId)
                                            .setSkillName(skillName)
                                            .setSkillVersion(targetVersion))
                            .getBody()
                            .getData();
            return toNacosSkill(data);
        } catch (Exception e) {
            throw toAiRegistryException("Failed to get AIRegistry Skill version", e);
        }
    }

    @Override
    public List<VersionResult> listVersions(
            String aiRegistryId, String namespaceId, String skillName) {
        AiRegistryInstance instance = findInstance(aiRegistryId);
        try {
            GetSkillDetailResponseBody.GetSkillDetailResponseBodyData data =
                    buildClient(instance)
                            .getSkillDetail(
                                    new GetSkillDetailRequest()
                                            .setNamespaceId(namespaceId)
                                            .setSkillName(skillName))
                            .getBody()
                            .getData();
            if (data == null || data.getVersions() == null) {
                return List.of();
            }
            String latestVersion = data.getLabels() == null ? null : data.getLabels().get("latest");
            return data.getVersions().stream()
                    .sorted(
                            Comparator.comparing(
                                            GetSkillDetailResponseBody
                                                            .GetSkillDetailResponseBodyDataVersions
                                                    ::getCreateTime,
                                            Comparator.nullsLast(Long::compareTo))
                                    .reversed())
                    .map(version -> toVersionResult(version, latestVersion))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            throw toAiRegistryException("Failed to list AIRegistry Skill versions", e);
        }
    }

    @Override
    public byte[] downloadZip(
            String aiRegistryId, String namespaceId, String skillName, String version) {
        AiRegistryInstance instance = findInstance(aiRegistryId);
        try {
            Client client = buildClient(instance);
            String targetVersion =
                    StrUtil.isBlank(version)
                            ? resolveLatestVersion(client, namespaceId, skillName)
                            : version;
            String downloadUrl =
                    client.downloadSkillVersionViaOss(
                                    new DownloadSkillVersionViaOssRequest()
                                            .setNamespaceId(namespaceId)
                                            .setSkillName(skillName)
                                            .setSkillVersion(targetVersion))
                            .getBody()
                            .getData();
            if (StrUtil.isBlank(downloadUrl)) {
                throw new BusinessException(
                        ErrorCode.INTERNAL_ERROR, "AIRegistry download URL is empty");
            }
            Request request = new Request.Builder().url(downloadUrl).get().build();
            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) {
                    throw new BusinessException(
                            ErrorCode.INTERNAL_ERROR, "Failed to download AIRegistry Skill");
                }
                return response.body().bytes();
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw toAiRegistryException("Failed to download AIRegistry Skill", e);
        }
    }

    @Override
    public PageResult<AiRegistrySkillResult> listSkills(
            String aiRegistryId, String namespaceId, int pageNo, int pageSize) {
        AiRegistryInstance instance = findInstance(aiRegistryId);
        try {
            ListSkillsResponseBody.ListSkillsResponseBodyData data =
                    buildClient(instance)
                            .listSkills(
                                    new ListSkillsRequest()
                                            .setNamespaceId(namespaceId)
                                            .setPageNo(pageNo)
                                            .setPageSize(pageSize))
                            .getBody()
                            .getData();
            if (data == null || data.getPageItems() == null) {
                return PageResult.empty(pageNo, pageSize);
            }
            List<AiRegistrySkillResult> items =
                    data.getPageItems().stream()
                            .map(
                                    item ->
                                            AiRegistrySkillResult.builder()
                                                    .name(item.getName())
                                                    .description(item.getDescription())
                                                    .downloadCount(item.getDownloadCount())
                                                    .build())
                            .collect(Collectors.toList());
            long total =
                    data.getTotalCount() == null ? items.size() : data.getTotalCount().longValue();
            return PageResult.of(items, pageNo, pageSize, total);
        } catch (Exception e) {
            throw toAiRegistryException("Failed to list AIRegistry Skills", e);
        }
    }

    @Override
    public Map<String, Long> listSkillDownloadCounts(String aiRegistryId, String namespaceId) {
        AiRegistryInstance instance = findInstance(aiRegistryId);
        try {
            Client client = buildClient(instance);
            Map<String, Long> downloadCounts = new HashMap<>();
            int pageNo = 1;
            while (true) {
                ListSkillsResponseBody.ListSkillsResponseBodyData data =
                        client.listSkills(
                                        new ListSkillsRequest()
                                                .setNamespaceId(namespaceId)
                                                .setPageNo(pageNo)
                                                .setPageSize(PAGE_SIZE))
                                .getBody()
                                .getData();
                if (data == null || data.getPageItems() == null || data.getPageItems().isEmpty()) {
                    break;
                }
                for (ListSkillsResponseBody.ListSkillsResponseBodyDataPageItems item :
                        data.getPageItems()) {
                    if (item.getDownloadCount() != null) {
                        downloadCounts.putIfAbsent(item.getName(), item.getDownloadCount());
                    }
                }
                if (data.getPageItems().size() < PAGE_SIZE) {
                    break;
                }
                pageNo++;
            }
            return downloadCounts;
        } catch (Exception e) {
            throw toAiRegistryException("Failed to list AIRegistry Skill download counts", e);
        }
    }

    @Override
    public void validateNamespace(String aiRegistryId, String namespaceId) {
        AiRegistryInstance instance = findInstance(aiRegistryId);
        try {
            buildClient(instance)
                    .listSkills(
                            new ListSkillsRequest()
                                    .setNamespaceId(namespaceId)
                                    .setPageNo(1)
                                    .setPageSize(1));
        } catch (Exception e) {
            throw toAiRegistryException("Failed to validate AIRegistry namespace", e);
        }
    }

    private AiRegistryInstance findInstance(String aiRegistryId) {
        return aiRegistryInstanceRepository
                .findByAiRegistryId(aiRegistryId)
                .orElseThrow(
                        () ->
                                new BusinessException(
                                        ErrorCode.NOT_FOUND, "AiRegistryInstance", aiRegistryId));
    }

    private Client buildClient(AiRegistryInstance instance) throws Exception {
        Config config =
                new Config()
                        .setAccessKeyId(instance.getAccessKeyId())
                        .setAccessKeySecret(instance.getAccessKeySecret())
                        .setSecurityToken(instance.getSecurityToken())
                        .setRegionId(instance.getRegionId());
        config.setEndpoint(resolveEndpoint(instance));
        return new Client(config);
    }

    private String resolveEndpoint(AiRegistryInstance instance) {
        if (StrUtil.isNotBlank(instance.getEndpoint())) {
            return StrUtil.trim(instance.getEndpoint());
        }
        return StrUtil.format(DEFAULT_ENDPOINT_TEMPLATE, StrUtil.trim(instance.getRegionId()));
    }

    private String resolveLatestVersion(Client client, String namespaceId, String skillName)
            throws Exception {
        GetSkillDetailResponseBody.GetSkillDetailResponseBodyData data =
                client.getSkillDetail(
                                new GetSkillDetailRequest()
                                        .setNamespaceId(namespaceId)
                                        .setSkillName(skillName))
                        .getBody()
                        .getData();
        if (data == null || data.getVersions() == null || data.getVersions().isEmpty()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Skill", skillName);
        }
        if (data.getLabels() != null && StrUtil.isNotBlank(data.getLabels().get("latest"))) {
            return data.getLabels().get("latest");
        }
        return data.getVersions().stream()
                .sorted(
                        Comparator.comparing(
                                        GetSkillDetailResponseBody
                                                        .GetSkillDetailResponseBodyDataVersions
                                                ::getCreateTime,
                                        Comparator.nullsLast(Long::compareTo))
                                .reversed())
                .map(GetSkillDetailResponseBody.GetSkillDetailResponseBodyDataVersions::getVersion)
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Skill", skillName));
    }

    private Skill toNacosSkill(
            GetSkillVersionDetailResponseBody.GetSkillVersionDetailResponseBodyData data) {
        Skill skill = new Skill();
        if (data == null) {
            return skill;
        }
        skill.setName(data.getName());
        skill.setNamespaceId(data.getNamespaceId());
        skill.setDescription(data.getDescription());
        skill.setSkillMd(data.getSkillMd());
        if (data.getResource() != null) {
            Map<String, SkillResource> resources = new HashMap<>();
            data.getResource().forEach((key, value) -> resources.put(key, toNacosResource(value)));
            skill.setResource(resources);
        }
        return skill;
    }

    private SkillResource toNacosResource(DataResourceValue value) {
        SkillResource resource = new SkillResource();
        resource.setName(value.getName());
        resource.setType(value.getType());
        resource.setContent(value.getContent());
        if (value.getMetadata() != null) {
            resource.setMetadata(new HashMap<>(value.getMetadata()));
        }
        return resource;
    }

    private VersionResult toVersionResult(
            GetSkillDetailResponseBody.GetSkillDetailResponseBodyDataVersions version,
            String latestVersion) {
        return VersionResult.builder()
                .version(version.getVersion())
                .status(
                        VersionResult.resolveStatus(
                                version.getStatus(), version.getPublishPipelineInfo(), false))
                .updateTime(version.getUpdateTime())
                .downloadCount(version.getDownloadCount())
                .publishPipelineInfo(version.getPublishPipelineInfo())
                .isLatest(version.getVersion().equals(latestVersion))
                .build();
    }

    private void validateUploadInfo(
            GetSkillImportFileUrlResponseBody.GetSkillImportFileUrlResponseBodyData uploadInfo,
            int fileSize,
            String aiRegistryId) {
        if (uploadInfo == null
                || StrUtil.isBlank(uploadInfo.getUploadUrl())
                || StrUtil.isBlank(uploadInfo.getOssObjectName())) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "AIRegistry upload URL is empty");
        }
        if (StrUtil.isNotBlank(uploadInfo.getMaxSize())) {
            long maxSize = Long.parseLong(uploadInfo.getMaxSize());
            if (fileSize > maxSize) {
                log.warn(
                        "AIRegistry Skill package exceeds max size, aiRegistryId={}, maxSize={}",
                        aiRegistryId,
                        maxSize);
                throw new BusinessException(
                        ErrorCode.INVALID_PARAMETER,
                        "ZIP file exceeds AIRegistry upload size limit");
            }
        }
    }

    private void putZip(String uploadUrl, String contentType, byte[] zipBytes, String aiRegistryId)
            throws IOException {
        String resolvedContentType =
                StrUtil.isBlank(contentType) ? DEFAULT_CONTENT_TYPE : contentType;
        Request request =
                new Request.Builder()
                        .url(uploadUrl)
                        .put(RequestBody.create(zipBytes, MediaType.parse(resolvedContentType)))
                        .header("Content-Type", resolvedContentType)
                        .build();
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                log.warn(
                        "Failed to PUT AIRegistry Skill package, aiRegistryId={}, status={}",
                        aiRegistryId,
                        response.code());
                throw new BusinessException(
                        ErrorCode.INTERNAL_ERROR, "Failed to upload AIRegistry Skill package");
            }
        }
    }

    private BusinessException toAiRegistryException(String message, Exception e) {
        if (e instanceof BusinessException businessException) {
            return businessException;
        }
        if (e instanceof TeaException teaException) {
            ErrorCode errorCode = mapTeaErrorCode(teaException);
            String code = StrUtil.blankToDefault(teaException.getCode(), "Unknown");
            log.warn(
                    "{}: AIRegistry error code={}, status={}",
                    message,
                    code,
                    teaException.getStatusCode());
            return new BusinessException(errorCode, message + ": " + code);
        }
        log.warn("{}", message, e);
        return new BusinessException(ErrorCode.INTERNAL_ERROR, message);
    }

    private ErrorCode mapTeaErrorCode(TeaException e) {
        String code = StrUtil.blankToDefault(e.getCode(), "").toLowerCase();
        if (code.contains("notfound") || code.contains("not_found")) {
            return ErrorCode.NOT_FOUND;
        }
        if (code.contains("alreadyexist")
                || code.contains("already_exists")
                || code.contains("conflict")) {
            return ErrorCode.CONFLICT;
        }
        if (code.contains("invalidparameter") || code.contains("invalid_parameter")) {
            return ErrorCode.INVALID_PARAMETER;
        }
        if (code.contains("accessdenied")
                || code.contains("forbidden")
                || code.contains("unauthorized")
                || code.contains("nopermission")
                || Integer.valueOf(401).equals(e.getStatusCode())
                || Integer.valueOf(403).equals(e.getStatusCode())) {
            return ErrorCode.INVALID_REQUEST;
        }
        return ErrorCode.INTERNAL_ERROR;
    }
}
