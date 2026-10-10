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

package com.alibaba.himarket.core.utils;

import com.alibaba.himarket.support.common.Strings;
import java.util.Map;

public final class ClaimUtils {

    private static final String PATH_SEPARATOR = "\\.";

    private ClaimUtils() {}

    /**
     * Resolves a claim value by field name. A field matching a top-level claim is returned
     * directly; otherwise a dot-separated field (e.g. {@code data.open_id}) is resolved as a
     * nested path, so identity providers that wrap user info in an envelope (such as Feishu's
     * {@code {"code":0,"data":{...}}}) can be mapped through configuration.
     */
    public static Object resolveClaim(Map<String, Object> claims, String field) {
        if (claims == null || Strings.isBlank(field)) {
            return null;
        }

        // Top-level claim wins to stay compatible with keys that contain dots
        if (claims.containsKey(field)) {
            return claims.get(field);
        }
        if (!field.contains(".")) {
            return null;
        }

        Object current = claims;
        for (String segment : field.split(PATH_SEPARATOR)) {
            if (!(current instanceof Map<?, ?> map)) {
                return null;
            }
            current = map.get(segment);
        }
        return current;
    }
}
