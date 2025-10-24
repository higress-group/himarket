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

package com.alibaba.apiopenplatform.controller;

import com.alibaba.apiopenplatform.core.annotation.AdminAuth;
import com.alibaba.apiopenplatform.dto.params.product.CreateProductCategoryParam;
import com.alibaba.apiopenplatform.dto.result.PageResult;
import com.alibaba.apiopenplatform.dto.result.ProductCategoryResult;
import com.alibaba.apiopenplatform.service.ProductCategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

@Tag(name = "产品类别管理", description = "提供产品类别的创建、更新、删除、查询等管理功能")
@RestController
@RequestMapping("/product-categories")
@Slf4j
@RequiredArgsConstructor
public class ProductCategoryController {
    
    private final ProductCategoryService productCategoryService;
    
    @Operation(summary = "创建产品类别")
    @PostMapping
    @AdminAuth
    public ProductCategoryResult createProductCategory(@RequestBody @Valid CreateProductCategoryParam param) {
        return productCategoryService.createProductCategory(param);
    }
    
    @Operation(summary = "获取所有产品类别")
    @GetMapping
    public List<ProductCategoryResult> listProductCategories() {
        return productCategoryService.listProductCategories();
    }
    
    @Operation(summary = "分页获取产品类别列表")
    @GetMapping("/page")
    public PageResult<ProductCategoryResult> listProductCategoriesByPage(Pageable pageable) {
        return productCategoryService.listProductCategoriesByPage(pageable);
    }
    
    @Operation(summary = "更新产品类别")
    @PutMapping("/{categoryId}")
    @AdminAuth
    public ProductCategoryResult updateProductCategory(@PathVariable String categoryId, @RequestBody @Valid CreateProductCategoryParam param) {
        return productCategoryService.updateProductCategory(categoryId, param);
    }
    
    @Operation(summary = "删除产品类别")
    @DeleteMapping("/{categoryId}")
    @AdminAuth
    public void deleteProductCategory(@PathVariable String categoryId) {
        productCategoryService.deleteProductCategory(categoryId);
    }
}