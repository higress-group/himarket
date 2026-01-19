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

package com.alibaba.himarket.support.api.service;

import com.alibaba.himarket.support.enums.ServiceType;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.io.Serializable;
import lombok.Data;

/** 服务配置 */
@Data
@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.EXISTING_PROPERTY,
        property = "serviceType",
        visible = true)
@JsonSubTypes({
    @JsonSubTypes.Type(value = NacosServiceConfig.class, name = "NACOS"),
    @JsonSubTypes.Type(value = FixedAddressServiceConfig.class, name = "FIXED_ADDRESS"),
    @JsonSubTypes.Type(value = DnsServiceConfig.class, name = "DNS"),
    @JsonSubTypes.Type(value = AiServiceConfig.class, name = "AI_SERVICE"),
    @JsonSubTypes.Type(value = GatewayServiceConfig.class, name = "GATEWAY")
})
public abstract class ServiceConfig implements Serializable {

    /** 服务类型 */
    private ServiceType serviceType;

    /** 是否开启 TLS */
    private boolean tlsEnabled;

    /** 元数据 */
    private java.util.Map<String, String> meta;

    /**
     * 是否为网关原生服务
     * 网关原生服务（如 GatewayServiceConfig）可能已经在网关中注册，可以直接使用现有的 serviceId。
     * 非网关原生服务（如 AI、DNS、FixedAddress）需要先确保服务存在。
     *
     * @return true 如果是网关原生服务，false 否则
     */
    public abstract boolean isNativeService();
}
