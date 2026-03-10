package com.alibaba.himarket.dto.params.product;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UpdateSkillNacosParam {

    @NotBlank(message = "nacosId cannot be blank")
    private String nacosId;

    @NotBlank(message = "namespace cannot be blank")
    private String namespace;
}
