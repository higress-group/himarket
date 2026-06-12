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

package com.alibaba.himarket.dto.result.common;

import com.alibaba.himarket.utils.JsonUtil;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VersionResult {

    private String version;

    private Long updateTime;

    /**
     * Version status: draft, reviewing, online, offline.
     */
    private String status;

    /**
     * Download count for this version.
     */
    private Long downloadCount;

    /**
     * Pipeline info (JSON string from Nacos, null when no pipeline configured).
     */
    private String publishPipelineInfo;

    /**
     * Whether this version is labeled as "latest".
     */
    private Boolean isLatest;

    /**
     * Resolves effective version status by reconciling raw version status with pipeline result.
     *
     * <p>When pipeline is APPROVED but Nacos hasn't yet transitioned version status to "online",
     * returns "online" to eliminate the inconsistency window where the UI would simultaneously show
     * "审核中" (from version status) and "审核通过" (from pipeline result).
     *
     * <p>When pipeline is REJECTED, returns "rejected" so the UI can correctly display
     * "审核不通过" instead of misleadingly showing "审核中".
     */
    public static String resolveStatus(String rawStatus, String publishPipelineInfo) {
        return resolveStatus(rawStatus, publishPipelineInfo, true);
    }

    public static String resolveStatus(
            String rawStatus, String publishPipelineInfo, boolean approvedAsOnline) {
        if (!"reviewing".equals(rawStatus) || publishPipelineInfo == null) {
            return rawStatus;
        }
        try {
            JsonNode pipeline = JsonUtil.readTree(publishPipelineInfo);
            String pipelineStatus = pipeline.path("status").asText();
            if ("APPROVED".equals(pipelineStatus)) {
                return approvedAsOnline ? "online" : "approved";
            }
            if ("REJECTED".equals(pipelineStatus)) {
                return "rejected";
            }
        } catch (Exception ignored) {
            // Malformed pipeline info, fall through to raw status
        }
        return rawStatus;
    }
}
