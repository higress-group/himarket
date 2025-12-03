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

package com.alibaba.apiopenplatform.converter;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.ClassUtil;
import cn.hutool.core.util.ReflectUtil;
import cn.hutool.json.JSONUtil;
import com.alibaba.apiopenplatform.support.common.Encrypted;
import com.alibaba.apiopenplatform.support.common.Encryptor;
import lombok.extern.slf4j.Slf4j;

import jakarta.persistence.AttributeConverter;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

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

        T attribute = JSONUtil.toBean(dbData, type);
        decrypt(attribute);
        return attribute;
    }

    @SuppressWarnings("unchecked")
    private T cloneAndEncrypt(T original) {
        // Clone避免JPA更新数据
//        T cloned = JSONUtil.toBean(JSONUtil.toJsonStr(original), type);
        T cloned = original instanceof List ?
                (T) new ArrayList<>((List<?>) original) :
                JSONUtil.toBean(JSONUtil.toJsonStr(original), type);
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

        BeanUtil.descForEach(obj.getClass(), pd -> {
            Field field = pd.getField();
            if (field == null) {
                return;
            }

            Object value = ReflectUtil.getFieldValue(obj, field);
            if (value == null) {
                return;
            }

            // 处理需要加密/解密的字段
            if (field.isAnnotationPresent(Encrypted.class) && value instanceof String) {
                String result = isEncrypt ?
                        Encryptor.encrypt((String) value) :
                        Encryptor.decrypt((String) value);
                ReflectUtil.setFieldValue(obj, field, result);
            } else if (!ClassUtil.isSimpleValueType(value.getClass())) {
                handleEncryption(value, isEncrypt);
            }
        });
    }
}
