package com.alibaba.himarket.dto.result.product;

import com.alibaba.himarket.dto.converter.OutputConverter;
import com.alibaba.himarket.dto.result.ProductCategoryResult;
import com.alibaba.himarket.entity.ProductSummary;
import com.alibaba.himarket.support.enums.ProductType;
import com.alibaba.himarket.support.product.Icon;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;

@Data
public class ProductSummaryResult implements OutputConverter<ProductSummaryResult, ProductSummary> {
    private String productId;

    private String name;

    private String description;

    private ProductType type;

    private String document;

    private Icon icon;

    private List<ProductCategoryResult> categories;

    private LocalDateTime createAt;

    private LocalDateTime updatedAt;

    private Boolean isSubscribed;

    private Integer subscriptionCount = 0;
    private Integer usageCount = 0;
    private Integer likesCount = 0;
}
