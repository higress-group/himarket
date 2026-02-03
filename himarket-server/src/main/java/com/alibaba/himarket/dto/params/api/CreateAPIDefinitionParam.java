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

package com.alibaba.himarket.dto.params.api;

import com.alibaba.himarket.dto.converter.InputConverter;
import com.alibaba.himarket.entity.APIDefinition;
import com.alibaba.himarket.support.api.property.APIPolicy;
import com.alibaba.himarket.support.enums.APIType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Data;

@Data
public class CreateAPIDefinitionParam implements InputConverter<APIDefinition> {

    @NotBlank(message = "API名称不能为空")
    @Size(max = 100, message = "API名称长度不能超过100个字符")
    private String name;

    @Size(max = 500, message = "API描述长度不能超过500个字符")
    private String description;

    @NotNull(message = "API类型不能为空")
    private APIType type;

    @Size(max = 50, message = "版本号长度不能超过50个字符")
    private String version;

    private List<APIPolicy> policies;
}
