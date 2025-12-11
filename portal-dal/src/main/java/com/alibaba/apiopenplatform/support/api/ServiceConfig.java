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

package com.alibaba.apiopenplatform.support.api;

import com.alibaba.apiopenplatform.support.enums.ProtocolType;
import com.alibaba.apiopenplatform.support.enums.ServiceType;
import lombok.Data;

import java.io.Serializable;

/**
 * 服务配置
 */
@Data
public class ServiceConfig implements Serializable {

    /**
     * 服务类型
     */
    private ServiceType serviceType;

    /**
     * 服务名称（Nacos 服务名）
     */
    private String serviceName;

    /**
     * 命名空间
     */
    private String namespace;

    /**
     * 服务地址（固定地址时使用）
     */
    private String address;

    /**
     * 端口
     */
    private Integer port;

    /**
     * 协议类型
     */
    private ProtocolType protocol;
}
