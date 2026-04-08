package com.alibaba.himarket.dto.result.product;

import lombok.Data;

@Data
public class ProductImportResult {

    private String serviceName;

    private boolean success;

    private String productId;

    private String errorMessage;

    private String errorCode;
}
