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
     * Version status: draft, reviewing, reviewed, approved, rejected, online, offline.
     */
    private String status;

    /**
     * Download count for this version.
     */
    private Long downloadCount;

    /**
     * Version author.
     */
    private String author;

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
     * <p>For legacy "reviewing" versions, an APPROVED result resolves to "online" by default,
     * or "approved" when explicit publishing is required. A "reviewed" version always resolves
     * to "approved" on approval because review completion does not publish the version.
     *
     * <p>When pipeline is REJECTED, returns "rejected" so the UI can correctly display
     * rejection status instead of misleadingly showing reviewing status.
     */
    public static String resolveStatus(String rawStatus, String publishPipelineInfo) {
        return resolveStatus(rawStatus, publishPipelineInfo, true);
    }

    public static String resolveStatus(
            String rawStatus, String publishPipelineInfo, boolean approvedAsOnline) {
        boolean reviewed = "reviewed".equals(rawStatus);
        if ((!"reviewing".equals(rawStatus) && !reviewed) || publishPipelineInfo == null) {
            return rawStatus;
        }
        try {
            JsonNode pipeline = JsonUtil.readTree(publishPipelineInfo);
            String pipelineStatus = pipeline.path("status").asText();
            if ("APPROVED".equals(pipelineStatus)) {
                return approvedAsOnline && !reviewed ? "online" : "approved";
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
