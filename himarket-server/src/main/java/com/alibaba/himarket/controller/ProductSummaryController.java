package com.alibaba.himarket.controller;

import com.alibaba.himarket.core.annotation.AdminAuth;
import com.alibaba.himarket.dto.params.product.QueryProductParam;
import com.alibaba.himarket.dto.result.common.PageResult;
import com.alibaba.himarket.dto.result.product.ProductSummaryResult;
import com.alibaba.himarket.service.ProductSummaryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "产品统计", description = "产品统计信息管理")
@RestController
@RequestMapping("/product-summary")
@RequiredArgsConstructor
public class ProductSummaryController {

    private final ProductSummaryService productSummaryService;

    @Operation(summary = "获取API产品列表")
    @GetMapping
    public PageResult<ProductSummaryResult> getProductSummaryList(
            @Valid QueryProductParam param, Pageable pageable) {
        return productSummaryService.listPortalProducts(param, pageable);
    }

    @Operation(summary = "同步产品统计数据", description = "手动触发产品统计数据同步")
    @PostMapping("/sync")
    @AdminAuth
    public void syncAllProductSummary() {
        productSummaryService.syncAllProductSummary();
    }
}
