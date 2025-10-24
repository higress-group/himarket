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

package com.alibaba.apiopenplatform.service.impl;

import com.alibaba.apiopenplatform.core.exception.BusinessException;
import com.alibaba.apiopenplatform.core.exception.ErrorCode;
import com.alibaba.apiopenplatform.core.utils.IdGenerator;
import com.alibaba.apiopenplatform.dto.params.product.CreateProductCategoryParam;
import com.alibaba.apiopenplatform.dto.result.ProductCategoryResult;
import com.alibaba.apiopenplatform.dto.result.PageResult;
import com.alibaba.apiopenplatform.entity.ProductCategory;
import com.alibaba.apiopenplatform.entity.ProductCategoryRelation;
import com.alibaba.apiopenplatform.repository.ProductCategoryRelationRepository;
import com.alibaba.apiopenplatform.repository.ProductCategoryRepository;
import com.alibaba.apiopenplatform.service.ProductCategoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class ProductCategoryServiceImpl implements ProductCategoryService {
    
    private final ProductCategoryRepository productCategoryRepository;
    private final ProductCategoryRelationRepository productCategoryRelationRepository;
    
    @Override
    public ProductCategoryResult createProductCategory(CreateProductCategoryParam param) {
        // 检查是否已存在相同code的类别
        productCategoryRepository.findByCode(param.getCode())
                .ifPresent(category -> {
                    throw new BusinessException(ErrorCode.CONFLICT, "类别代码已存在: " + param.getCode());
                });
        
        ProductCategory category = param.convertTo();
        category.setCategoryId(IdGenerator.genCategoryId());
        productCategoryRepository.save(category);
        
        return new ProductCategoryResult().convertFrom(category);
    }
    
    @Override
    public List<ProductCategoryResult> listProductCategories() {
        // 为了保持向后兼容，这里复用分页方法，但获取全部数据
        Pageable pageable = PageRequest.of(0, Integer.MAX_VALUE);
        PageResult<ProductCategoryResult> pageResult = listProductCategoriesByPage(pageable);
        return pageResult.getContent();
    }
    
    @Override
    public PageResult<ProductCategoryResult> listProductCategoriesByPage(Pageable pageable) {
        Page<ProductCategory> categoryPage = productCategoryRepository.findAll(pageable);
        
        return new PageResult<ProductCategoryResult>().convertFrom(
                categoryPage, category -> new ProductCategoryResult().convertFrom(category));
    }
    
    @Override
    public ProductCategoryResult updateProductCategory(String categoryId, CreateProductCategoryParam param) {
        ProductCategory category = productCategoryRepository.findByCategoryId(categoryId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "产品类别不存在"));
        
        // 检查是否已存在相同code的类别（排除自己）
        productCategoryRepository.findByCode(param.getCode())
                .ifPresent(existingCategory -> {
                    if (!existingCategory.getCategoryId().equals(categoryId)) {
                        throw new BusinessException(ErrorCode.CONFLICT, "类别代码已存在: " + param.getCode());
                    }
                });
        
        category.setCode(param.getCode());
        category.setName(param.getName());
        category.setDescription(param.getDescription());
        productCategoryRepository.save(category);
        
        return new ProductCategoryResult().convertFrom(category);
    }
    
    @Override
    public void deleteProductCategory(String categoryId) {
        ProductCategory category = productCategoryRepository.findByCategoryId(categoryId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "产品类别不存在"));
        
        // 检查该类别是否已被产品绑定
        List<ProductCategoryRelation> relations = productCategoryRelationRepository.findByCategoryId(categoryId);
        if (!relations.isEmpty()) {
            throw new BusinessException(ErrorCode.CONFLICT, 
                "该类别已被" + relations.size() + "个产品绑定，请先删除绑定关系再删除类别");
        }
        
        productCategoryRepository.delete(category);
    }
}