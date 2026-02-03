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

package com.alibaba.himarket.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.himarket.core.constant.Resources;
import com.alibaba.himarket.core.exception.BusinessException;
import com.alibaba.himarket.core.exception.ErrorCode;
import com.alibaba.himarket.core.security.ContextHolder;
import com.alibaba.himarket.dto.params.category.CreateProductCategoryParam;
import com.alibaba.himarket.dto.params.category.QueryProductCategoryParam;
import com.alibaba.himarket.dto.params.category.UpdateProductCategoryParam;
import com.alibaba.himarket.dto.result.ProductCategoryResult;
import com.alibaba.himarket.dto.result.common.PageResult;
import com.alibaba.himarket.entity.Product;
import com.alibaba.himarket.entity.ProductCategory;
import com.alibaba.himarket.entity.ProductCategoryRelation;
import com.alibaba.himarket.repository.ProductCategoryRelationRepository;
import com.alibaba.himarket.repository.ProductCategoryRepository;
import com.alibaba.himarket.service.ProductCategoryService;
import com.alibaba.himarket.support.enums.ProductType;
import com.alibaba.himarket.utils.IdGenerator;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class ProductCategoryServiceImpl implements ProductCategoryService {

    private final ProductCategoryRepository categoryRepository;

    private final ProductCategoryRelationRepository categoryRelationRepository;

    private final ContextHolder contextHolder;

    @Override
    public ProductCategoryResult createProductCategory(CreateProductCategoryParam param) {
        categoryRepository
                .findByName(param.getName())
                .ifPresent(
                        category -> {
                            throw new BusinessException(
                                    ErrorCode.CONFLICT,
                                    StrUtil.format(
                                            "Product category with name `{}` already exists",
                                            category.getName()));
                        });

        String categoryId = IdGenerator.genCategoryId();

        ProductCategory category = param.convertTo();
        category.setCategoryId(categoryId);
        category.setAdminId(contextHolder.getUser());

        categoryRepository.save(category);

        return getProductCategory(categoryId);
    }

    @Override
    public PageResult<ProductCategoryResult> listProductCategories(
            QueryProductCategoryParam param, Pageable pageable) {
        Page<ProductCategory> categories =
                categoryRepository.findAll(buildProductCategorySpec(param), pageable);
        return new PageResult<ProductCategoryResult>()
                .convertFrom(
                        categories, category -> new ProductCategoryResult().convertFrom(category));
    }

    @Override
    public ProductCategoryResult getProductCategory(String categoryId) {
        ProductCategory category = findCategory(categoryId);
        return new ProductCategoryResult().convertFrom(category);
    }

    @Override
    public ProductCategoryResult updateProductCategory(
            String categoryId, UpdateProductCategoryParam param) {
        ProductCategory category = findCategory(categoryId);

        Optional.ofNullable(param.getName())
                .filter(name -> !name.equals(category.getName()))
                .flatMap(categoryRepository::findByName)
                .ifPresent(
                        p -> {
                            throw new BusinessException(
                                    ErrorCode.CONFLICT,
                                    StrUtil.format(
                                            "Product category with name `{}` already exists",
                                            category.getName()));
                        });

        param.update(category);
        categoryRepository.saveAndFlush(category);

        return getProductCategory(categoryId);
    }

    @Override
    public void deleteProductCategory(String categoryId) {
        ProductCategory category = findCategory(categoryId);
        if (categoryRelationRepository.existsByCategoryId(categoryId)) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST,
                    StrUtil.format(
                            "Product category with name '{}' is in use", category.getName()));
        }

        categoryRepository.delete(category);
    }

    @Override
    public List<ProductCategoryResult> listCategoriesForProduct(String productId) {
        List<ProductCategoryRelation> relations =
                categoryRelationRepository.findByProductId(productId);
        if (CollUtil.isEmpty(relations)) {
            return CollUtil.newArrayList();
        }

        List<String> categoryIds =
                relations.stream()
                        .map(ProductCategoryRelation::getCategoryId)
                        .collect(Collectors.toList());

        return categoryRepository.findByCategoryIdIn(categoryIds).stream()
                .map(category -> new ProductCategoryResult().convertFrom(category))
                .collect(Collectors.toList());
    }

    @Override
    public Map<String, List<ProductCategoryResult>> listCategoriesForProducts(
            List<String> productIds) {
        // Return empty map if no product ids provided
        if (CollUtil.isEmpty(productIds)) {
            return Collections.emptyMap();
        }

        // Get all category relations for these products
        List<ProductCategoryRelation> relations =
                categoryRelationRepository.findByProductIdIn(productIds);
        if (CollUtil.isEmpty(relations)) {
            return Collections.emptyMap();
        }

        // Build categoryId to category mapping
        Map<String, ProductCategory> categoryMap =
                relations.stream()
                        .map(ProductCategoryRelation::getCategoryId)
                        .distinct()
                        .collect(
                                Collectors.collectingAndThen(
                                        Collectors.toList(),
                                        ids ->
                                                categoryRepository.findByCategoryIdIn(ids).stream()
                                                        .collect(
                                                                Collectors.toMap(
                                                                        ProductCategory
                                                                                ::getCategoryId,
                                                                        c -> c))));

        // Build final result: product id -> list of categories
        return relations.stream()
                .collect(
                        Collectors.groupingBy(
                                ProductCategoryRelation::getProductId,
                                Collectors.mapping(
                                        relation ->
                                                Optional.ofNullable(
                                                                categoryMap.get(
                                                                        relation.getCategoryId()))
                                                        .map(
                                                                category ->
                                                                        new ProductCategoryResult()
                                                                                .convertFrom(
                                                                                        category))
                                                        .orElse(null),
                                        Collectors.filtering(
                                                Objects::nonNull, Collectors.toList()))));
    }

    @Override
    public void bindProductCategories(String productId, List<String> categoryIds) {
        if (CollUtil.isEmpty(categoryIds)) {
            return;
        }

        categoryIds =
                categoryRepository.findByCategoryIdIn(categoryIds).stream()
                        .map(ProductCategory::getCategoryId)
                        .collect(Collectors.toList());

        Set<String> existedRelations =
                categoryRelationRepository.findByProductId(productId).stream()
                        .map(ProductCategoryRelation::getCategoryId)
                        .collect(Collectors.toSet());

        List<ProductCategoryRelation> relations =
                categoryIds.stream()
                        // filter out existed relations
                        .filter(categoryId -> !existedRelations.contains(categoryId))
                        .map(
                                categoryId -> {
                                    ProductCategoryRelation relation =
                                            new ProductCategoryRelation();
                                    relation.setProductId(productId);
                                    relation.setCategoryId(categoryId);
                                    return relation;
                                })
                        .collect(Collectors.toList());

        if (CollUtil.isNotEmpty(relations)) {
            categoryRelationRepository.saveAll(relations);
        }
    }

    @Override
    public void unbindAllProductCategories(String productId) {
        categoryRelationRepository.deleteAllByProductId(productId);
    }

    @Override
    public void unbindProductsFromCategory(List<String> productIds, String categoryId) {
        if (CollUtil.isEmpty(productIds)) {
            return;
        }

        // Delete the relationships between products and category
        categoryRelationRepository.deleteByProductIdInAndCategoryId(productIds, categoryId);
    }

    @Override
    public void bindProductsToCategory(String categoryId, List<String> productIds) {
        if (CollUtil.isEmpty(productIds)) {
            return;
        }

        // Get existing relationships to avoid duplicates
        Set<String> existingProductIds =
                categoryRelationRepository.findByCategoryId(categoryId).stream()
                        .map(ProductCategoryRelation::getProductId)
                        .collect(Collectors.toSet());

        // Create new relationships
        List<ProductCategoryRelation> newRelations =
                productIds.stream()
                        .filter(productId -> !existingProductIds.contains(productId))
                        .map(
                                productId -> {
                                    ProductCategoryRelation relation =
                                            new ProductCategoryRelation();
                                    relation.setProductId(productId);
                                    relation.setCategoryId(categoryId);
                                    return relation;
                                })
                        .collect(Collectors.toList());

        if (CollUtil.isNotEmpty(newRelations)) {
            categoryRelationRepository.saveAll(newRelations);
        }

        log.info("Bound {} products to category {}", newRelations.size(), categoryId);
    }

    private ProductCategory findCategory(String categoryId) {
        return categoryRepository
                .findByCategoryId(categoryId)
                .orElseThrow(
                        () ->
                                new BusinessException(
                                        ErrorCode.NOT_FOUND,
                                        Resources.PRODUCT_CATEGORY,
                                        categoryId));
    }

    private Specification<ProductCategory> buildProductCategorySpec(
            QueryProductCategoryParam param) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (StrUtil.isNotBlank(param.getName())) {
                String likePattern = "%" + param.getName().toLowerCase() + "%";
                predicates.add(cb.like(cb.lower(root.get("name")), likePattern));
            }

            if (StrUtil.isNotBlank(param.getProductType())) {
                try {
                    ProductType productType = ProductType.valueOf(param.getProductType());

                    // Use EXISTS clause to ensure proper handling of empty results
                    Subquery<Long> subquery = query.subquery(Long.class);
                    Root<ProductCategoryRelation> relationRoot =
                            subquery.from(ProductCategoryRelation.class);
                    Root<Product> productRoot = subquery.from(Product.class);

                    subquery.select(cb.literal(1L))
                            .where(
                                    cb.and(
                                            cb.equal(
                                                    relationRoot.get("categoryId"),
                                                    root.get("categoryId")),
                                            cb.equal(
                                                    relationRoot.get("productId"),
                                                    productRoot.get("productId")),
                                            cb.equal(productRoot.get("type"), productType)));

                    predicates.add(cb.exists(subquery));

                } catch (IllegalArgumentException e) {
                    log.warn("Invalid product type provided: {}", param.getProductType());
                    // Return no results for invalid product type
                    predicates.add(cb.disjunction());
                }
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
