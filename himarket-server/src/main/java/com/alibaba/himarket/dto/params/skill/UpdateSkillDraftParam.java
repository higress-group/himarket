package com.alibaba.himarket.dto.params.skill;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateSkillDraftParam {

    @Schema(description = "Full SkillCard JSON object", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "Skill card cannot be null")
    private JsonNode skillCard;
}
