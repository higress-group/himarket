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
 * KIND, either express or implied.  See the License for the specific
 * language governing permissions and limitations
 * under the License.
 */

package com.alibaba.apiopenplatform.dto.result;

import lombok.Data;

import java.util.List;

/**
 * ADP网关实例列表响应结果
 */
@Data
public class AdpGatewayInstanceResult {

    private Integer code;
    private String msg;
    private String message;
    private AdpGatewayInstanceData data;

    @Data
    public static class AdpGatewayInstanceData {
        private List<AdpGatewayInstance> records;
        private Integer total;
        private Integer size;
        private Integer current;
    }

    @Data
    public static class AdpGatewayInstance {
        private Integer status;
        private String gwInstanceId;
        private String name;
        private String deployClusterNamespace;
        private String deployClusterCode;
        private List<AccessMode> accessMode;
        private String deployClusterName;
        private String k8sServiceName;
        private String createTime;
        private String modifyTime;
        private String tid;
        private String vpcId;
        private String regionId;
        private String zoneId;
        private String deployMode;
        private String edasAppId;
        private String edasNamespaceId;
        private String k8sClusterId;
        private String k8sNamespace;
        private String instanceClass;
        private String edasAppInfos;
        private String department;
        private String resourceGroup;
        private String ingressClassName;
        private String brokerEngineType;
        private String brokerEngineVersion;
        private String deployClusterAttribute;
        private String vSwitchId;
    }

    @Data
    public static class AccessMode {
        private List<String> ips;
        private List<String> ports;
        private String accessModeType;
        private String loadBalancerNetworkType;
        private String loadBalancerAddressType;
        private String serviceName;
        private List<String> externalIps;
        private List<String> clusterIp;
    }
}
