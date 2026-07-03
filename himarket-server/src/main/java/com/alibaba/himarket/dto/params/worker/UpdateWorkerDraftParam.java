package com.alibaba.himarket.dto.params.worker;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateWorkerDraftParam {

    @Schema(
            description = "Full AgentSpec card JSON object",
            requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "AgentSpec card cannot be null")
    private JsonNode agentSpecCard;
}
