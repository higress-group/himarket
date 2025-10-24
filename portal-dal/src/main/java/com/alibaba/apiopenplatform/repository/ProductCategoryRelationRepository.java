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

package com.alibaba.apiopenplatform.repository;

import java.util.List;

import com.alibaba.apiopenplatform.entity.ProductCategoryRelation;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface ProductCategoryRelationRepository extends BaseRepository<ProductCategoryRelation, Long> {
    List<ProductCategoryRelation> findByProductId(String productId);

    List<ProductCategoryRelation> findByCategoryId(String categoryId);

    @Modifying
    @Transactional
    @Query("DELETE FROM ProductCategoryRelation p WHERE p.productId = :productId")
    void deleteByProductId(@Param("productId") String productId);
}