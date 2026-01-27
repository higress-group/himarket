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
}
