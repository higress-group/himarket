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

import com.alibaba.himarket.dto.converter.OutputConverter;
import com.alibaba.himarket.entity.APIEndpoint;
import com.alibaba.himarket.support.api.endpoint.EndpointConfig;
import com.alibaba.himarket.support.enums.EndpointType;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Data
@Slf4j
public class APIEndpointVO implements OutputConverter<APIEndpointVO, APIEndpoint> {

    private String endpointId;

    private String apiDefinitionId;

    private EndpointType type;

    private String name;

    private String description;

    private Integer sortOrder;

    private EndpointConfig config;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @Override
    public APIEndpointVO convertFrom(APIEndpoint domain) {
        OutputConverter.super.convertFrom(domain);

        // 手动处理 config 字段：将 JSON 字符串反序列化为 EndpointConfig 对象
        if (domain.getConfig() != null) {
            try {
                ObjectMapper objectMapper = new ObjectMapper();
                this.config = objectMapper.readValue(domain.getConfig(), EndpointConfig.class);
            } catch (JsonProcessingException e) {
                log.error("Failed to deserialize endpoint config from JSON", e);
                this.config = null;
            }
        }

        return this;
    }
}
