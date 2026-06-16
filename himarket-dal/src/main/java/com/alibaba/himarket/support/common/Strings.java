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

package com.alibaba.himarket.support.common;

import org.springframework.util.StringUtils;

public final class Strings {

    private Strings() {}

    public static boolean isBlank(CharSequence value) {
        return !StringUtils.hasText(value);
    }

    public static boolean isNotBlank(CharSequence value) {
        return StringUtils.hasText(value);
    }

    public static boolean hasBlank(CharSequence... values) {
        if (values == null || values.length == 0) {
            return true;
        }

        for (CharSequence value : values) {
            if (isBlank(value)) {
                return true;
            }
        }
        return false;
    }

    public static String blankToDefault(String value, String defaultValue) {
        return isBlank(value) ? defaultValue : value;
    }

    public static boolean equals(CharSequence left, CharSequence right) {
        if (left == right) {
            return true;
        }
        if (left == null || right == null) {
            return false;
        }
        return left.toString().contentEquals(right);
    }

    public static boolean equalsIgnoreCase(CharSequence left, CharSequence right) {
        if (left == right) {
            return true;
        }
        if (left == null || right == null) {
            return false;
        }
        return left.toString().equalsIgnoreCase(right.toString());
    }
}
