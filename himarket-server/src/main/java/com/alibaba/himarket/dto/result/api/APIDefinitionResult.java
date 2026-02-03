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

package com.alibaba.himarket.dto.result.api;

import com.alibaba.himarket.dto.converter.OutputConverter;
import com.alibaba.himarket.entity.APIDefinition;
import com.alibaba.himarket.support.api.property.APIPolicy;
import com.alibaba.himarket.support.api.v2.spec.APISpec;
import com.alibaba.himarket.support.enums.APIStatus;
import com.alibaba.himarket.support.enums.APIType;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;

@Data
public class APIDefinitionResult implements OutputConverter<APIDefinitionResult, APIDefinition> {

    private String apiDefinitionId;

    private String name;

    private String description;

    private APIType type;

    private APIStatus status;

    private String version;

    private List<APIPolicy> policies;

    private APISpec spec;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @Override
    public APIDefinitionResult convertFrom(APIDefinition source) {
        OutputConverter.super.convertFrom(source);

        // 直接复制 policies 和 spec（无需手动 JSON 转换）
        this.policies = source.getPolicies();
        this.spec = source.getSpec();

        return this;
    }
}
