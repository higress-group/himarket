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
import com.alibaba.himarket.core.security.ContextHolder;
import com.alibaba.himarket.dto.params.product.*;
import com.alibaba.himarket.dto.params.product.CreateProductParam;
import com.alibaba.himarket.dto.params.product.CreateProductRefParam;
import com.alibaba.himarket.dto.params.product.PublishProductParam;
import com.alibaba.himarket.dto.params.product.QueryProductParam;
import com.alibaba.himarket.dto.params.product.QueryProductSubscriptionParam;
import com.alibaba.himarket.dto.params.product.UpdateProductParam;
import com.alibaba.himarket.dto.result.ProductCategoryResult;
import com.alibaba.himarket.dto.result.common.PageResult;
import com.alibaba.himarket.dto.result.mcp.McpMetaPublicResult;
import com.alibaba.himarket.dto.result.mcp.McpMetaResult;
import com.alibaba.himarket.dto.result.mcp.McpToolListResult;
import com.alibaba.himarket.dto.result.product.*;
import com.alibaba.himarket.service.McpServerService;
import com.alibaba.himarket.service.ProductCategoryService;
import com.alibaba.himarket.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

@Tag(name = "API产品管理", description = "提供API产品的创建、更新、删除、查询、订阅等管理功能")
@RestController
@RequestMapping("/products")
@Slf4j
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    private final ProductCategoryService productCategoryService;

    private final McpServerService mcpServerService;

    private final ContextHolder contextHolder;

    @Operation(summary = "创建API产品")
    @PostMapping
    @AdminAuth
    public ProductResult createProduct(@RequestBody @Valid CreateProductParam param) {
        return productService.createProduct(param);
    }

    @Operation(summary = "获取API产品列表")
    @GetMapping
    @PublicAccess
    public PageResult<ProductResult> listProducts(QueryProductParam param, Pageable pageable) {
        return productService.listProducts(param, pageable);
    }

    @Operation(summary = "获取API产品详情")
    @GetMapping("/{productId}")
    @PublicAccess
    public ProductResult getProduct(@PathVariable String productId) {
        return productService.getProduct(productId);
    }

    @Operation(summary = "更新API产品")
    @PutMapping("/{productId}")
    @AdminAuth
    public ProductResult updateProduct(
            @PathVariable String productId, @RequestBody @Valid UpdateProductParam param) {
        return productService.updateProduct(productId, param);
    }

    @Operation(summary = "发布API产品")
    @PostMapping("/{productId}/publications")
    @AdminAuth
    public void publishProduct(
            @PathVariable String productId, @RequestBody @Valid PublishProductParam param) {
        productService.publishProduct(productId, param.getPortalId());
    }

    @Operation(summary = "获取API产品的发布信息")
    @GetMapping("/{productId}/publications")
    @AdminAuth
    public PageResult<ProductPublicationResult> getPublications(
            @PathVariable String productId, Pageable pageable) {
        return productService.getPublications(productId, pageable);
    }

    @Operation(summary = "下线API产品")
    @DeleteMapping("/{productId}/publications/{publicationId}")
    @AdminAuth
    public void unpublishProduct(
            @PathVariable String productId, @PathVariable String publicationId) {
        productService.unpublishProduct(productId, publicationId);
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
    public void addProductRef(
            @PathVariable String productId, @RequestBody @Valid CreateProductRefParam param) {
        productService.addProductRef(productId, param);
    }

    @Operation(summary = "获取API产品关联的API或MCP Server")
    @GetMapping("/{productId}/ref")
    @PublicAccess
    public ProductRefResult getProductRef(@PathVariable String productId) {
        return productService.getProductRef(productId);
    }

    @Operation(summary = "获取产品关联的 MCP 元信息")
    @GetMapping("/{productId}/mcp-meta")
    public List<McpMetaResult> listMcpMeta(@PathVariable String productId) {
        List<McpMetaResult> results = mcpServerService.listMetaByProduct(productId);
        if (!contextHolder.isAdministrator()) {
            results.forEach(McpMetaResult::sanitize);
        }
        return results;
    }

    @Operation(summary = "获取产品关联的 MCP 公开信息（匿名可访问，脱敏）")
    @GetMapping("/{productId}/mcp-meta/public")
    @PublicAccess
    public List<McpMetaPublicResult> listMcpMetaPublic(@PathVariable String productId) {
        return mcpServerService.listMetaByProduct(productId).stream()
                .map(McpMetaPublicResult::fromFull)
                .collect(java.util.stream.Collectors.toList());
    }

    @Operation(summary = "删除API产品关联的API或MCP Server")
    @DeleteMapping("/{productId}/ref")
    @AdminAuth
    public void deleteProductRef(@PathVariable String productId) {
        productService.deleteProductRef(productId);
    }

    @Operation(summary = "获取产品的订阅列表")
    @GetMapping("/{productId}/subscriptions")
    @AdminOrDeveloperAuth
    public PageResult<SubscriptionResult> listProductSubscriptions(
            @PathVariable String productId,
            QueryProductSubscriptionParam param,
            Pageable pageable) {
        return productService.listProductSubscriptions(productId, param, pageable);
    }

    @Operation(summary = "获取产品关联的类别")
    @GetMapping("/{productId}/categories")
    @AdminOrDeveloperAuth
    public List<ProductCategoryResult> getProductCategories(@PathVariable String productId) {
        return productCategoryService.listCategoriesForProduct(productId);
    }

    @Operation(summary = "设置产品类别")
    @PostMapping("/{productId}/categories")
    @AdminAuth
    public void setProductCategories(
            @PathVariable String productId, @RequestBody List<String> categoryIds) {
        productService.setProductCategories(productId, categoryIds);
    }

    @Operation(summary = "重新加载API产品配置")
    @PutMapping("/{productId}/configurations")
    @AdminAuth
    public void reloadProductConfig(@PathVariable String productId) {
        productService.reloadProductConfig(productId);
    }

    @Operation(summary = "获取MCP服务的工具详情")
    @GetMapping("/{productId}/tools")
    @PublicAccess
    public McpToolListResult listMcpTools(@PathVariable String productId) {
        return productService.listMcpTools(productId);
    }

    @Operation(summary = "更新 Skill 的 Nacos 关联")
    @PutMapping("/{productId}/skill-nacos")
    @AdminAuth
    public void updateSkillNacos(
            @PathVariable String productId, @RequestBody @Valid BindNacosParam param) {
        productService.bindProductNacos(productId, param);
    }

    @Operation(summary = "更新 Worker 的 Nacos 关联")
    @PutMapping("/{productId}/worker-nacos")
    @AdminAuth
    public void updateWorkerNacos(
            @PathVariable String productId, @RequestBody @Valid BindNacosParam param) {
        productService.bindProductNacos(productId, param);
    }

    @Operation(summary = "批量导入AI API资源为产品")
    @PostMapping("/import")
    @AdminAuth
    public ImportProductsResult importProducts(@RequestBody @Valid ImportProductsParam param) {
        return productService.importProducts(param);
    }
}
