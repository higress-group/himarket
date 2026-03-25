package com.alibaba.himarket.dto.result.common;

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
}
