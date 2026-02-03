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

package com.alibaba.himarket.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;
import java.util.List;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.Assert;

/**
 * JSON utility class based on Jackson.
 */
@Slf4j
public class JsonUtil {

    public static final ObjectMapper DEFAULT_JSON_MAPPER = createDefaultJsonMapper();

    /**
     * Creates default ObjectMapper instance.
     *
     * @return default ObjectMapper
     */
    public static ObjectMapper createDefaultJsonMapper() {
        return createJsonMapper(null);
    }

    /**
     * Creates ObjectMapper with custom naming strategy.
     *
     * @param strategy property naming strategy
     * @return configured ObjectMapper
     */
    @NonNull
    public static ObjectMapper createJsonMapper(PropertyNamingStrategy strategy) {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        if (strategy != null) {
            mapper.setPropertyNamingStrategy(strategy);
        }
        return mapper;
    }

    /**
     * Creates default ObjectNode instance.
     *
     * @return ObjectNode
     */
    public static ObjectNode createObjectNode() {
        return createObjectNode(DEFAULT_JSON_MAPPER);
    }

    /**
     * Creates ObjectNode with custom ObjectMapper.
     *
     * @param objectMapper ObjectMapper instance
     * @return ObjectNode
     */
    @NonNull
    public static ObjectNode createObjectNode(ObjectMapper objectMapper) {
        Assert.notNull(objectMapper, "Object mapper must not null");
        return objectMapper.createObjectNode();
    }

    /**
     * Parses JSON string to object.
     *
     * @param string JSON string
     * @param type   target class
     * @param <T>    target type
     * @return parsed object
     */
    public static <T> T parse(String string, Class<T> type) {
        return parse(string, type, DEFAULT_JSON_MAPPER);
    }

    /**
     * Parses JSON string to object with TypeReference.
     *
     * @param string       JSON string
     * @param valueTypeRef type reference
     * @param <T>          target type
     * @return parsed object
     */
    public static <T> T parse(String string, TypeReference<T> valueTypeRef) {
        return parse(string, valueTypeRef, DEFAULT_JSON_MAPPER);
    }

    /**
     * Parses JSON array to List.
     *
     * @param string JSON array string
     * @param type   element class
     * @param <T>    element type
     * @return parsed list
     */
    public static <T> List<T> parseArray(String string, Class<T> type) {
        return parse(string, new TypeReference<>() {});
    }

    /**
     * Parses JSON string to object with custom ObjectMapper.
     *
     * @param string       JSON string
     * @param type         target class
     * @param objectMapper ObjectMapper instance
     * @param <T>          target type
     * @return parsed object
     */
    public static <T> T parse(String string, Class<T> type, ObjectMapper objectMapper) {
        Assert.notNull(type, "Target type must not be null");
        Assert.notNull(objectMapper, "Object mapper must not null");

        if (string == null || string.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(string, type);
        } catch (JsonProcessingException e) {
            log.error(
                    "Failed to parse JSON: json={}, type={}, error={}",
                    string,
                    type.getName(),
                    e.getMessage(),
                    e);
            throw new RuntimeException(e);
        }
    }

    /**
     * Parses JSON string to object with TypeReference and custom ObjectMapper.
     *
     * @param string       JSON string
     * @param valueTypeRef type reference
     * @param objectMapper ObjectMapper instance
     * @param <T>          target type
     * @return parsed object
     */
    public static <T> T parse(
            String string, TypeReference<T> valueTypeRef, ObjectMapper objectMapper) {
        Assert.notNull(valueTypeRef, "ValueTypeRef must not be null");
        Assert.notNull(objectMapper, "Object mapper must not null");

        if (string == null || string.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(string, valueTypeRef);
        } catch (JsonProcessingException e) {
            log.error(
                    "Failed to parse JSON: json={}, typeRef={}, error={}",
                    string,
                    valueTypeRef.getType(),
                    e.getMessage(),
                    e);
            throw new RuntimeException(e);
        }
    }

    /**
     * Converts object to JSON string.
     *
     * @param source source object
     * @return JSON string
     */
    public static String toJson(Object source) {
        return toJson(source, DEFAULT_JSON_MAPPER);
    }

    /**
     * Converts object to JSON string with custom ObjectMapper.
     *
     * @param source       source object
     * @param objectMapper ObjectMapper instance
     * @return JSON string
     */
    @NonNull
    public static String toJson(Object source, ObjectMapper objectMapper) {
        Assert.notNull(objectMapper, "Object mapper must not be null");

        try {
            return objectMapper.writeValueAsString(source);
        } catch (JsonProcessingException e) {
            log.error("Failed to convert to JSON: error={}", e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    /**
     * Converts object to target type.
     *
     * @param obj  source object
     * @param type target class
     * @param <T>  target type
     * @return converted object
     */
    public static <T> T convert(Object obj, Class<T> type) {
        return convert(obj, type, DEFAULT_JSON_MAPPER);
    }

    /**
     * Converts object to target type with custom ObjectMapper.
     *
     * @param obj          source object
     * @param type         target class
     * @param objectMapper ObjectMapper instance
     * @param <T>          target type
     * @return converted object
     */
    public static <T> T convert(Object obj, Class<T> type, ObjectMapper objectMapper) {
        Assert.notNull(type, "Target type must not be null");
        Assert.notNull(objectMapper, "Object mapper must not be null");

        return objectMapper.convertValue(obj, type);
    }

    /**
     * Reads JSON string to JsonNode.
     *
     * @param jsonStr JSON string
     * @return JsonNode
     */
    public static JsonNode readTree(String jsonStr) {
        Assert.hasText(jsonStr, "JsonStr must not be blank");

        try {
            return DEFAULT_JSON_MAPPER.readTree(jsonStr);
        } catch (IOException e) {
            log.error("Failed to read JSON tree: json={}, error={}", jsonStr, e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    /**
     * Reads value from JSON string by key.
     *
     * @param jsonStr     JSON string
     * @param key         field key
     * @param ignoreError ignore error flag
     * @return field value
     */
    public static String readValue(String jsonStr, String key, boolean ignoreError) {
        Assert.hasText(jsonStr, "JsonStr must not be blank");
        Assert.hasText(key, "Key must not be blank");

        try {
            JsonNode node = readTree(jsonStr);
            return node.get(key).asText();
        } catch (Exception e) {
            if (ignoreError) {
                log.warn("Failed to parse JSON: jsonStr={}, key={}", jsonStr, key);
            } else {
                throw new RuntimeException(e);
            }
        }
        return null;
    }
}
