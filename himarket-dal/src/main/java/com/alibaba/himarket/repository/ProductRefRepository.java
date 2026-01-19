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

import com.alibaba.himarket.entity.ProductRef;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public interface ProductRefRepository extends BaseRepository<ProductRef, Long> {

    /**
     * Find product reference by product ID
     *
     * @param productId the product ID
     * @return the product reference if found
     */
    Optional<ProductRef> findByProductId(String productId);

    Optional<ProductRef> findByApiDefinitionId(String apiDefinitionId);

    /**
     * Find first product reference by product ID
     *
     * @param productId the product ID
     * @return the first product reference if found
     */
    Optional<ProductRef> findFirstByProductId(String productId);

    /**
     * Check if gateway ID exists in product references
     *
     * @param gatewayId the gateway ID
     * @return true if exists, false otherwise
     */
    boolean existsByGatewayId(String gatewayId);

    /**
     * Delete product reference by product ID
     *
     * @param productId the product ID
     */
    void deleteByProductId(String productId);
}
