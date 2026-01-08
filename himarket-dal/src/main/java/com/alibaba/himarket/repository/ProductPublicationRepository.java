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

import com.alibaba.himarket.entity.ProductPublication;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ProductPublicationRepository extends BaseRepository<ProductPublication, Long> {

    /**
     * Find product publication by publication ID
     *
     * @param publicationId the publication ID
     * @return the product publication if found
     */
    Optional<ProductPublication> findByPublicationId(String publicationId);

    /**
     * Find product publications by portal ID with pagination
     *
     * @param portalId the portal ID
     * @param pageable the pagination information
     * @return the page of product publications
     */
    Page<ProductPublication> findByPortalId(String portalId, Pageable pageable);

    /**
     * Find product publication by portal ID and product ID
     *
     * @param portalId the portal ID
     * @param productId the product ID
     * @return the product publication if found
     */
    Optional<ProductPublication> findByPortalIdAndProductId(String portalId, String productId);

    /**
     * Find product publications by product ID with pagination
     *
     * @param productId the product ID
     * @param pageable the pagination information
     * @return the page of product publications
     */
    Page<ProductPublication> findByProductId(String productId, Pageable pageable);

    /**
     * Delete product publication by product ID
     *
     * @param productId the product ID
     */
    void deleteByProductId(String productId);

    /**
     * Delete all product publications by portal ID
     *
     * @param portalId the portal ID
     */
    void deleteAllByPortalId(String portalId);

    /**
     * Check if product publication exists by product ID
     *
     * @param productId the product ID
     * @return true if exists, false otherwise
     */
    boolean existsByProductId(String productId);

    /**
     * Check if product is published to portals other than specified portal ID
     *
     * @param productId the product ID
     * @param portalId the portal ID to exclude
     * @return true if published to other portals, false otherwise
     */
    boolean existsByProductIdAndPortalIdNot(String productId, String portalId);
}
