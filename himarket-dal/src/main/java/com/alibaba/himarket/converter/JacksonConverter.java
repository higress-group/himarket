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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import lombok.extern.slf4j.Slf4j;

/**
 * Base JPA converter for polymorphic JSON types that use Jackson's @JsonTypeInfo/@JsonSubTypes
 * annotations for deserialization. Unlike hutool-based JsonConverter, this supports proper
 * polymorphic deserialization of abstract types.
 */
@Slf4j
public abstract class JacksonConverter<T> implements AttributeConverter<T, String> {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Class<T> targetType;

    protected JacksonConverter(Class<T> targetType) {
        this.targetType = targetType;
    }

    @Override
    public String convertToDatabaseColumn(T attribute) {
        if (attribute == null) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(attribute);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize {}", targetType.getSimpleName(), e);
            throw new RuntimeException("Failed to serialize " + targetType.getSimpleName(), e);
        }
    }

    @Override
    public T convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            return null;
        }
        try {
            return MAPPER.readValue(dbData, targetType);
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize {}", targetType.getSimpleName(), e);
            throw new RuntimeException("Failed to deserialize " + targetType.getSimpleName(), e);
        }
    }
}
