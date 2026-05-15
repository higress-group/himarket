package com.alibaba.himarket.dto.result.product;

import java.util.List;
import lombok.Data;

@Data
public class ImportProductsResult {

    private int successCount;

    private List<ProductImportResult> failures;
}
