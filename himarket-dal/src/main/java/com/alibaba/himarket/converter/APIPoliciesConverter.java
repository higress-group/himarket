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

package com.alibaba.himarket.converter;

import com.alibaba.himarket.support.api.policy.ApiPolicy;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Converter
@Slf4j
public class APIPoliciesConverter implements AttributeConverter<List<ApiPolicy>, String> {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<List<ApiPolicy>> TYPE_REF = new TypeReference<>() {};

    @Override
    public String convertToDatabaseColumn(List<ApiPolicy> attribute) {
        if (attribute == null) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(attribute);
        } catch (Exception e) {
            log.error("Failed to serialize APIPolicy list, errorMessage={}", e.getMessage(), e);
            throw new RuntimeException("Failed to serialize APIPolicy list: " + e.getMessage(), e);
        }
    }

    @Override
    public List<ApiPolicy> convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            return null;
        }
        try {
            return MAPPER.readValue(dbData, TYPE_REF);
        } catch (Exception e) {
            log.error("Failed to deserialize APIPolicy list, errorMessage={}", e.getMessage(), e);
            throw new RuntimeException(
                    "Failed to deserialize APIPolicy list: " + e.getMessage(), e);
        }
    }
}
