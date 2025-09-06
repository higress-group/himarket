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
import com.alibaba.apiopenplatform.dto.params.product.*;
import com.alibaba.apiopenplatform.dto.params.product.CreateProductRefParam;
import com.alibaba.apiopenplatform.dto.result.PageResult;
import com.alibaba.apiopenplatform.dto.result.ProductPublicationResult;
import com.alibaba.apiopenplatform.dto.result.ProductRefResult;
import com.alibaba.apiopenplatform.dto.result.ProductResult;
import com.alibaba.apiopenplatform.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

@Tag(name = "API产品管理", description = "提供API产品的创建、更新、删除、查询、订阅等管理功能")
@RestController
@RequestMapping("/products")
@Slf4j
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @Operation(summary = "创建API产品")
    @PostMapping
    @AdminAuth
    public ProductResult createProduct(@RequestBody @Valid CreateProductParam param) {
        return productService.createProduct(param);
    }

    @Operation(summary = "获取API产品列表")
    @GetMapping
    public PageResult<ProductResult> listProducts(QueryProductParam param,
                                                  Pageable pageable) {
        return productService.listProducts(param, pageable);
    }

    @Operation(summary = "获取API产品详情")
    @GetMapping("/{productId}")
    public ProductResult getProduct(@PathVariable String productId) {
        return productService.getProduct(productId);
    }

    @Operation(summary = "更新API产品")
    @PutMapping("/{productId}")
    @AdminAuth
    public ProductResult updateProduct(@PathVariable String productId, @RequestBody @Valid UpdateProductParam param) {
        return productService.updateProduct(productId, param);
    }

    @Operation(summary = "发布API产品")
    @PostMapping("/{productId}/publications/{portalId}")
    @AdminAuth
    public void publishProduct(@PathVariable String productId, @PathVariable String portalId) {
        productService.publishProduct(productId, portalId);
    }

    @Operation(summary = "获取API产品的发布信息")
    @GetMapping("/{productId}/publications")
    @AdminAuth
    public PageResult<ProductPublicationResult> getPublications(@PathVariable String productId, Pageable pageable) {
        return productService.getPublications(productId, pageable);
    }

    @Operation(summary = "下线API产品")
    @DeleteMapping("/{productId}/publications/{portalId}")
    @AdminAuth
    public void unpublishProduct(@PathVariable String productId, @PathVariable String portalId) {
        productService.unpublishProduct(productId, portalId);
    }

    @Operation(summary = "删除API产品")
    @DeleteMapping("/{productId}")
    @AdminAuth
    public void deleteProduct(@PathVariable String productId) {
        productService.deleteProduct(productId);
    }

    @Operation(summary = "API产品关联API或MCP Server")
    @PostMapping("/{productId}/ref")
    @AdminAuth
    public void addProductRef(@PathVariable String productId, @RequestBody @Valid CreateProductRefParam param) throws Exception {
        productService.addProductRef(productId, param);
    }

    @Operation(summary = "获取API产品关联的API或MCP Server")
    @GetMapping("/{productId}/ref")
    public ProductRefResult getProductRef(@PathVariable String productId) {
        return productService.getProductRef(productId);
    }

    @Operation(summary = "删除API产品关联的API或MCP Server")
    @DeleteMapping("/{productId}/ref")
    @AdminAuth
    public void deleteProductRef(@PathVariable String productId) {
        productService.deleteProductRef(productId);
    }

    @Operation(summary = "获取API产品的Dashboard监控面板URL")
    @GetMapping("/{productId}/dashboard")
    public String getProductDashboard(@PathVariable String productId) {
        return productService.getProductDashboard(productId);
    }
}
