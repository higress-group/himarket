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

package com.alibaba.himarket.repository;

import com.alibaba.himarket.entity.ProductCategoryRelation;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

public interface ProductCategoryRelationRepository
        extends BaseRepository<ProductCategoryRelation, Long> {

    /**
     * Find product-category relations by product ID
     *
     * @param productId the product ID
     * @return the list of product-category relations
     */
    List<ProductCategoryRelation> findByProductId(String productId);

    /**
     * Find product-category relations by category ID
     *
     * @param categoryId the category ID
     * @return the list of product-category relations
     */
    List<ProductCategoryRelation> findByCategoryId(String categoryId);

    /**
     * Check if category ID exists in relations
     *
     * @param categoryId the category ID
     * @return true if exists, false otherwise
     */
    boolean existsByCategoryId(String categoryId);

    /**
     * Delete all product-category relations by product ID
     *
     * @param productId the product ID
     */
    @Modifying
    @Transactional
    void deleteAllByProductId(String productId);

    /**
     * Delete product-category relations by product IDs and category ID
     *
     * @param productIds the collection of product IDs
     * @param categoryId the category ID
     */
    @Modifying
    @Transactional
    void deleteByProductIdInAndCategoryId(Collection<String> productIds, String categoryId);
}
