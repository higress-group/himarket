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

package com.alibaba.himarket.dto.params.airegistry;

import com.alibaba.himarket.dto.converter.InputConverter;
import com.alibaba.himarket.entity.AiRegistryInstance;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateAiRegistryParam implements InputConverter<AiRegistryInstance> {

    @NotBlank(message = "AIRegistry name cannot be blank")
    @Size(max = 64, message = "AIRegistry name cannot exceed 64 characters")
    private String name;

    @NotBlank(message = "Region ID cannot be blank")
    @Size(max = 64, message = "Region ID cannot exceed 64 characters")
    private String regionId;

    @Size(max = 256, message = "Endpoint cannot exceed 256 characters")
    private String endpoint;

    @NotBlank(message = "Namespace ID cannot be blank")
    @Size(max = 128, message = "Namespace ID cannot exceed 128 characters")
    private String namespaceId;

    @Size(max = 128, message = "Access key ID cannot exceed 128 characters")
    private String accessKeyId;

    @Size(max = 512, message = "Access key secret cannot exceed 512 characters")
    private String accessKeySecret;

    @Size(max = 1024, message = "Security token cannot exceed 1024 characters")
    private String securityToken;

    @Size(max = 512, message = "Description cannot exceed 512 characters")
    private String description;
}
