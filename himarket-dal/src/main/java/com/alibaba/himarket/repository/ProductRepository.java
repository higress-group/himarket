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

import com.alibaba.himarket.entity.Product;
import com.alibaba.himarket.support.enums.ProductType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public interface ProductRepository extends BaseRepository<Product, Long> {

    /**
     * Find product by product ID
     *
     * @param productId the product ID
     * @return the product if found
     */
    Optional<Product> findByProductId(String productId);

    /**
     * Find product by name and admin ID
     *
     * @param name the product name
     * @param adminId the admin ID
     * @return the product if found
     */
    Optional<Product> findByNameAndAdminId(String name, String adminId);

    /**
     * Find products by product IDs
     *
     * @param productIds the collection of product IDs
     * @return the list of products
     */
    List<Product> findByProductIdIn(Collection<String> productIds);

    /**
     * Find products by type
     *
     * @param type the product type
     * @return the list of products
     */
    List<Product> findAllByType(ProductType type);

    /**
     * /**
     * Find products by type and status (paginated)
     *
     * @param type the product type
     * @param status the product status
     * @param pageable pagination info
     * @return the page of products
     */
    org.springframework.data.domain.Page<Product> findByTypeAndStatus(
            ProductType type,
            com.alibaba.himarket.support.enums.ProductStatus status,
            org.springframework.data.domain.Pageable pageable);

    /**
     * Find all product IDs by type and status (no pagination).
     *
     * @param type the product type
     * @param status the product status
     * @return the list of product IDs
     */
    @org.springframework.data.jpa.repository.Query(
            "SELECT p.productId FROM Product p WHERE p.type = :type AND p.status = :status")
    List<String> findProductIdsByTypeAndStatus(
            @org.springframework.data.repository.query.Param("type") ProductType type,
            @org.springframework.data.repository.query.Param("status")
                    com.alibaba.himarket.support.enums.ProductStatus status);

    /**
     * Find products by names and admin ID (for batch name conflict check)
     *
     * @param names the collection of product names
     * @param adminId the admin ID
     * @return the list of products matching the names
     */
    List<Product> findByNameInAndAdminId(Collection<String> names, String adminId);

    /**
     * Find products by developer ID and type
     *
     * @param developerId the developer ID
     * @param type the product type
     * @return the list of products
     */
    List<Product> findByDeveloperIdAndType(String developerId, ProductType type);

    /**
     * Find products by type where developer ID is null (official products)
     *
     * @param type the product type
     * @return the list of products
     */
    List<Product> findByTypeAndDeveloperIdIsNull(ProductType type);

    /**
     * Find products by type where developer ID is not null (personal products)
     *
     * @param type the product type
     * @return the list of products
     */
    List<Product> findByTypeAndDeveloperIdIsNotNull(ProductType type);

    /**
     * Check if a product with the given name and type exists
     *
     * @param name the product name
     * @param type the product type
     * @return true if exists
     */
    boolean existsByNameAndType(String name, ProductType type);
}
