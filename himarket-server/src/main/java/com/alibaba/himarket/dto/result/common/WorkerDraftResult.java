package com.alibaba.himarket.dto.result.common;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkerDraftResult {

    @Schema(description = "Current draft version")
    private String version;

    @Schema(description = "Full AgentSpec card JSON object")
    private JsonNode agentSpecCard;
}
