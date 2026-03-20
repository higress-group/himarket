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

import com.alibaba.himarket.dto.params.product.CreateProductLikeParam;
import com.alibaba.himarket.dto.result.product.ProductLikeResult;
import com.alibaba.himarket.service.ProductLikeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/product-like")
@Tag(name = "产品点赞", description = "产品点赞相关接口")
public class ProductLikeController {

    @Autowired private ProductLikeService productLikeService;

    /**
     * Like/unlike a product
     */
    @PostMapping
    @Operation(summary = "点赞/取消点赞产品")
    public ProductLikeResult toggleLike(@RequestBody CreateProductLikeParam param) {
        ProductLikeResult result = productLikeService.toggleLike(param.getProductId());
        return result;
    }

    /**
     * Check if current user has liked a product
     */
    @GetMapping("/{productId}/status")
    @Operation(summary = "查询当前用户是否已点赞产品")
    public ProductLikeResult isLiked(@PathVariable String productId) {
        return productLikeService.isLiked(productId);
    }
}
