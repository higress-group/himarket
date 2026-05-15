package com.alibaba.himarket.dto.params.product;

import com.alibaba.himarket.support.enums.SourceType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateProductSourceParam {

    @NotNull(message = "Source type cannot be null")
    private SourceType sourceType;

    @NotBlank(message = "NacosId cannot be blank")
    private String nacosId;

    @NotBlank(message = "Namespace cannot be blank")
    private String namespace;
}
