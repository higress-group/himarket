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

package com.alibaba.himarket.service;

import com.alibaba.himarket.dto.result.airegistry.AiRegistrySkillResult;
import com.alibaba.himarket.dto.result.common.PageResult;
import com.alibaba.himarket.dto.result.common.VersionResult;
import com.alibaba.nacos.api.ai.model.skills.Skill;
import java.util.List;
import java.util.Map;

public interface AiRegistrySkillService {

    String uploadFromZip(
            String aiRegistryId,
            String namespaceId,
            byte[] zipBytes,
            String fileName,
            boolean overwrite);

    void deleteSkill(String aiRegistryId, String namespaceId, String skillName);

    String submit(String aiRegistryId, String namespaceId, String skillName, String version);

    void forcePublish(
            String aiRegistryId,
            String namespaceId,
            String skillName,
            String version,
            Boolean updateLatestLabel);

    void publish(
            String aiRegistryId,
            String namespaceId,
            String skillName,
            String version,
            Boolean updateLatestLabel);

    void changeVersionStatus(
            String aiRegistryId,
            String namespaceId,
            String skillName,
            String version,
            boolean online);

    void setLatestVersion(
            String aiRegistryId, String namespaceId, String skillName, String version);

    Skill getSkillVersion(
            String aiRegistryId, String namespaceId, String skillName, String version);

    List<VersionResult> listVersions(String aiRegistryId, String namespaceId, String skillName);

    byte[] downloadZip(String aiRegistryId, String namespaceId, String skillName, String version);

    PageResult<AiRegistrySkillResult> listSkills(
            String aiRegistryId, String namespaceId, int pageNo, int pageSize);

    Map<String, Long> listSkillDownloadCounts(String aiRegistryId, String namespaceId);

    void validateNamespace(String aiRegistryId, String namespaceId);
}
