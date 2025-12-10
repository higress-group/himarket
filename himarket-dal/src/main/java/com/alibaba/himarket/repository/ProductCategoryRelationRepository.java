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

    List<ProductCategoryRelation> findByProductId(String productId);

    List<ProductCategoryRelation> findByCategoryId(String categoryId);

    boolean existsByCategoryId(String categoryId);

    @Modifying
    @Transactional
    void deleteAllByProductId(String productId);

    @Modifying
    @Transactional
    void deleteByProductIdInAndCategoryId(Collection<String> productIds, String categoryId);

    List<ProductCategoryRelation> findByCategoryIdIn(Collection<String> categoryId);
}
