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

import cn.hutool.core.lang.Snowflake;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.alibaba.himarket.core.exception.BusinessException;
import com.alibaba.himarket.core.exception.ErrorCode;
import com.alibaba.himarket.dto.params.api.CreateAPIDefinitionParam;
import com.alibaba.himarket.dto.params.api.CreateEndpointParam;
import com.alibaba.himarket.dto.params.api.PublishAPIParam;
import com.alibaba.himarket.dto.params.api.QueryAPIDefinitionParam;
import com.alibaba.himarket.dto.params.api.UpdateAPIDefinitionParam;
import com.alibaba.himarket.dto.params.api.UpdateEndpointParam;
import com.alibaba.himarket.dto.result.api.APIDefinitionVO;
import com.alibaba.himarket.dto.result.api.APIEndpointVO;
import com.alibaba.himarket.dto.result.api.APIPublishHistoryVO;
import com.alibaba.himarket.dto.result.api.APIPublishRecordVO;
import com.alibaba.himarket.dto.result.api.PropertyFieldVO;
import com.alibaba.himarket.dto.result.api.PropertySchemaVO;
import com.alibaba.himarket.dto.result.common.PageResult;
import com.alibaba.himarket.entity.APIDefinition;
import com.alibaba.himarket.entity.APIEndpoint;
import com.alibaba.himarket.entity.APIPublishHistory;
import com.alibaba.himarket.entity.APIPublishRecord;
import com.alibaba.himarket.entity.Gateway;
import com.alibaba.himarket.repository.APIDefinitionRepository;
import com.alibaba.himarket.repository.APIEndpointRepository;
import com.alibaba.himarket.repository.APIPublishHistoryRepository;
import com.alibaba.himarket.repository.APIPublishRecordRepository;
import com.alibaba.himarket.repository.GatewayRepository;
import com.alibaba.himarket.service.APIDefinitionService;
import com.alibaba.himarket.service.api.GatewayCapabilityRegistry;
import com.alibaba.himarket.service.api.GatewayPublisher;
import com.alibaba.himarket.support.annotation.APIField;
import com.alibaba.himarket.support.api.PublishConfig;
import com.alibaba.himarket.support.api.BaseAPIProperty;
import com.alibaba.himarket.support.enums.APIStatus;
import com.alibaba.himarket.support.enums.PropertyType;
import com.alibaba.himarket.support.enums.PublishAction;
import com.alibaba.himarket.support.enums.RateLimitScope;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** API Definition 服务实现类 */
@Service
@Slf4j
@RequiredArgsConstructor
@Transactional
public class APIDefinitionServiceImpl implements APIDefinitionService {

    private final APIDefinitionRepository apiDefinitionRepository;
    private final APIEndpointRepository apiEndpointRepository;
    private final APIPublishRecordRepository apiPublishRecordRepository;
    private final APIPublishHistoryRepository apiPublishHistoryRepository;
    private final GatewayRepository gatewayRepository;
    private final GatewayCapabilityRegistry gatewayCapabilityRegistry;

    private static final Snowflake SNOWFLAKE = IdUtil.getSnowflake(1, 1);

    @Override
    public List<PropertySchemaVO> listSupportedProperties() {
        List<PropertySchemaVO> schemas = new ArrayList<>();

        // Get the mapping from BaseAPIProperty
        JsonSubTypes jsonSubTypes = BaseAPIProperty.class.getAnnotation(JsonSubTypes.class);
        Map<String, Class<?>> typeMap = new HashMap<>();
        if (jsonSubTypes != null) {
            for (JsonSubTypes.Type type : jsonSubTypes.value()) {
                typeMap.put(type.name(), type.value());
            }
        }

        for (PropertyType type : PropertyType.values()) {
            // Skip if no class mapping found
            Class<?> clazz = typeMap.get(type.name());
            if (clazz == null) {
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

            if (!fields.isEmpty()) {
                schemas.add(
                        PropertySchemaVO.builder()
                                .type(type)
                                .name(type.getLabel())
                                .description(type.getDescription())
                                .fields(fields)
                                .build());
            }
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
                type = "string";
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
    public APIDefinitionVO createAPIDefinition(CreateAPIDefinitionParam param) {
        log.info("Creating API Definition: {}", param.getName());

        // 生成 API Definition ID
        String apiDefinitionId = "api-def-" + SNOWFLAKE.nextIdStr();

        // 转换为实体
        APIDefinition apiDefinition = param.convertTo();
        apiDefinition.setApiDefinitionId(apiDefinitionId);
        apiDefinition.setStatus(APIStatus.DRAFT);

        // 保存
        apiDefinition = apiDefinitionRepository.save(apiDefinition);

        // 批量创建 endpoints（如果有）
        if (param.getEndpoints() != null && !param.getEndpoints().isEmpty()) {
            log.info(
                    "Creating {} endpoints for API Definition: {}",
                    param.getEndpoints().size(),
                    apiDefinitionId);
            for (CreateEndpointParam endpointParam : param.getEndpoints()) {
                createEndpoint(apiDefinitionId, endpointParam);
            }
        }

        log.info("API Definition created successfully: {}", apiDefinitionId);

        // 转换为 VO
        return new APIDefinitionVO().convertFrom(apiDefinition);
    }

    @Override
    public APIDefinitionVO getAPIDefinition(String apiDefinitionId) {
        log.info("Getting API Definition: {}", apiDefinitionId);

        APIDefinition apiDefinition =
                apiDefinitionRepository
                        .findByApiDefinitionId(apiDefinitionId)
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                ErrorCode.API_DEFINITION_NOT_FOUND,
                                                apiDefinitionId));

        // 转换为 VO（包含 endpoints）
        APIDefinitionVO vo = new APIDefinitionVO().convertFrom(apiDefinition);

        // 查询关联的 Endpoints
        vo.setEndpoints(
                apiEndpointRepository
                        .findByApiDefinitionIdOrderBySortOrderAsc(apiDefinitionId)
                        .stream()
                        .map(endpoint -> new APIEndpointVO().convertFrom(endpoint))
                        .collect(java.util.stream.Collectors.toList()));

        return vo;
    }

    @Override
    public PageResult<APIDefinitionVO> listAPIDefinitions(
            QueryAPIDefinitionParam param, Pageable pageable) {
        log.info("Listing API Definitions with param: {}", param);

        Page<APIDefinition> page;

        // 根据查询条件构建查询
        if (param.getType() != null && param.getStatus() != null) {
            // 同时指定类型和状态
            page =
                    apiDefinitionRepository.findByTypeAndStatus(
                            param.getType(), param.getStatus(), pageable);
        } else if (param.getType() != null) {
            // 仅指定类型
            if (StrUtil.isNotBlank(param.getKeyword())) {
                page =
                        apiDefinitionRepository.findByTypeAndNameContaining(
                                param.getType(), param.getKeyword(), pageable);
            } else {
                page = apiDefinitionRepository.findByType(param.getType(), pageable);
            }
        } else if (param.getStatus() != null) {
            // 仅指定状态
            page = apiDefinitionRepository.findByStatus(param.getStatus(), pageable);
        } else if (StrUtil.isNotBlank(param.getKeyword())) {
            // 仅指定关键词
            page = apiDefinitionRepository.findByNameContaining(param.getKeyword(), pageable);
        } else {
            // 查询全部
            page = apiDefinitionRepository.findAll(pageable);
        }

        // 转换为 VO
        return new PageResult<APIDefinitionVO>()
                .convertFrom(page, entity -> new APIDefinitionVO().convertFrom(entity));
    }

    @Override
    public APIDefinitionVO updateAPIDefinition(
            String apiDefinitionId, UpdateAPIDefinitionParam param) {
        log.info("Updating API Definition: {}", apiDefinitionId);

        APIDefinition apiDefinition =
                apiDefinitionRepository
                        .findByApiDefinitionId(apiDefinitionId)
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                ErrorCode.API_DEFINITION_NOT_FOUND,
                                                apiDefinitionId));

        // 更新字段
        param.update(apiDefinition);

        // 保存
        apiDefinition = apiDefinitionRepository.save(apiDefinition);

        // 更新 Endpoints
        if (param.getEndpoints() != null) {
            log.info("Updating endpoints for API Definition: {}", apiDefinitionId);
            // 删除旧的 endpoints
            apiEndpointRepository.deleteByApiDefinitionId(apiDefinitionId);

            // 创建新的 endpoints
            if (!param.getEndpoints().isEmpty()) {
                for (CreateEndpointParam endpointParam : param.getEndpoints()) {
                    createEndpoint(apiDefinitionId, endpointParam);
                }
            }
        }

        log.info("API Definition updated successfully: {}", apiDefinitionId);

        return new APIDefinitionVO().convertFrom(apiDefinition);
    }

    @Override
    public void deleteAPIDefinition(String apiDefinitionId) {
        log.info("Deleting API Definition: {}", apiDefinitionId);

        APIDefinition apiDefinition =
                apiDefinitionRepository
                        .findByApiDefinitionId(apiDefinitionId)
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                ErrorCode.API_DEFINITION_NOT_FOUND,
                                                apiDefinitionId));

        // 检查是否有活跃的发布记录
        boolean hasActivePublish =
                !apiPublishRecordRepository
                        .findByApiDefinitionIdAndStatus(
                                apiDefinitionId,
                                com.alibaba.himarket.support.enums.PublishStatus.ACTIVE)
                        .isEmpty();

        if (hasActivePublish) {
            throw new BusinessException(ErrorCode.ACTIVE_PUBLISH_EXISTS);
        }

        // 删除关联的 Endpoints
        apiEndpointRepository.deleteByApiDefinitionId(apiDefinitionId);

        // 删除 API Definition
        apiDefinitionRepository.delete(apiDefinition);

        log.info("API Definition deleted successfully: {}", apiDefinitionId);
    }

    @Override
    public List<APIEndpointVO> listEndpoints(String apiDefinitionId) {
        log.info("Listing endpoints for API Definition: {}", apiDefinitionId);

        // 验证 API Definition 是否存在
        if (!apiDefinitionRepository.existsByApiDefinitionId(apiDefinitionId)) {
            throw new BusinessException(ErrorCode.API_DEFINITION_NOT_FOUND, apiDefinitionId);
        }

        // 查询并转换
        return apiEndpointRepository
                .findByApiDefinitionIdOrderBySortOrderAsc(apiDefinitionId)
                .stream()
                .map(endpoint -> new APIEndpointVO().convertFrom(endpoint))
                .collect(Collectors.toList());
    }

    private APIEndpointVO createEndpoint(String apiDefinitionId, CreateEndpointParam param) {
        log.info("Creating endpoint for API Definition: {}", apiDefinitionId);

        // 验证 API Definition 是否存在
        if (!apiDefinitionRepository.existsByApiDefinitionId(apiDefinitionId)) {
            throw new BusinessException(ErrorCode.API_DEFINITION_NOT_FOUND, apiDefinitionId);
        }

        // 生成 Endpoint ID
        String endpointId = "endpoint-" + SNOWFLAKE.nextIdStr();

        // 转换为实体
        APIEndpoint endpoint = param.convertTo();
        endpoint.setEndpointId(endpointId);
        endpoint.setApiDefinitionId(apiDefinitionId);

        // 如果未指定 sortOrder，设置为当前最大值 + 1
        if (endpoint.getSortOrder() == null) {
            int maxSortOrder =
                    apiEndpointRepository
                            .findByApiDefinitionIdOrderBySortOrderAsc(apiDefinitionId)
                            .stream()
                            .mapToInt(e -> e.getSortOrder() != null ? e.getSortOrder() : 0)
                            .max()
                            .orElse(-1);
            endpoint.setSortOrder(maxSortOrder + 1);
        }

        // 保存
        endpoint = apiEndpointRepository.save(endpoint);

        log.info("Endpoint created successfully: {}", endpointId);

        // 转换为 VO
        return new APIEndpointVO().convertFrom(endpoint);
    }

    @Override
    public PageResult<APIPublishRecordVO> listPublishRecords(
            String apiDefinitionId, Pageable pageable) {
        log.info("Listing publish records for API Definition: {}", apiDefinitionId);

        // 验证 API Definition 是否存在
        if (!apiDefinitionRepository.existsByApiDefinitionId(apiDefinitionId)) {
            throw new BusinessException(ErrorCode.API_DEFINITION_NOT_FOUND, apiDefinitionId);
        }

        // 查询发布记录
        Page<APIPublishRecord> page =
                apiPublishRecordRepository.findByApiDefinitionId(apiDefinitionId, pageable);

        // 转换为 VO
        return new PageResult<APIPublishRecordVO>()
                .convertFrom(page, record -> new APIPublishRecordVO().convertFrom(record));
    }

    @Override
    public APIPublishRecordVO publishAPI(String apiDefinitionId, PublishAPIParam param) {
        log.info(
                "Publishing API Definition: {} to gateway: {}",
                apiDefinitionId,
                param.getGatewayId());

        // 验证 API Definition 是否存在
        APIDefinition apiDefinition =
                apiDefinitionRepository
                        .findByApiDefinitionId(apiDefinitionId)
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                ErrorCode.API_DEFINITION_NOT_FOUND,
                                                apiDefinitionId));

        // 验证 Gateway 是否存在
        Gateway gateway =
                gatewayRepository
                        .findByGatewayId(param.getGatewayId())
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                ErrorCode.GATEWAY_NOT_FOUND, param.getGatewayId()));

        // 检查是否已发布到该网关
        apiPublishRecordRepository
                .findByApiDefinitionIdAndGatewayId(apiDefinitionId, param.getGatewayId())
                .ifPresent(
                        record -> {
                            throw new BusinessException(
                                    ErrorCode.ALREADY_PUBLISHED, gateway.getGatewayName());
                        });

        // 【限制】检查是否已发布到其他网关（当前版本限制只能发布到一个网关）
        List<APIPublishRecord> activeRecords =
                apiPublishRecordRepository.findByApiDefinitionIdAndStatus(
                        apiDefinitionId, com.alibaba.himarket.support.enums.PublishStatus.ACTIVE);
        if (!activeRecords.isEmpty()) {
            APIPublishRecord existingRecord = activeRecords.get(0);
            throw new BusinessException(
                    ErrorCode.API_ALREADY_PUBLISHED_TO_GATEWAY, existingRecord.getGatewayName());
        }

        // 准备数据
        List<APIEndpoint> endpoints =
                apiEndpointRepository.findByApiDefinitionIdOrderBySortOrderAsc(apiDefinitionId);
        List<APIEndpointVO> endpointVOs =
                endpoints.stream()
                        .map(endpoint -> new APIEndpointVO().convertFrom(endpoint))
                        .collect(Collectors.toList());

        APIDefinitionVO apiDefinitionVO = new APIDefinitionVO().convertFrom(apiDefinition);
        apiDefinitionVO.setEndpoints(endpointVOs);

        // 获取发布器
        GatewayPublisher publisher = gatewayCapabilityRegistry.getPublisher(gateway);

        // 验证配置
        publisher.validatePublishConfig(apiDefinitionVO, endpointVOs, param.getPublishConfig());

        // 执行发布
        String gatewayResourceId =
                publisher.publish(gateway, apiDefinitionVO, param.getPublishConfig());

        // 生成发布记录 ID
        String recordId = "record-" + SNOWFLAKE.nextIdStr();

        // 创建发布记录
        APIPublishRecord publishRecord = new APIPublishRecord();
        publishRecord.setRecordId(recordId);
        publishRecord.setApiDefinitionId(apiDefinitionId);
        publishRecord.setGatewayId(param.getGatewayId());
        publishRecord.setGatewayName(gateway.getGatewayName());
        publishRecord.setGatewayType(gateway.getGatewayType().name());
        publishRecord.setStatus(com.alibaba.himarket.support.enums.PublishStatus.ACTIVE);
        publishRecord.setPublishConfig(JSONUtil.toJsonStr(param.getPublishConfig()));
        publishRecord.setPublishedAt(java.time.LocalDateTime.now());

        // 设置网关资源ID
        publishRecord.setGatewayResourceId(gatewayResourceId);

        // 保存发布记录
        publishRecord = apiPublishRecordRepository.save(publishRecord);

        // 创建发布历史记录
        createPublishHistory(
                apiDefinitionId,
                param.getGatewayId(),
                recordId,
                PublishAction.PUBLISH,
                param.getComment());

        log.info("API published successfully to gateway: {}", param.getGatewayId());

        // 转换为 VO
        return new APIPublishRecordVO().convertFrom(publishRecord);
    }

    @Override
    public void unpublishAPI(String apiDefinitionId, String recordId) {
        log.info("Unpublishing API Definition: {}, recordId: {}", apiDefinitionId, recordId);

        // 验证 API Definition 是否存在
        APIDefinition apiDefinition =
                apiDefinitionRepository
                        .findByApiDefinitionId(apiDefinitionId)
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                ErrorCode.API_DEFINITION_NOT_FOUND,
                                                apiDefinitionId));

        // 查询发布记录
        APIPublishRecord publishRecord =
                apiPublishRecordRepository
                        .findByRecordId(recordId)
                        .orElseThrow(
                                () -> new BusinessException(ErrorCode.NOT_FOUND, "发布记录", recordId));

        // 验证发布记录属于该 API Definition
        if (!publishRecord.getApiDefinitionId().equals(apiDefinitionId)) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "发布记录不属于该 API Definition");
        }

        // 获取 Gateway
        Gateway gateway =
                gatewayRepository
                        .findByGatewayId(publishRecord.getGatewayId())
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                ErrorCode.GATEWAY_NOT_FOUND,
                                                publishRecord.getGatewayId()));

        // 准备数据
        List<APIEndpoint> endpoints =
                apiEndpointRepository.findByApiDefinitionIdOrderBySortOrderAsc(apiDefinitionId);
        List<APIEndpointVO> endpointVOs =
                endpoints.stream()
                        .map(endpoint -> new APIEndpointVO().convertFrom(endpoint))
                        .collect(Collectors.toList());

        APIDefinitionVO apiDefinitionVO = new APIDefinitionVO().convertFrom(apiDefinition);
        apiDefinitionVO.setEndpoints(endpointVOs);

        // 获取发布配置
        PublishConfig publishConfig =
                JSONUtil.toBean(publishRecord.getPublishConfig(), PublishConfig.class);

        // 获取发布器并执行下线
        GatewayPublisher publisher = gatewayCapabilityRegistry.getPublisher(gateway);
        publisher.unpublish(gateway, apiDefinitionVO, publishConfig);

        // 创建取消发布历史记录
        createPublishHistory(
                apiDefinitionId,
                publishRecord.getGatewayId(),
                recordId,
                PublishAction.UNPUBLISH,
                null);

        // 更新发布记录状态为 INACTIVE
        publishRecord.setStatus(com.alibaba.himarket.support.enums.PublishStatus.INACTIVE);
        apiPublishRecordRepository.save(publishRecord);

        log.info("API unpublished successfully from gateway: {}", publishRecord.getGatewayId());
    }

    @Override
    public PageResult<APIPublishHistoryVO> listPublishHistory(
            String apiDefinitionId, Pageable pageable) {
        log.info("Listing publish history for API Definition: {}", apiDefinitionId);

        // 验证 API Definition 是否存在
        if (!apiDefinitionRepository.existsByApiDefinitionId(apiDefinitionId)) {
            throw new BusinessException(ErrorCode.API_DEFINITION_NOT_FOUND, apiDefinitionId);
        }

        // 查询发布历史（按时间倒序）
        Page<APIPublishHistory> page =
                apiPublishHistoryRepository.findByApiDefinitionIdOrderByCreateAtDesc(
                        apiDefinitionId, pageable);

        // 转换为 VO
        return new PageResult<APIPublishHistoryVO>()
                .convertFrom(page, history -> new APIPublishHistoryVO().convertFrom(history));
    }

    /** 创建发布历史记录 */
    private void createPublishHistory(
            String apiDefinitionId,
            String gatewayId,
            String recordId,
            PublishAction action,
            String comment) {
        String historyId = "history-" + SNOWFLAKE.nextIdStr();

        APIPublishHistory history = new APIPublishHistory();
        history.setHistoryId(historyId);
        history.setApiDefinitionId(apiDefinitionId);
        history.setPublishRecordId(recordId);
        history.setGatewayId(gatewayId);
        history.setAction(action);
        history.setPublishNote(comment);

        // 如果是发布操作，保存当前 API Definition 的快照
        if (action == PublishAction.PUBLISH) {
            try {
                APIDefinitionVO apiDefinitionVO = getAPIDefinition(apiDefinitionId);
                history.setSnapshot(JSONUtil.toJsonStr(apiDefinitionVO));
            } catch (Exception e) {
                log.error("Failed to create snapshot for API Definition: {}", apiDefinitionId, e);
            }
        }

        apiPublishHistoryRepository.save(history);
    }

    /** 更新发布历史为失败状态 */
    private void updatePublishHistoryFailure(String recordId, String errorMessage) {
        apiPublishHistoryRepository.findByApiDefinitionId(recordId).stream()
                .filter(h -> h.getPublishRecordId().equals(recordId))
                .findFirst()
                .ifPresent(
                        history -> {
                            history.setReason(errorMessage);
                            apiPublishHistoryRepository.save(history);
                        });
    }
}
