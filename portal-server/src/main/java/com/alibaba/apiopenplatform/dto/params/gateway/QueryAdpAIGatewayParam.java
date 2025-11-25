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


package com.alibaba.apiopenplatform.dto.params.gateway;

import com.alibaba.apiopenplatform.dto.converter.InputConverter;
import com.alibaba.apiopenplatform.support.gateway.AdpAIGatewayConfig;
import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 查询 ADP 网关实例列表所需的连接参数
 */
@Data
public class QueryAdpAIGatewayParam implements InputConverter<AdpAIGatewayConfig> {

    @NotBlank(message = "ADP网关baseUrl不能为空")
    private String baseUrl;

    @NotNull(message = "ADP网关端口不能为空")
    private Integer port;

    private String authSeed;
    
    private String authType;
    
    private java.util.List<AuthHeader> authHeaders;
    
    @Data
    public static class AuthHeader {
        private String key;
        private String value;
    }
}
