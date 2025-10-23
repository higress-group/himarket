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

import com.aliyun.apsarastack.csb220230206.models.ListMcpServersResponseBody;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;
import java.util.stream.Collectors;

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
    
    /**
     * 从SDK的ListMcpServersResponseBodyDataRecords创建AdpMCPServerResult
     */
    public static AdpMCPServerResult fromSdkRecord(ListMcpServersResponseBody.ListMcpServersResponseBodyDataRecords record) {
        if (record == null) {
            return null;
        }
        
        AdpMCPServerResult result = new AdpMCPServerResult();
        // 设置基础字段
        result.setGwInstanceId(record.getGwInstanceId());
        result.setName(record.getName()); // 该方法会同时设置name和mcpServerName
        result.setDescription(record.getDescription());
        result.setType(record.getType());
        result.setDbType(record.getDbType());
        result.setRawConfigurations(record.getRawConfigurations());
        result.setDomains(record.getDomains());
        
        // 映射services列表
        if (record.getServices() != null) {
            List<Service> services = record.getServices().stream()
                .map(svc -> {
                    Service service = new Service();
                    service.setName(svc.getName());
                    service.setPort(svc.getPort());
                    service.setVersion(svc.getVersion());
                    service.setWeight(svc.getWeight());
                    return service;
                })
                .collect(Collectors.toList());
            result.setServices(services);
        }
        
        // 映射consumerAuthInfo
        if (record.getConsumerAuthInfo() != null) {
            ConsumerAuthInfo authInfo = new ConsumerAuthInfo();
            authInfo.setType(record.getConsumerAuthInfo().getType());
            authInfo.setEnable(record.getConsumerAuthInfo().getEnable());
            authInfo.setAllowedConsumers(record.getConsumerAuthInfo().getAllowedConsumers());
            result.setConsumerAuthInfo(authInfo);
        }
        
        return result;
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


