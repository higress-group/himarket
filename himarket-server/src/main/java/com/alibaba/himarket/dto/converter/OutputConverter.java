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

import java.beans.PropertyDescriptor;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.core.convert.support.DefaultConversionService;

public interface OutputConverter<Target extends OutputConverter<Target, Source>, Source> {

    /**
     * Updates the current target object from a source object.
     *
     * @param source source object
     * @return updated target object
     */
    @SuppressWarnings("unchecked")
    default Target convertFrom(Source source) {
        if (source == null) {
            return (Target) this;
        }
        copyProperties(source, this);
        return (Target) this;
    }

    private static void copyProperties(Object source, Object target) {
        BeanWrapper sourceWrapper = new BeanWrapperImpl(source);
        BeanWrapper targetWrapper = new BeanWrapperImpl(target);
        targetWrapper.setConversionService(DefaultConversionService.getSharedInstance());

        for (PropertyDescriptor targetProperty : targetWrapper.getPropertyDescriptors()) {
            String propertyName = targetProperty.getName();
            if ("class".equals(propertyName)
                    || !sourceWrapper.isReadableProperty(propertyName)
                    || !targetWrapper.isWritableProperty(propertyName)) {
                continue;
            }

            Object value = sourceWrapper.getPropertyValue(propertyName);
            if (value == null) {
                continue;
            }

            try {
                targetWrapper.setPropertyValue(propertyName, value);
            } catch (RuntimeException ignored) {
                // Keep the previous ignore-error behavior for result conversion.
            }
        }
    }
}
