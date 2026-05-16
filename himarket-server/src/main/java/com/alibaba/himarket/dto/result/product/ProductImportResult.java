package com.alibaba.himarket.dto.result.product;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "Failed product import item")
public class ProductImportResult {

    @Schema(description = "Resource name", example = "Fetch web content")
    private String resourceName;

    @Schema(
            description = "Failure reason",
            example = "Product with name 'Fetch web content' already exists")
    private String errorMessage;
}
