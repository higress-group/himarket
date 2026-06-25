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

package com.alibaba.himarket.service;

import com.alibaba.himarket.dto.params.category.CreateProductCategoryParam;
import com.alibaba.himarket.dto.params.category.QueryProductCategoryParam;
import com.alibaba.himarket.dto.params.category.UpdateProductCategoryParam;
import com.alibaba.himarket.dto.result.ProductCategoryResult;
import com.alibaba.himarket.dto.result.common.PageResult;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Pageable;

public interface ProductCategoryService {

    /**
     * Create a product category.
     *
     * @param param product category creation parameters
     * @return created product category information
     */
    ProductCategoryResult createProductCategory(CreateProductCategoryParam param);

    /**
     * List all product categories.
     *
     * @param param product category query parameters
     * @param pageable pagination parameters
     * @return paged product category results
     */
    PageResult<ProductCategoryResult> listProductCategories(
            QueryProductCategoryParam param, Pageable pageable);

    /**
     * Delete a product category.
     *
     * @param categoryId product category ID
     */
    void deleteProductCategory(String categoryId);

    /**
     * Get the detailed information of a product category.
     *
     * @param categoryId product category ID
     * @return product category information
     */
    ProductCategoryResult getProductCategory(String categoryId);

    /**
     * Update a product category.
     *
     * @param categoryId product category ID
     * @param param product category update parameters
     * @return updated product category information
     */
    ProductCategoryResult updateProductCategory(
            String categoryId, UpdateProductCategoryParam param);

    /**
     * List all product categories for a product.
     *
     * @param productId product ID
     * @return product category results
     */
    List<ProductCategoryResult> listCategoriesForProduct(String productId);

    /**
     * List all product categories for multiple products.
     *
     * @param productIds product IDs
     * @return map from product ID to product category results
     */
    Map<String, List<ProductCategoryResult>> listCategoriesForProducts(List<String> productIds);

    /**
     * Bind product categories to a product.
     *
     * @param productId product ID
     * @param categoryIds product category IDs
     */
    void bindProductCategories(String productId, List<String> categoryIds);

    /**
     * Unbind all product categories from a product.
     *
     * @param productId product ID
     */
    void unbindAllProductCategories(String productId);

    /**
     * Unbind products from a category.
     *
     * @param productIds product IDs
     * @param categoryId product category ID
     */
    void unbindProductsFromCategory(List<String> productIds, String categoryId);

    /**
     * Bind products to a category.
     *
     * @param categoryId product category ID
     * @param productIds product IDs
     */
    void bindProductsToCategory(String categoryId, List<String> productIds);
}
