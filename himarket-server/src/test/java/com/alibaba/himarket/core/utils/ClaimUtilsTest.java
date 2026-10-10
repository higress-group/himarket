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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Map;
import org.junit.jupiter.api.Test;

class ClaimUtilsTest {

    @Test
    void resolveTopLevelClaim() {
        Map<String, Object> claims = Map.of("sub", "user-1", "name", "Alice");

        assertEquals("user-1", ClaimUtils.resolveClaim(claims, "sub"));
        assertEquals("Alice", ClaimUtils.resolveClaim(claims, "name"));
    }

    @Test
    void resolveNestedClaimByDotPath() {
        // Feishu authen user_info style envelope: {"code":0,"data":{...},"msg":"success"}
        Map<String, Object> claims =
                Map.of(
                        "code",
                        0,
                        "data",
                        Map.of("open_id", "ou_abc", "name", "Alice", "email", "a@example.com"),
                        "msg",
                        "success");

        assertEquals("ou_abc", ClaimUtils.resolveClaim(claims, "data.open_id"));
        assertEquals("Alice", ClaimUtils.resolveClaim(claims, "data.name"));
        assertEquals("a@example.com", ClaimUtils.resolveClaim(claims, "data.email"));
    }

    @Test
    void topLevelKeyContainingDotWinsOverNestedPath() {
        Map<String, Object> claims =
                Map.of("data.name", "flat-value", "data", Map.of("name", "nested-value"));

        assertEquals("flat-value", ClaimUtils.resolveClaim(claims, "data.name"));
    }

    @Test
    void returnNullForMissingOrInvalidPath() {
        Map<String, Object> claims = Map.of("data", Map.of("name", "Alice"), "plain", "value");

        assertNull(ClaimUtils.resolveClaim(claims, "data.missing"));
        assertNull(ClaimUtils.resolveClaim(claims, "missing.name"));
        // Intermediate segment is not a map
        assertNull(ClaimUtils.resolveClaim(claims, "plain.name"));
        assertNull(ClaimUtils.resolveClaim(claims, ""));
        assertNull(ClaimUtils.resolveClaim(null, "data.name"));
    }
}
