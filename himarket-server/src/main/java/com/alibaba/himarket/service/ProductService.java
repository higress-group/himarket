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

import com.alibaba.himarket.dto.params.product.AddProductRefParam;
import com.alibaba.himarket.dto.params.product.CreateProductParam;
import com.alibaba.himarket.dto.params.product.QueryProductParam;
import com.alibaba.himarket.dto.params.product.QueryProductSubscriptionParam;
import com.alibaba.himarket.dto.params.product.UpdateProductParam;
import com.alibaba.himarket.dto.params.product.UpdateProductSourceParam;
import com.alibaba.himarket.dto.result.common.PageResult;
import com.alibaba.himarket.dto.result.mcp.McpToolListResult;
import com.alibaba.himarket.dto.result.product.ProductPublicationResult;
import com.alibaba.himarket.dto.result.product.ProductRefResult;
import com.alibaba.himarket.dto.result.product.ProductResult;
import com.alibaba.himarket.dto.result.product.SubscriptionResult;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Pageable;

public interface ProductService {

    /**
     * Create an API product.
     *
     * @param param the product creation parameters
     * @return the created product
     */
    ProductResult createProduct(CreateProductParam param);

    /**
     * Get API product details.
     *
     * @param productId the product ID
     * @return the product details
     */
    ProductResult getProduct(String productId);

    /**
     * List API products.
     *
     * @param param the product query filters
     * @param pageable the pagination parameters
     * @return paged products
     */
    PageResult<ProductResult> listProducts(QueryProductParam param, Pageable pageable);

    /**
     * Update an API product.
     *
     * @param productId the product ID
     * @param param the product update parameters
     * @return the updated product
     */
    ProductResult updateProduct(String productId, UpdateProductParam param);

    /**
     * Publish an API product to a portal.
     *
     * @param productId the product ID
     * @param portalId the target portal ID
     */
    void publishProduct(String productId, String portalId);

    /**
     * List publication records for an API product.
     *
     * @param productId the product ID
     * @param pageable the pagination parameters
     * @return paged publication records
     */
    PageResult<ProductPublicationResult> getPublications(String productId, Pageable pageable);

    /**
     * Remove a product publication.
     *
     * @param productId the product ID
     * @param publicationId the publication ID
     */
    void unpublishProduct(String productId, String publicationId);

    /**
     * Delete an API product.
     *
     * @param productId the product ID
     */
    void deleteProduct(String productId);

    /**
     * Create or replace the resource reference for an API product.
     *
     * @param productId the product ID
     * @param param the reference creation parameters
     */
    void addProductRef(String productId, AddProductRefParam param);

    /**
     * Get the resource reference for an API product.
     *
     * @param productId the product ID
     * @return the product reference
     */
    ProductRefResult getProductRef(String productId);

    /**
     * Delete the resource reference for an API product.
     *
     * @param productId the product ID
     */
    void deleteProductRef(String productId);

    /**
     * Get API products by ID, including their categories and product-specific configurations.
     *
     * @param productIds the product IDs
     * @return products keyed by product ID
     */
    Map<String, ProductResult> getProducts(List<String> productIds);

    /**
     * List subscriptions for an API product.
     *
     * @param productId the product ID
     * @param param the subscription query filters
     * @param pageable the pagination parameters
     * @return paged subscriptions
     */
    PageResult<SubscriptionResult> listProductSubscriptions(
            String productId, QueryProductSubscriptionParam param, Pageable pageable);

    /**
     * Require an API product to exist.
     *
     * @param productId the product ID
     */
    void existsProduct(String productId);

    /**
     * Require API products to exist.
     *
     * @param productIds the product IDs
     */
    void existsProducts(List<String> productIds);

    /**
     * Set category bindings for an API product.
     *
     * @param productId the product ID
     * @param categoryIds the category IDs to bind
     */
    void setProductCategories(String productId, List<String> categoryIds);

    /**
     * Clear category bindings for an API product.
     *
     * @param productId the product ID
     */
    void clearProductCategoryRelations(String productId);

    /**
     * Reload the product configuration from its referenced resource.
     *
     * @param productId the product ID
     */
    void reloadProductConfig(String productId);

    /**
     * List MCP tools for a product.
     *
     * @param productId the product ID
     * @return MCP tools exposed by the product
     */
    McpToolListResult listMcpTools(String productId);

    /**
     * Update the source configuration for a product.
     *
     * @param productId the product ID
     * @param param the product source update parameters
     */
    void updateProductSource(String productId, UpdateProductSourceParam param);
}
