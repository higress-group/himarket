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

import com.alibaba.himarket.core.constant.Resources;
import com.alibaba.himarket.core.exception.BusinessException;
import com.alibaba.himarket.core.exception.ErrorCode;
import com.alibaba.himarket.core.utils.IdGenerator;
import com.alibaba.himarket.dto.params.apidefinition.CreateApiDefinitionParam;
import com.alibaba.himarket.dto.params.apidefinition.QueryApiDefinitionParam;
import com.alibaba.himarket.dto.params.apidefinition.UpdateApiDefinitionParam;
import com.alibaba.himarket.dto.result.apidefinition.ApiDefinitionResult;
import com.alibaba.himarket.dto.result.common.PageResult;
import com.alibaba.himarket.dto.result.mcp.McpConfigResult;
import com.alibaba.himarket.entity.ApiDefinition;
import com.alibaba.himarket.entity.Product;
import com.alibaba.himarket.entity.ProductRef;
import com.alibaba.himarket.repository.ApiDefinitionRepository;
import com.alibaba.himarket.repository.ProductRefRepository;
import com.alibaba.himarket.repository.ProductRepository;
import com.alibaba.himarket.service.ApiDefinitionService;
import com.alibaba.himarket.support.common.Strings;
import com.alibaba.himarket.support.enums.ApiStatus;
import com.alibaba.himarket.support.enums.ApiType;
import com.alibaba.himarket.support.enums.ProductStatus;
import com.alibaba.himarket.support.enums.SourceType;
import com.alibaba.himarket.utils.JsonUtil;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional
public class ApiDefinitionServiceImpl implements ApiDefinitionService {

    private final ApiDefinitionRepository apiDefinitionRepository;

    private final ProductRepository productRepository;

    private final ProductRefRepository productRefRepository;

    @Override
    public ApiDefinitionResult createApiDefinition(CreateApiDefinitionParam param) {
        ApiDefinition definition = param.convertTo();
        definition.setApiDefinitionId(IdGenerator.genApiDefinitionId());
        apiDefinitionRepository.save(definition);

        // Bind product if relatedProductId is provided
        if (Strings.isNotBlank(param.getRelatedProductId())) {
            bindApiDefinitionToProduct(param.getRelatedProductId(), definition);
        }

        return new ApiDefinitionResult().convertFrom(definition);
    }

    @Override
    public ApiDefinitionResult getApiDefinition(String apiDefinitionId) {
        ApiDefinition apiDefinition = findApiDefinition(apiDefinitionId);

        return new ApiDefinitionResult().convertFrom(apiDefinition);
    }

    @Override
    public PageResult<ApiDefinitionResult> listApiDefinitions(
            QueryApiDefinitionParam param, Pageable pageable) {
        Page<ApiDefinition> page =
                apiDefinitionRepository.findAll(buildSpecification(param), pageable);

        return new PageResult<ApiDefinitionResult>()
                .convertFrom(page, definition -> new ApiDefinitionResult().convertFrom(definition));
    }

    @Override
    public void updateApiDefinition(String apiDefinitionId, UpdateApiDefinitionParam param) {
        ApiDefinition definition = findApiDefinition(apiDefinitionId);

        if (definition.getStatus() == ApiStatus.PUBLISHING) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST,
                    "Cannot update API Definition while it is being published");
        }

        param.update(definition);
        apiDefinitionRepository.saveAndFlush(definition);

        productRefRepository
                .findByApiDefinitionId(apiDefinitionId)
                .ifPresent(
                        ref -> {
                            syncProductRefConfig(ref, definition);
                            productRefRepository.save(ref);
                        });
    }

    @Override
    public void deleteApiDefinition(String apiDefinitionId) {
        ApiDefinition definition = findApiDefinition(apiDefinitionId);

        if (definition.getStatus() == ApiStatus.PUBLISHED) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST,
                    "Cannot delete a published API Definition. Unpublish first.");
        }

        productRefRepository
                .findByApiDefinitionId(apiDefinitionId)
                .ifPresent(productRefRepository::delete);

        apiDefinitionRepository.delete(definition);
        log.info("Deleted API Definition, apiDefinitionId={}", apiDefinitionId);
    }

    private ApiDefinition findApiDefinition(String apiDefinitionId) {
        return apiDefinitionRepository
                .findByApiDefinitionId(apiDefinitionId)
                .orElseThrow(
                        () ->
                                new BusinessException(
                                        ErrorCode.NOT_FOUND,
                                        Resources.API_DEFINITION,
                                        apiDefinitionId));
    }

    private Product findProduct(String productId) {
        return productRepository
                .findByProductId(productId)
                .orElseThrow(
                        () ->
                                new BusinessException(
                                        ErrorCode.NOT_FOUND, Resources.PRODUCT, productId));
    }

    private Specification<ApiDefinition> buildSpecification(QueryApiDefinitionParam param) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (param.getType() != null) {
                predicates.add(cb.equal(root.get("type"), param.getType()));
            }

            if (param.getStatus() != null) {
                predicates.add(cb.equal(root.get("status"), param.getStatus()));
            }

            if (Strings.isNotBlank(param.getKeyword())) {
                predicates.add(cb.like(root.get("name"), "%" + param.getKeyword() + "%"));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private void bindApiDefinitionToProduct(String relatedProductId, ApiDefinition definition) {
        // Only MCP_SERVER type is supported for now
        if (definition.getType() != ApiType.MCP_SERVER) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST,
                    String.format(
                            "Binding product is only supported for MCP_SERVER type; actualType=%s",
                            definition.getType()));
        }

        Product product = findProduct(relatedProductId);
        if (product.getType() != definition.getType().toProductType()) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST,
                    String.format(
                            "Product type %s does not match API Definition type %s",
                            product.getType(), definition.getType()));
        }

        // Make sure product is not already linked to another API
        productRefRepository
                .findByProductId(product.getProductId())
                .ifPresent(
                        productRef -> {
                            throw new BusinessException(
                                    ErrorCode.CONFLICT, "Product is already linked to an API");
                        });

        ProductRef productRef =
                ProductRef.builder()
                        .productId(product.getProductId())
                        .apiDefinitionId(definition.getApiDefinitionId())
                        .sourceType(SourceType.API_DEFINITION)
                        .enabled(true)
                        .build();

        syncProductRefConfig(productRef, definition);
        productRefRepository.save(productRef);

        if (product.getStatus() != ProductStatus.PUBLISHED) {
            product.setStatus(ProductStatus.READY);
            productRepository.save(product);
        }
    }

    private void syncProductRefConfig(ProductRef ref, ApiDefinition definition) {
        switch (definition.getType()) {
            case MCP_SERVER ->
                    ref.setMcpConfig(
                            JsonUtil.toJson(McpConfigResult.fromApiDefinition(definition)));
            case AGENT_API -> ref.setAgentConfig(JsonUtil.toJson(definition.getSpec()));
            case MODEL_API -> ref.setModelConfig(JsonUtil.toJson(definition.getSpec()));
        }
    }
}
