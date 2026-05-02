package com.alibaba.himarket.dto.params.product;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class BindNacosParam {

    @NotBlank(message = "NacosId cannot be blank")
    private String nacosId;

    @NotBlank(message = "Namespace cannot be blank")
    private String namespace;
}
