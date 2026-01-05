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

import java.util.List;
import java.util.Optional;

import com.alibaba.himarket.entity.ProductLike;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Repository interface for managing product like operations.
 * Provides methods for querying, counting, and managing product like records.
 */
public interface ProductLikeRepository extends JpaRepository<ProductLike, Long> {

    /**
     * Finds a like record by product ID and developer ID
     *
     * @param productId   the product identifier
     * @param developerId the developer identifier
     * @return optional like record
     */
    Optional<ProductLike> findByProductIdAndDeveloperId(String productId, String developerId);

    /**
     * Deletes all like records associated with a product
     *
     * @param productId the product identifier
     */
    void deleteAllByProductId(String productId);

    /**
     * Counts the number of likes for a specific product
     *
     * @param productId the product identifier
     * @return count of likes for the product
     */
    @Query("SELECT COUNT(pl) FROM ProductLike pl WHERE pl.productId = :productId AND pl.status ="
                    + " com.alibaba.himarket.support.enums.LikeStatus.LIKED")
    Long countByProductIdAndStatus(@Param("productId") String productId);


    /**
     * Counts likes grouped by product ID
     *
     * @return List of Object[] where index 0 is productId and index 1 is like count
     */
    @Query("SELECT pl.productId, COUNT(pl) FROM ProductLike pl WHERE pl.status ="
                    + " com.alibaba.himarket.support.enums.LikeStatus.LIKED GROUP BY pl.productId")
    List<Object[]> countLikesGroupedByProductId();
}
