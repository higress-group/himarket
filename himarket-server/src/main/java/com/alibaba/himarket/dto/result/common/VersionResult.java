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
     * reviewing status from the version and approved status from the pipeline result.
     *
     * <p>When pipeline is REJECTED, returns "rejected" so the UI can correctly display
     * rejection status instead of misleadingly showing reviewing status.
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
