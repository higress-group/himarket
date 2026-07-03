package com.alibaba.himarket.dto.params.skill;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateSkillDraftParam {

    @Schema(description = "Base version to copy from", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "Base version cannot be blank")
    private String baseVersion;

    @Schema(description = "New draft version", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "Version cannot be blank")
    private String version;
}
