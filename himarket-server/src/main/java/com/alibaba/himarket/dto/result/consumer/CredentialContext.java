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

package com.alibaba.himarket.dto.result.consumer;

import java.util.HashMap;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CredentialContext {

    private String apiKey;

    @Builder.Default private Map<String, String> headers = new HashMap<>();

    @Builder.Default private Map<String, String> queryParams = new HashMap<>();

    /**
     * Returns a copy of headers to prevent modifications to the original map
     *
     * @return
     */
    public Map<String, String> copyHeaders() {
        if (headers == null) {
            return new HashMap<>();
        }
        return new HashMap<>(headers);
    }

    public Map<String, String> copyQueryParams() {
        if (queryParams == null) {
            return new HashMap<>();
        }
        return new HashMap<>(queryParams);
    }
}
