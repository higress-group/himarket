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

package com.alibaba.himarket.dto.params.api;

import com.alibaba.himarket.dto.converter.InputConverter;
import com.alibaba.himarket.entity.APIEndpoint;
import com.alibaba.himarket.support.api.EndpointConfig;
import com.alibaba.himarket.support.enums.EndpointType;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateEndpointParam implements InputConverter<APIEndpoint> {

    @NotBlank(message = "端点名称不能为空")
    @Size(max = 100, message = "端点名称长度不能超过100个字符")
    private String name;

    @Size(max = 500, message = "端点描述长度不能超过500个字符")
    private String description;

    @NotNull(message = "端点类型不能为空")
    private EndpointType type;

    private Integer sortOrder;

    @NotNull(message = "端点配置不能为空")
    private EndpointConfig config;

    @Override
    public APIEndpoint convertTo() {
        APIEndpoint endpoint = InputConverter.super.convertTo();

        // 手动处理 config 字段：将 EndpointConfig 对象序列化为 JSON 字符串
        if (this.config != null) {
            try {
                ObjectMapper objectMapper = new ObjectMapper();
                endpoint.setConfig(objectMapper.writeValueAsString(this.config));
            } catch (JsonProcessingException e) {
                throw new RuntimeException("Failed to serialize endpoint config to JSON", e);
            }
        }

        return endpoint;
    }
}
