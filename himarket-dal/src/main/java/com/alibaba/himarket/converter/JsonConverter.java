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
import cn.hutool.json.JSONUtil;
import com.alibaba.himarket.support.common.Encrypted;
import com.alibaba.himarket.support.common.Encryptor;
import jakarta.persistence.AttributeConverter;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class JsonConverter<T> implements AttributeConverter<T, String> {

    private final Class<T> type;

    protected JsonConverter(Class<T> type) {
        this.type = type;
    }

    @Override
    public String convertToDatabaseColumn(T attribute) {
        if (attribute == null) {
            return null;
        }

        T clonedAttribute = cloneAndEncrypt(attribute);
        return JSONUtil.toJsonStr(clonedAttribute);
    }

    @Override
    @SuppressWarnings("unchecked")
    public T convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            return null;
        }
        if (List.class.isAssignableFrom(type)) {
            T attribute = (T) JSONUtil.toList(dbData, Object.class);
            decrypt(attribute);
            return attribute;
        }

        // 检查类型是否为枚举，如果是则直接处理
        if (type.isEnum()) {
            // 对于枚举类型，直接通过valueOf方法解析
            try {
                return (T) Enum.valueOf((Class<? extends Enum>) type, dbData);
            } catch (IllegalArgumentException e) {
                log.warn("Failed to parse enum value: {} for type: {}", dbData, type.getName(), e);
                return null;
            }
        }
        
        T attribute = JSONUtil.toBean(dbData, type);
        decrypt(attribute);
        return attribute;
    }

    @SuppressWarnings("unchecked")
    private T cloneAndEncrypt(T original) {
        // Clone to avoid automatic database updates through JPA persistence
        T cloned;
        if (original != null && original.getClass().isEnum()) {
            // 避免对枚举类型进行克隆，直接返回原始值
            cloned = original;
        } else if (original instanceof List) {
            cloned = (T) new ArrayList<>((List<?>) original);
        } else {
            cloned = JSONUtil.toBean(JSONUtil.toJsonStr(original), type);
        }
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
        
        // 避免对集合类型进行递归处理，防止访问内部字段
        if (obj instanceof List) {
            List<?> list = (List<?>) obj;
            for (Object item : list) {
                if (item != null && !ClassUtil.isSimpleValueType(item.getClass()) && !item.getClass().isEnum()) {
                    handleEncryption(item, isEncrypt);
                }
            }
            return;
        }

        // 避免对枚举类型进行反射处理，防止访问java.lang.Enum的私有字段
        if (obj.getClass().isEnum()) {
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
                    } else if (!ClassUtil.isSimpleValueType(value.getClass()) && !(value instanceof List) && !value.getClass().isEnum()) {
                        // 避免再次处理List和Enum类型，防止访问内部字段
                        handleEncryption(value, isEncrypt);
                    }
                });
    }
}
