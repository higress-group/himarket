package com.alibaba.himarket.service;

import com.alibaba.himarket.dto.params.product.QueryProductParam;
import com.alibaba.himarket.dto.result.common.PageResult;
import com.alibaba.himarket.dto.result.product.ProductSummaryResult;
import org.springframework.data.domain.Pageable;

public interface ProductSummaryService {
    /**
     * Synchronize all fields of the specified product to the product_summary table
     * @param productId the product ID
     */
    void syncProductSummary(String productId);

    void syncAllProductSummary();

    /**
     * List API products for portal
     *
     * @param param
     * @param pageable
     * @return
     */
    PageResult<ProductSummaryResult> listPortalProducts(QueryProductParam param, Pageable pageable);
}
