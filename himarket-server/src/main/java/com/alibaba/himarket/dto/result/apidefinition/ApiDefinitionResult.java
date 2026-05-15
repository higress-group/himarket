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

package com.alibaba.himarket.dto.result.apidefinition;

import com.alibaba.himarket.dto.converter.OutputConverter;
import com.alibaba.himarket.entity.ApiDefinition;
import com.alibaba.himarket.support.api.meta.ApiDefinitionMeta;
import com.alibaba.himarket.support.api.policy.ApiPolicy;
import com.alibaba.himarket.support.api.spec.ApiSpec;
import com.alibaba.himarket.support.enums.ApiStatus;
import com.alibaba.himarket.support.enums.ApiType;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;

@Data
public class ApiDefinitionResult implements OutputConverter<ApiDefinitionResult, ApiDefinition> {

    private String apiDefinitionId;

    private String name;

    private String description;

    private ApiType type;

    private ApiStatus status;

    private String version;

    private ApiSpec spec;

    private ApiDefinitionMeta meta;

    private List<ApiPolicy> policies;

    private LocalDateTime createAt;

    private LocalDateTime updatedAt;
}
