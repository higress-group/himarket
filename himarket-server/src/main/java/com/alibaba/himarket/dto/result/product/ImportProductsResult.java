package com.alibaba.himarket.dto.result.product;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Data;

@Data
@Schema(description = "Product import result")
public class ImportProductsResult {

    @Schema(description = "Number of successfully imported resources", example = "1")
    private int successCount;

    @Schema(description = "Failed import items")
    private List<ProductImportResult> failures;
}
