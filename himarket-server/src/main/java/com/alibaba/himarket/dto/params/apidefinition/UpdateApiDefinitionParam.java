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

package com.alibaba.himarket.dto.params.apidefinition;

import com.alibaba.himarket.dto.converter.InputConverter;
import com.alibaba.himarket.entity.ApiDefinition;
import com.alibaba.himarket.support.api.policy.ApiPolicy;
import com.alibaba.himarket.support.api.spec.ApiSpec;
import com.alibaba.himarket.support.enums.ApiStatus;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Data;

@Data
public class UpdateApiDefinitionParam implements InputConverter<ApiDefinition> {

    @Size(max = 255, message = "API definition name cannot exceed 255 characters")
    private String name;

    @Size(max = 512, message = "API definition description cannot exceed 512 characters")
    private String description;

    private ApiStatus status;

    private String version;

    private ApiSpec spec;

    private List<ApiPolicy> policies;
}
