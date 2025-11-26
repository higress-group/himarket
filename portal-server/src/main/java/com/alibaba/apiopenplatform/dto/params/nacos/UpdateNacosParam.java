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
import jakarta.validation.constraints.Size;

/**
 * 更新Nacos实例参数
 *
 */
@Data
public class UpdateNacosParam implements InputConverter<NacosInstance> {

    @NotBlank(message = "Nacos名称不能为空")
    @Size(max = 64, message = "Nacos名称长度不能超过64个字符")
    private String nacosName;

    @NotBlank(message = "服务器地址不能为空")
    @Size(max = 256, message = "服务器地址长度不能超过256个字符")
    private String serverUrl;

    // namespace removed from update param as it's no longer stored on instance

    @Size(max = 64, message = "用户名长度不能超过64个字符")
    private String username;

    @Size(max = 128, message = "密码长度不能超过128个字符")
    private String password;

    @Size(max = 128, message = "AccessKey 长度不能超过128个字符")
    private String accessKey;

    @Size(max = 256, message = "SecretKey 长度不能超过256个字符")
    private String secretKey;

    @Size(max = 512, message = "描述长度不能超过512个字符")
    private String description;
    }
