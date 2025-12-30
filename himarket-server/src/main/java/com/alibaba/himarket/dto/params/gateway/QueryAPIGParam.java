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

package com.alibaba.himarket.dto.params.gateway;

import com.alibaba.himarket.dto.converter.InputConverter;
import com.alibaba.himarket.support.enums.GatewayType;
import com.alibaba.himarket.support.gateway.APIGConfig;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class QueryAPIGParam implements InputConverter<APIGConfig> {

    @NotBlank(message = "Gateway region cannot be blank")
    private String region;

    @NotNull(message = "Gateway type cannot be null")
    private GatewayType gatewayType;

    @NotBlank(message = "Access key cannot be blank")
    private String accessKey;

    @NotBlank(message = "Secret key cannot be blank")
    private String secretKey;
}
