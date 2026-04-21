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

package com.alibaba.himarket.dto.params.sandbox;

import com.alibaba.himarket.dto.converter.InputConverter;
import com.alibaba.himarket.entity.SandboxInstance;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ImportSandboxParam implements InputConverter<SandboxInstance> {

    @NotBlank(message = "实例名称不能为空")
    private String sandboxName;

    @NotBlank(message = "实例类型不能为空")
    private String sandboxType;

    @NotBlank(message = "KubeConfig不能为空")
    private String kubeConfig;

    private String description;
}
