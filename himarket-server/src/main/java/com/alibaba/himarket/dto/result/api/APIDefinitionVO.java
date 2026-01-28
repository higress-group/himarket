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

package com.alibaba.himarket.dto.result.api;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.alibaba.himarket.dto.converter.OutputConverter;
import com.alibaba.himarket.entity.APIDefinition;
import com.alibaba.himarket.support.api.property.BaseAPIProperty;
import com.alibaba.himarket.support.enums.APIStatus;
import com.alibaba.himarket.support.enums.APIType;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.Data;

@Data
public class APIDefinitionVO implements OutputConverter<APIDefinitionVO, APIDefinition> {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private String apiDefinitionId;

    private String name;

    private String description;

    private APIType type;

    private APIStatus status;

    private String version;

    private String basePath;

    private List<BaseAPIProperty> properties;

    private Map<String, Object> metadata;

    private List<APIEndpointVO> endpoints;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @Override
    public APIDefinitionVO convertFrom(APIDefinition source) {
        OutputConverter.super.convertFrom(source);

        // 处理 properties JSON 字段 - 转换为 APIProperties 类型
        if (StrUtil.isNotBlank(source.getProperties())) {
            try {
                this.properties =
                        objectMapper.readValue(
                                source.getProperties(),
                                new TypeReference<List<BaseAPIProperty>>() {});
            } catch (Exception e) {
                this.properties = null;
            }
        }

        // 处理 metadata JSON 字段
        if (StrUtil.isNotBlank(source.getMetadata())) {
            try {
                this.metadata = JSONUtil.toBean(source.getMetadata(), Map.class);
            } catch (Exception e) {
                this.metadata = null;
            }
        }

        return this;
    }
}
