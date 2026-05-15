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
import com.alibaba.himarket.core.annotation.AdminOrDeveloperAuth;
import com.alibaba.himarket.core.annotation.PublicAccess;
import com.alibaba.himarket.dto.params.product.*;
import com.alibaba.himarket.dto.params.product.AddProductRefParam;
import com.alibaba.himarket.dto.params.product.CreateProductParam;
import com.alibaba.himarket.dto.params.product.PublishProductParam;
import com.alibaba.himarket.dto.params.product.QueryProductParam;
import com.alibaba.himarket.dto.params.product.QueryProductSubscriptionParam;
import com.alibaba.himarket.dto.params.product.UpdateProductParam;
import com.alibaba.himarket.dto.result.ProductCategoryResult;
import com.alibaba.himarket.dto.result.common.PageResult;
import com.alibaba.himarket.dto.result.mcp.McpToolListResult;
import com.alibaba.himarket.dto.result.product.*;
import com.alibaba.himarket.service.ProductCategoryService;
import com.alibaba.himarket.service.ProductService;
import com.alibaba.himarket.service.importer.ProductImporter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

@Tag(
        name = "API Product Management",
        description =
                "Create, update, delete, query, publish, and manage subscriptions for API products")
@RestController
@RequestMapping("/products")
@Slf4j
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    private final ProductCategoryService productCategoryService;

    private final ProductImporter productImporter;

    @Operation(summary = "Create API product")
    @PostMapping
    @AdminAuth
    public ProductResult createProduct(@RequestBody @Valid CreateProductParam param) {
        return productService.createProduct(param);
    }

    @Operation(summary = "List API products")
    @GetMapping
    @PublicAccess
    public PageResult<ProductResult> listProducts(QueryProductParam param, Pageable pageable) {
        return productService.listProducts(param, pageable);
    }

    @Operation(summary = "Get API product details")
    @GetMapping("/{productId}")
    @PublicAccess
    public ProductResult getProduct(@PathVariable String productId) {
        return productService.getProduct(productId);
    }

    @Operation(summary = "Update API product")
    @PutMapping("/{productId}")
    @AdminAuth
    public ProductResult updateProduct(
            @PathVariable String productId, @RequestBody @Valid UpdateProductParam param) {
        return productService.updateProduct(productId, param);
    }

    @Operation(summary = "Publish API product")
    @PostMapping("/{productId}/publications")
    @AdminAuth
    public void publishProduct(
            @PathVariable String productId, @RequestBody @Valid PublishProductParam param) {
        productService.publishProduct(productId, param.getPortalId());
    }

    @Operation(summary = "List API product publications")
    @GetMapping("/{productId}/publications")
    @AdminAuth
    public PageResult<ProductPublicationResult> getPublications(
            @PathVariable String productId, Pageable pageable) {
        return productService.getPublications(productId, pageable);
    }

    @Operation(summary = "Unpublish API product")
    @DeleteMapping("/{productId}/publications/{publicationId}")
    @AdminAuth
    public void unpublishProduct(
            @PathVariable String productId, @PathVariable String publicationId) {
        productService.unpublishProduct(productId, publicationId);
    }

    @Operation(summary = "Delete API product")
    @DeleteMapping("/{productId}")
    @AdminAuth
    public void deleteProduct(@PathVariable String productId) {
        productService.deleteProduct(productId);
    }

    @Operation(summary = "Create or replace API product reference")
    @PutMapping("/{productId}/ref")
    @AdminAuth
    public void addProductRef(
            @PathVariable String productId, @RequestBody @Valid AddProductRefParam param) {
        productService.addProductRef(productId, param);
    }

    @Operation(summary = "Get API product reference")
    @GetMapping("/{productId}/ref")
    @PublicAccess
    public ProductRefResult getProductRef(@PathVariable String productId) {
        return productService.getProductRef(productId);
    }

    @Operation(summary = "Delete API product reference")
    @DeleteMapping("/{productId}/ref")
    @AdminAuth
    public void deleteProductRef(@PathVariable String productId) {
        productService.deleteProductRef(productId);
    }

    @Operation(summary = "List product subscriptions")
    @GetMapping("/{productId}/subscriptions")
    @AdminOrDeveloperAuth
    public PageResult<SubscriptionResult> listProductSubscriptions(
            @PathVariable String productId,
            QueryProductSubscriptionParam param,
            Pageable pageable) {
        return productService.listProductSubscriptions(productId, param, pageable);
    }

    @Operation(summary = "List product categories")
    @GetMapping("/{productId}/categories")
    @AdminOrDeveloperAuth
    public List<ProductCategoryResult> getProductCategories(@PathVariable String productId) {
        return productCategoryService.listCategoriesForProduct(productId);
    }

    @Operation(summary = "Set product categories")
    @PutMapping("/{productId}/categories")
    @AdminAuth
    public void setProductCategories(
            @PathVariable String productId, @RequestBody List<String> categoryIds) {
        productService.setProductCategories(productId, categoryIds);
    }

    @Operation(summary = "Reload API product configuration")
    @PostMapping("/{productId}/configurations/reload")
    @AdminAuth
    public void reloadProductConfig(@PathVariable String productId) {
        productService.reloadProductConfig(productId);
    }

    @Operation(summary = "List MCP tools for product")
    @GetMapping("/{productId}/tools")
    @PublicAccess
    public McpToolListResult listMcpTools(@PathVariable String productId) {
        return productService.listMcpTools(productId);
    }

    @Operation(summary = "Update product source")
    @PutMapping("/{productId}/source")
    @AdminAuth
    public void updateProductSource(
            @PathVariable String productId, @RequestBody @Valid UpdateProductSourceParam param) {
        productService.updateProductSource(productId, param);
    }

    @Operation(summary = "Import AI API resources as products")
    @PostMapping("/import")
    @AdminAuth
    public ImportProductsResult importProducts(@RequestBody @Valid ImportProductsParam param) {
        return productImporter.importProducts(param);
    }
}
