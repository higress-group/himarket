package com.alibaba.himarket.dto.params.worker;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateWorkerDraftParam {

    @Schema(description = "Base version to copy from", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "Base version cannot be blank")
    private String baseVersion;
}
