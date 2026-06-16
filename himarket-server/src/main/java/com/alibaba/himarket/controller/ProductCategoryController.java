/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.alibaba.himarket.controller;

import com.alibaba.himarket.core.annotation.AdminAuth;
import com.alibaba.himarket.core.annotation.PublicAccess;
import com.alibaba.himarket.dto.params.category.CreateProductCategoryParam;
import com.alibaba.himarket.dto.params.category.QueryProductCategoryParam;
import com.alibaba.himarket.dto.params.category.UpdateProductCategoryParam;
import com.alibaba.himarket.dto.result.ProductCategoryResult;
import com.alibaba.himarket.dto.result.common.PageResult;
import com.alibaba.himarket.service.ProductCategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Product Category Management", description = "Product category CRUD and binding APIs")
@RestController
@RequestMapping("/product-categories")
@Slf4j
@RequiredArgsConstructor
public class ProductCategoryController {

    private final ProductCategoryService productCategoryService;

    @Operation(summary = "Create product category")
    @PostMapping
    @AdminAuth
    public ProductCategoryResult createProductCategory(
            @RequestBody @Valid CreateProductCategoryParam param) {
        return productCategoryService.createProductCategory(param);
    }

    @Operation(summary = "List product categories")
    @GetMapping
    @PublicAccess
    public PageResult<ProductCategoryResult> listProductCategories(
            QueryProductCategoryParam param, Pageable pageable) {
        return productCategoryService.listProductCategories(param, pageable);
    }

    @Operation(summary = "Update product category")
    @PutMapping("/{categoryId}")
    @AdminAuth
    public ProductCategoryResult updateProductCategory(
            @PathVariable String categoryId, @RequestBody @Valid UpdateProductCategoryParam param) {
        return productCategoryService.updateProductCategory(categoryId, param);
    }

    @Operation(summary = "Get product category")
    @GetMapping("/{categoryId}")
    @PublicAccess
    public ProductCategoryResult getProductCategory(@PathVariable String categoryId) {
        return productCategoryService.getProductCategory(categoryId);
    }

    @Operation(summary = "Delete product category")
    @DeleteMapping("/{categoryId}")
    @AdminAuth
    public void deleteProductCategory(@PathVariable String categoryId) {
        productCategoryService.deleteProductCategory(categoryId);
    }

    @Operation(summary = "Remove products from category")
    @DeleteMapping("/{categoryId}/products")
    @AdminAuth
    public void unbindProductsFromCategory(
            @PathVariable String categoryId,
            @RequestBody @Valid
                    List<@NotBlank(message = "Product ID cannot be blank") String> productIds) {
        productCategoryService.unbindProductsFromCategory(productIds, categoryId);
    }

    @Operation(summary = "Add products to category")
    @PostMapping("/{categoryId}/products")
    @AdminAuth
    public void bindProductsToCategory(
            @PathVariable String categoryId,
            @RequestBody @Valid
                    List<@NotBlank(message = "Product ID cannot be blank") String> productIds) {
        productCategoryService.bindProductsToCategory(categoryId, productIds);
    }
}
