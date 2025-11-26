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

package com.alibaba.apiopenplatform.dto.params.nacos;

import com.alibaba.apiopenplatform.dto.converter.InputConverter;
import com.alibaba.apiopenplatform.entity.NacosInstance;

import lombok.Data;

import jakarta.validation.constraints.NotBlank;

/**
 * 创建Nacos实例参数
 *
 */
@Data
public class CreateNacosParam implements InputConverter<NacosInstance> {

    @NotBlank(message = "Nacos名称不能为空")
    private String nacosName;

    @NotBlank(message = "服务器地址不能为空")
    private String serverUrl;

    /**
     * 可选：客户端指定的 nacosId，若为空则由服务端生成
     */
    private String nacosId;

    // namespace removed from create param as it's no longer stored on instance

    private String username;

    private String password;

    private String accessKey;

    private String secretKey;

    private String description;
}
