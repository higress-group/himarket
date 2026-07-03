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
public class SkillDraftResult {

    @Schema(description = "Current draft version")
    private String version;

    @Schema(description = "Full SkillCard JSON object")
    private JsonNode skillCard;
}
