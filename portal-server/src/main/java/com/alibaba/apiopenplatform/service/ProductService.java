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

package com.alibaba.apiopenplatform.service;

import com.alibaba.apiopenplatform.core.event.PortalDeletingEvent;
import com.alibaba.apiopenplatform.dto.params.product.*;
import com.alibaba.apiopenplatform.dto.result.common.PageResult;
import com.alibaba.apiopenplatform.dto.result.mcp.McpToolListResult;
import com.alibaba.apiopenplatform.dto.result.product.ProductPublicationResult;
import com.alibaba.apiopenplatform.dto.result.product.ProductRefResult;
import com.alibaba.apiopenplatform.dto.result.product.ProductResult;
import com.alibaba.apiopenplatform.dto.result.product.SubscriptionResult;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Map;

public interface ProductService {

    /**
     * Create API product
     *
     * @param param
     * @return
     */
    ProductResult createProduct(CreateProductParam param);

    /**
     * Get API product
     *
     * @param productId
     * @return
     */
    ProductResult getProduct(String productId);

    /**
     * List API products
     *
     * @param param
     * @param pageable
     * @return
     */
    PageResult<ProductResult> listProducts(QueryProductParam param, Pageable pageable);

    /**
     * Update API product
     *
     * @param productId
     * @param param
     * @return
     */
    ProductResult updateProduct(String productId, UpdateProductParam param);

    /**
     * Publish API product
     *
     * @param productId
     * @param portalId
     * @return
     */
    void publishProduct(String productId, String portalId);

    /**
     * Get API product publication information
     *
     * @param productId
     * @param pageable
     * @return
     */
    PageResult<ProductPublicationResult> getPublications(String productId, Pageable pageable);

    /**
     * Unpublish API product
     *
     * @param productId
     * @param portalId
     * @return
     */
    void unpublishProduct(String productId, String portalId);

    /**
     * Delete API product
     *
     * @param productId
     */
    void deleteProduct(String productId);

    /**
     * API product references API
     *
     * @param productId
     * @param param
     */
    void addProductRef(String productId, CreateProductRefParam param);

    /**
     * Get API product referenced resources
     *
     * @param productId
     * @return
     */
    ProductRefResult getProductRef(String productId);

    /**
     * Delete API product reference
     *
     * @param productId
     */
    void deleteProductRef(String productId);

    /**
     * Clean up portal resources
     *
     * @param event
     */
    void handlePortalDeletion(PortalDeletingEvent event);

    Map<String, ProductResult> getProducts(List<String> productIds);

    /**
     * Get API product dashboard monitoring panel URL
     *
     * @param productId
     * @return Dashboard URL
     */
    String getProductDashboard(String productId);

    /**
     * Get API product subscription information
     *
     * @param productId
     * @param param
     * @param pageable
     * @return
     */
    PageResult<SubscriptionResult> listProductSubscriptions(String productId, QueryProductSubscriptionParam param, Pageable pageable);

    /**
     * Check if API product exists
     *
     * @param productId
     * @return
     */
    void existsProduct(String productId);

    /**
     * Check if API product exists
     *
     * @param productIds
     * @return
     */
    void existsProducts(List<String> productIds);

    /**
     * Set product categories (binding relationship only)
     *
     * @param productId
     * @param categoryIds
     */
    void setProductCategories(String productId, List<String> categoryIds);

    /**
     * Clear product category relationships when deleting product
     *
     * @param productId
     */
    void clearProductCategoryRelations(String productId);

    /**
     * Reload API configuration for the product
     *
     * @param productId
     */
    void reloadProductConfig(String productId);

    /**
     * List MCP tools for the product
     *
     * @param productId
     */
    McpToolListResult listMcpTools(String productId);
}