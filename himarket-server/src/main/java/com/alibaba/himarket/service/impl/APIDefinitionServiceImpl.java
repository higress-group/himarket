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

import cn.hutool.core.util.StrUtil;
import com.alibaba.himarket.core.constant.Resources;
import com.alibaba.himarket.core.exception.BusinessException;
import com.alibaba.himarket.core.exception.ErrorCode;
import com.alibaba.himarket.dto.params.api.CreateAPIDefinitionParam;
import com.alibaba.himarket.dto.params.api.PublishAPIParam;
import com.alibaba.himarket.dto.params.api.QueryAPIDefinitionParam;
import com.alibaba.himarket.dto.params.api.UpdateAPIDefinitionParam;
import com.alibaba.himarket.dto.result.api.APIDefinitionResult;
import com.alibaba.himarket.dto.result.api.APIDeploymentResult;
import com.alibaba.himarket.dto.result.api.APIDeploymentSnapshot;
import com.alibaba.himarket.dto.result.api.PropertyFieldVO;
import com.alibaba.himarket.dto.result.api.PropertySchemaVO;
import com.alibaba.himarket.dto.result.common.PageResult;
import com.alibaba.himarket.entity.APIDefinition;
import com.alibaba.himarket.entity.APIDeployment;
import com.alibaba.himarket.entity.Gateway;
import com.alibaba.himarket.repository.APIDefinitionRepository;
import com.alibaba.himarket.repository.APIDeploymentRepository;
import com.alibaba.himarket.repository.GatewayRepository;
import com.alibaba.himarket.service.APIDefinitionService;
import com.alibaba.himarket.service.api.GatewayCapabilityRegistry;
import com.alibaba.himarket.service.api.GatewayPublisher;
import com.alibaba.himarket.support.annotation.APIField;
import com.alibaba.himarket.support.annotation.SupportedAPITypes;
import com.alibaba.himarket.support.api.DeploymentConfig;
import com.alibaba.himarket.support.api.property.APIPolicy;
import com.alibaba.himarket.support.enums.APIType;
import com.alibaba.himarket.support.enums.PolicyType;
import com.alibaba.himarket.support.enums.PublishStatus;
import com.alibaba.himarket.utils.IdGenerator;
import com.alibaba.himarket.utils.JsonUtil;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import jakarta.persistence.criteria.Predicate;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional
public class APIDefinitionServiceImpl implements APIDefinitionService {

    private final APIDefinitionRepository apiDefinitionRepository;

    private final APIDeploymentRepository apiPublishRecordRepository;

    private final GatewayRepository gatewayRepository;

    private final GatewayCapabilityRegistry gatewayCapabilityRegistry;

    private final AsyncAPIPublishService asyncAPIPublishService;

    @Override
    public List<PropertySchemaVO> listSupportedProperties() {
        return listSupportedProperties(null);
    }

    @Override
    public List<PropertySchemaVO> listSupportedProperties(APIType apiType) {
        List<PropertySchemaVO> schemas = new ArrayList<>();

        // Get the mapping from APIPolicy
        JsonSubTypes jsonSubTypes = APIPolicy.class.getAnnotation(JsonSubTypes.class);
        Map<String, Class<?>> typeMap = new HashMap<>();
        if (jsonSubTypes != null) {
            for (JsonSubTypes.Type type : jsonSubTypes.value()) {
                typeMap.put(type.name(), type.value());
            }
        }

        for (PolicyType type : PolicyType.values()) {
            // Skip if no class mapping found
            Class<?> clazz = typeMap.get(type.name());
            if (clazz == null) {
                continue;
            }

            // Check if property supports the given API type
            SupportedAPITypes supportedAnnotation = clazz.getAnnotation(SupportedAPITypes.class);
            List<APIType> supportedApiTypes =
                    supportedAnnotation != null
                            ? Arrays.asList(supportedAnnotation.value())
                            : Collections.emptyList();

            // Filter by API type if specified
            if (apiType != null
                    && !supportedApiTypes.isEmpty()
                    && !supportedApiTypes.contains(apiType)) {
                continue;
            }

            List<PropertyFieldVO> fields = new ArrayList<>();
            // Iterate all fields of the subclass
            for (java.lang.reflect.Field field : clazz.getDeclaredFields()) {
                APIField annotation = field.getAnnotation(APIField.class);
                if (annotation != null) {
                    fields.add(buildFieldVO(field, annotation));
                }
            }

            schemas.add(
                    PropertySchemaVO.builder()
                            .type(type)
                            .name(type.getLabel())
                            .description(type.getDescription())
                            .fields(fields)
                            .supportedApiTypes(supportedApiTypes)
                            .build());
        }

        return schemas;
    }

    private PropertyFieldVO buildFieldVO(java.lang.reflect.Field field, APIField annotation) {
        String type = annotation.type();
        List<String> options = Arrays.asList(annotation.options());

        if (StrUtil.isBlank(type)) {
            Class<?> fieldType = field.getType();
            if (fieldType == Integer.class
                    || fieldType == int.class
                    || fieldType == Long.class
                    || fieldType == long.class) {
                type = "integer";
            } else if (fieldType == Boolean.class || fieldType == boolean.class) {
                type = "boolean";
            } else if (fieldType.isEnum()) {
                type = "select";
                if (options.isEmpty()) {
                    options =
                            Arrays.stream(fieldType.getEnumConstants())
                                    .map(Object::toString)
                                    .collect(Collectors.toList());
                }
            } else {
                // For String fields, check if options are provided
                if (!options.isEmpty()) {
                    type = "select";
                } else {
                    type = "string";
                }
            }
        }

        Object defaultValue = null;
        if (StrUtil.isNotBlank(annotation.defaultValue())) {
            // Simple conversion for default value
            if ("boolean".equals(type)) {
                defaultValue = Boolean.parseBoolean(annotation.defaultValue());
            } else if ("integer".equals(type)) {
                try {
                    defaultValue = Long.parseLong(annotation.defaultValue());
                } catch (NumberFormatException e) {
                    // ignore
                }
            } else {
                defaultValue = annotation.defaultValue();
            }
        }

        return PropertyFieldVO.builder()
                .name(field.getName())
                .label(annotation.label())
                .type(type)
                .description(annotation.description())
                .required(annotation.required())
                .options(options.isEmpty() ? null : options)
                .defaultValue(defaultValue)
                .build();
    }

    @Override
    public APIDefinitionResult createAPIDefinition(CreateAPIDefinitionParam param) {
        APIDefinition apiDefinition = param.convertTo();
        apiDefinition.setApiDefinitionId(IdGenerator.genApiDefinitionId());

        apiDefinitionRepository.save(apiDefinition);
        return new APIDefinitionResult().convertFrom(apiDefinition);
    }

    @Override
    public APIDefinitionResult getAPIDefinition(String apiDefinitionId) {
        APIDefinition apiDefinition = findAPIDefinition(apiDefinitionId);

        return new APIDefinitionResult().convertFrom(apiDefinition);
    }

    @Override
    public PageResult<APIDefinitionResult> listAPIDefinitions(
            QueryAPIDefinitionParam param, Pageable pageable) {
        Page<APIDefinition> apiDefinitions =
                apiDefinitionRepository.findAll(buildSpecification(param), pageable);

        return new PageResult<APIDefinitionResult>()
                .convertFrom(
                        apiDefinitions,
                        definition -> new APIDefinitionResult().convertFrom(definition));
    }

    @Override
    public APIDefinitionResult updateAPIDefinition(
            String apiDefinitionId, UpdateAPIDefinitionParam param) {
        APIDefinition apiDefinition = findAPIDefinition(apiDefinitionId);

        param.update(apiDefinition);
        apiDefinitionRepository.saveAndFlush(apiDefinition);

        return new APIDefinitionResult().convertFrom(apiDefinition);
    }

    @Override
    public void deleteAPIDefinition(String apiDefinitionId) {
        APIDefinition apiDefinition = findAPIDefinition(apiDefinitionId);

        // Check if API is currently published on any gateway by examining the latest record per
        // gateway
        boolean isPublished =
                apiPublishRecordRepository
                        .findByApiDefinitionId(
                                apiDefinitionId, Sort.by(Sort.Direction.DESC, "createdAt"))
                        .stream()
                        .collect(
                                Collectors.toMap(
                                        APIDeployment::getGatewayId,
                                        Function.identity(),
                                        (r1, r2) -> r1))
                        .values()
                        .stream()
                        .anyMatch(r -> r.getStatus().isActive());

        if (isPublished) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Cannot delete published API");
        }

        apiDefinitionRepository.delete(apiDefinition);
    }

    private Specification<APIDefinition> buildSpecification(QueryAPIDefinitionParam param) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (param.getType() != null) {
                predicates.add(cb.equal(root.get("type"), param.getType()));
            }

            if (param.getStatus() != null) {
                predicates.add(cb.equal(root.get("status"), param.getStatus()));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    @Override
    public PageResult<APIDeploymentResult> listPublishRecords(
            String apiDefinitionId, Pageable pageable) {
        existsAPIDefinition(apiDefinitionId);

        Page<APIDeployment> page =
                apiPublishRecordRepository.findByApiDefinitionIdOrderByCreatedAtDesc(
                        apiDefinitionId, pageable);

        return new PageResult<APIDeploymentResult>()
                .convertFrom(page, record -> new APIDeploymentResult().convertFrom(record));
    }

    @Override
    public APIDeploymentResult publishAPI(String apiDefinitionId, PublishAPIParam param) {
        APIDefinition apiDefinition = findAPIDefinition(apiDefinitionId);
        Gateway gateway = findGateway(param.getGatewayId());

        // Get latest record per gateway (ordered by createdAt DESC, so first is latest)
        Map<String, APIDeployment> latestByGateway =
                apiPublishRecordRepository
                        .findByApiDefinitionId(
                                apiDefinitionId, Sort.by(Sort.Direction.DESC, "createdAt"))
                        .stream()
                        .collect(
                                Collectors.toMap(
                                        APIDeployment::getGatewayId,
                                        Function.identity(),
                                        (r1, r2) -> r1));

        APIDeployment record =
                latestByGateway.values().stream()
                        .filter(r -> r.getStatus().isActive() || r.getStatus().isProcessing())
                        .findFirst()
                        .orElse(null);

        if (record != null) {
            boolean isSameGateway = record.getGatewayId().equals(param.getGatewayId());
            boolean isActive = record.getStatus().isActive();

            if (isSameGateway && isActive) {
                return new APIDeploymentResult().convertFrom(record);
            }

            String message =
                    isSameGateway
                            ? "API is being published or unpublished"
                            : isActive
                                    ? "API is published on gateway: " + record.getGatewayId()
                                    : "API is being published or unpublished on another gateway";

            throw new BusinessException(ErrorCode.INVALID_REQUEST, message);
        }

        // Validate publish config
        GatewayPublisher publisher =
                gatewayCapabilityRegistry.getPublisher(gateway.getGatewayType());
        publisher.validateDeploymentConfig(apiDefinition, param.getDeploymentConfig());

        String deploymentId = IdGenerator.genPublishRecordId();
        APIDeployment publishRecord =
                APIDeployment.builder()
                        .deploymentId(deploymentId)
                        .apiDefinitionId(apiDefinition.getApiDefinitionId())
                        .gatewayId(gateway.getGatewayId())
                        .version(apiDefinition.getVersion())
                        .status(PublishStatus.PUBLISHING)
                        .description(param.getDescription())
                        .snapshot(serializeSnapshot(apiDefinition, param.getDeploymentConfig()))
                        .build();

        apiPublishRecordRepository.save(publishRecord);

        asyncAPIPublishService.asyncPublish(
                deploymentId, gateway, apiDefinition, param.getDeploymentConfig());

        return new APIDeploymentResult().convertFrom(publishRecord);
    }

    @Override
    public void unpublishAPI(String apiDefinitionId, String deploymentId) {
        APIDefinition apiDefinition = findAPIDefinition(apiDefinitionId);
        APIDeployment publishRecord = findPublishRecord(deploymentId, apiDefinitionId);

        // Check if there's an ongoing operation on the target gateway
        Optional<APIDeployment> latestRecord =
                apiPublishRecordRepository.findFirstByApiDefinitionIdAndGatewayId(
                        apiDefinitionId,
                        publishRecord.getGatewayId(),
                        Sort.by(Sort.Direction.DESC, "createdAt"));

        if (latestRecord.isPresent() && latestRecord.get().getStatus().isProcessing()) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST, "API is being published or unpublished");
        }

        Gateway gateway = findGateway(publishRecord.getGatewayId());
        DeploymentConfig deploymentConfig =
                parseDeploymentConfigFromSnapshot(publishRecord.getSnapshot());

        String newRecordId = IdGenerator.genPublishRecordId();
        APIDeployment unpublishRecord =
                createUnpublishRecord(newRecordId, apiDefinitionId, publishRecord);

        apiPublishRecordRepository.save(unpublishRecord);

        asyncAPIPublishService.asyncUnpublish(
                newRecordId, gateway, apiDefinition, deploymentConfig);
    }

    @Override
    public APIDeploymentResult getPublishRecordStatus(String apiDefinitionId, String deploymentId) {
        existsAPIDefinition(apiDefinitionId);
        APIDeployment publishRecord = findPublishRecord(deploymentId, apiDefinitionId);
        return new APIDeploymentResult().convertFrom(publishRecord);
    }

    private APIDefinition findAPIDefinition(String apiDefinitionId) {
        return apiDefinitionRepository
                .findByApiDefinitionId(apiDefinitionId)
                .orElseThrow(
                        () ->
                                new BusinessException(
                                        ErrorCode.NOT_FOUND,
                                        Resources.API_DEFINITION,
                                        apiDefinitionId));
    }

    private void existsAPIDefinition(String apiDefinitionId) {
        apiDefinitionRepository
                .findByApiDefinitionId(apiDefinitionId)
                .orElseThrow(
                        () ->
                                new BusinessException(
                                        ErrorCode.NOT_FOUND,
                                        Resources.API_DEFINITION,
                                        apiDefinitionId));
    }

    private Gateway findGateway(String gatewayId) {
        return gatewayRepository
                .findByGatewayId(gatewayId)
                .orElseThrow(
                        () ->
                                new BusinessException(
                                        ErrorCode.NOT_FOUND, Resources.GATEWAY, gatewayId));
    }

    private APIDeployment findPublishRecord(String deploymentId, String apiDefinitionId) {
        APIDeployment publishRecord =
                apiPublishRecordRepository
                        .findByDeploymentId(deploymentId)
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                ErrorCode.NOT_FOUND,
                                                Resources.PUBLISH_RECORD,
                                                deploymentId));

        if (!publishRecord.getApiDefinitionId().equals(apiDefinitionId)) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST, "Publish record does not belong to this API");
        }

        return publishRecord;
    }

    private APIDeployment createUnpublishRecord(
            String newRecordId, String apiDefinitionId, APIDeployment publishRecord) {
        return APIDeployment.builder()
                .deploymentId(newRecordId)
                .apiDefinitionId(apiDefinitionId)
                .gatewayId(publishRecord.getGatewayId())
                .version(publishRecord.getVersion())
                .status(PublishStatus.UNPUBLISHING)
                .gatewayResourceConfig(publishRecord.getGatewayResourceConfig())
                .snapshot(publishRecord.getSnapshot())
                .build();
    }

    /**
     * Serialize snapshot (API Definition + Publish Config)
     */
    private String serializeSnapshot(
            APIDefinition apiDefinition, DeploymentConfig deploymentConfig) {
        try {
            APIDefinitionResult apiDefinitionResult =
                    new APIDefinitionResult().convertFrom(apiDefinition);
            APIDeploymentSnapshot snapshot =
                    APIDeploymentSnapshot.builder()
                            .apiDefinition(apiDefinitionResult)
                            .deploymentConfig(deploymentConfig)
                            .build();
            return JsonUtil.toJson(snapshot);
        } catch (Exception e) {
            throw new BusinessException(
                    ErrorCode.INTERNAL_ERROR, "Failed to serialize snapshot: " + e.getMessage());
        }
    }

    /**
     * Parse publish config from snapshot
     */
    private DeploymentConfig parseDeploymentConfigFromSnapshot(String snapshotJson) {
        try {
            APIDeploymentSnapshot snapshot =
                    JsonUtil.parse(snapshotJson, APIDeploymentSnapshot.class);
            if (snapshot == null || snapshot.getDeploymentConfig() == null) {
                throw new BusinessException(
                        ErrorCode.INTERNAL_ERROR, "DeploymentConfig not found in snapshot");
            }
            return snapshot.getDeploymentConfig();
        } catch (Exception e) {
            throw new BusinessException(
                    ErrorCode.INTERNAL_ERROR,
                    "Failed to parse deploymentConfig from snapshot: " + e.getMessage());
        }
    }
}
