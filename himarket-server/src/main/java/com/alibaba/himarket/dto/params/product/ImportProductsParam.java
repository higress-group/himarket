package com.alibaba.himarket.dto.params.product;

import com.alibaba.himarket.support.enums.ProductType;
import com.alibaba.himarket.support.enums.SourceType;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.Data;

@Data
public class ImportProductsParam {

    @NotNull(message = "Product type cannot be null")
    private ProductType productType;

    @NotNull(message = "Source type cannot be null")
    private SourceType sourceType;

    private String gatewayId;

    private String nacosId;

    private String namespaceId;

    @NotEmpty(message = "Services cannot be empty")
    private List<ServiceIdentifier> services;

    private List<String> categories;
}
