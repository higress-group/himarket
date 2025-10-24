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

import com.alibaba.apiopenplatform.dto.params.product.CreateProductCategoryParam;
import com.alibaba.apiopenplatform.dto.result.PageResult;
import com.alibaba.apiopenplatform.dto.result.ProductCategoryResult;

import java.util.List;

import org.springframework.data.domain.Pageable;

public interface ProductCategoryService {
    
    /**
     * 创建产品类别
     *
     * @param param
     * @return
     */
    ProductCategoryResult createProductCategory(CreateProductCategoryParam param);
    
    /**
     * 获取所有产品类别
     *
     * @return
     */
    List<ProductCategoryResult> listProductCategories();

    PageResult<ProductCategoryResult> listProductCategoriesByPage(Pageable pageable);

    /**
     * 更新产品类别
     *
     * @param categoryId
     * @param param
     * @return
     */
    ProductCategoryResult updateProductCategory(String categoryId, CreateProductCategoryParam param);
    
    /**
     * 删除产品类别
     *
     * @param categoryId
     */
    void deleteProductCategory(String categoryId);
}