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

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.ClassUtil;
import cn.hutool.core.util.ReflectUtil;
import com.alibaba.himarket.support.common.Encrypted;
import com.alibaba.himarket.support.common.Encryptor;
import com.alibaba.himarket.utils.JsonUtil;
import jakarta.persistence.AttributeConverter;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class JsonConverter<T> implements AttributeConverter<T, String> {

    /**
     * Field type: object class for simple types, List.class for List, Map.class for Map
     */
    private final Class<T> type;

    /**
     * Element type for List (e.g., ChatAttachmentConfig.class for List<ChatAttachmentConfig>), null
     * for simple types
     */
    private final Class<?> elementType;

    /**
     * For simple object types: super(ChatUsage.class)
     */
    protected JsonConverter(Class<T> type) {
        this(type, null);
    }

    /**
     * For List types: super(List.class, ChatAttachmentConfig.class)
     */
    protected JsonConverter(Class<T> type, Class<?> elementType) {
        this.type = type;
        this.elementType = elementType;
    }

    @Override
    public String convertToDatabaseColumn(T attribute) {
        if (attribute == null) {
            return null;
        }

        T clonedAttribute = cloneAndEncrypt(attribute);
        return JsonUtil.toJson(clonedAttribute);
    }

    @Override
    @SuppressWarnings("unchecked")
    public T convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            return null;
        }
        if (List.class.isAssignableFrom(type)) {
            // Use elementType if specified, otherwise fallback to Object.class for backward
            // compatibility
            Class<?> listElementType = elementType != null ? elementType : Object.class;
            T attribute = (T) JsonUtil.parseArray(dbData, listElementType);
            decrypt(attribute);
            return attribute;
        }

        T attribute = JsonUtil.parse(dbData, type);
        decrypt(attribute);
        return attribute;
    }

    @SuppressWarnings("unchecked")
    private T cloneAndEncrypt(T original) {
        // Clone to avoid automatic database updates through JPA persistence
        T cloned =
                original instanceof List
                        ? (T) new ArrayList<>((List<?>) original)
                        : JsonUtil.parse(JsonUtil.toJson(original), type);
        handleEncryption(cloned, true);
        return cloned;
    }

    private void decrypt(T attribute) {
        handleEncryption(attribute, false);
    }

    private void handleEncryption(Object obj, boolean isEncrypt) {
        if (obj == null) {
            return;
        }

        BeanUtil.descForEach(
                obj.getClass(),
                pd -> {
                    Field field = pd.getField();
                    if (field == null) {
                        return;
                    }

                    Object value = ReflectUtil.getFieldValue(obj, field);
                    if (value == null) {
                        return;
                    }

                    // Process fields that require encryption/decryption
                    if (field.isAnnotationPresent(Encrypted.class) && value instanceof String) {
                        String result =
                                isEncrypt
                                        ? Encryptor.encrypt((String) value)
                                        : Encryptor.decrypt((String) value);
                        ReflectUtil.setFieldValue(obj, field, result);
                    } else if (!ClassUtil.isSimpleValueType(value.getClass())) {
                        handleEncryption(value, isEncrypt);
                    }
                });
    }
}
