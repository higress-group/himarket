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

package com.alibaba.apiopenplatform.dto.result;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
public class AdpMCPServerResult extends GatewayMCPServerResult {

    private String gwInstanceId;
    
    @JsonProperty("name")
    private String name;
    
    private String description;
    private List<String> domains;
    private List<Service> services;
    private ConsumerAuthInfo consumerAuthInfo;
    private String rawConfigurations;
    private String type;
    private String dsn;
    private String dbType;
    private String upstreamPathPrefix;

    /**
     * 确保 mcpServerName 字段被正确设置
     */
    public void setName(String name) {
        this.name = name;
        // 同时设置父类的 mcpServerName 字段
        this.setMcpServerName(name);
    }

    @Data
    public static class Service {
        private String name;
        private Integer port;
        private String version;
        private Integer weight;
    }

    @Data
    public static class ConsumerAuthInfo {
        private String type;
        private Boolean enable;
        private List<String> allowedConsumers;
    }
}


