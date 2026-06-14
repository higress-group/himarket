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

package com.alibaba.himarket.dto.converter;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

/**
 * Generic converter that converts a DTO into a domain object.
 *
 * <p>Example: CreatePortalRequest -> Portal.
 *
 * @param <D> target domain object type
 */
public interface InputConverter<D> {

    /**
     * Converts the current object to the target domain object.
     *
     * @return converted domain object
     */
    default D convertTo() {
        ParameterizedType currentType = parameterizedType();

        @SuppressWarnings("unchecked")
        Class<D> clazz = (Class<D>) currentType.getActualTypeArguments()[0];
        return BeanUtil.copyProperties(this, clazz);
    }

    /**
     * Gets the parameterized type of the current converter.
     *
     * @return parameterized converter type, or {@code null} if it cannot be resolved
     */
    default ParameterizedType parameterizedType() {
        Type[] interfaces = this.getClass().getGenericInterfaces();

        // Find the parameterized InputConverter interface.
        for (Type type : interfaces) {
            if (type instanceof ParameterizedType) {
                ParameterizedType parameterizedType = (ParameterizedType) type;
                if (parameterizedType.getRawType().equals(InputConverter.class)) {
                    return parameterizedType;
                }
            }
        }

        // Fall back to the parameterized superclass when the interface is not declared directly.
        Class<?> superclass = this.getClass().getSuperclass();
        if (superclass == null || superclass.equals(Object.class)) {
            return null;
        }

        Type superType = superclass.getGenericSuperclass();
        if (superType instanceof ParameterizedType) {
            return (ParameterizedType) superType;
        }

        return null;
    }

    /**
     * Updates the target domain object with values from the current object.
     *
     * @param domain target domain object
     */
    default void update(D domain) {
        BeanUtil.copyProperties(this, domain, CopyOptions.create().setIgnoreNullValue(true));
    }
}
