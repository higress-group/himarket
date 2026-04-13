package com.alibaba.himarket.dto.result.product;

import java.util.List;
import lombok.Data;

@Data
public class ImportProductsResult {

    private int totalCount;

    private int successCount;

    private int failureCount;

    private List<ProductImportResult> results;
}
