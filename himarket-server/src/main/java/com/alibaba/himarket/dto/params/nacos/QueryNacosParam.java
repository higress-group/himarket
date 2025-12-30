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

package com.alibaba.himarket.dto.params.nacos;

import com.aliyun.teaopenapi.models.Config;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class QueryNacosParam {

    @NotBlank(message = "Region ID cannot be blank")
    private String regionId;

    @NotBlank(message = "Access key cannot be blank")
    private String accessKey;

    @NotBlank(message = "Secret key cannot be blank")
    private String secretKey;

    public Config toClientConfig() {
        Config config =
                new Config()
                        .setAccessKeyId(this.accessKey)
                        .setAccessKeySecret(this.secretKey)
                        .setRegionId(this.regionId);
        return config;
    }
}
