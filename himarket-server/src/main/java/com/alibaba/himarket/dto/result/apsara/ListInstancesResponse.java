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

package com.alibaba.himarket.dto.result.apsara;

import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ListInstancesResponse
        extends ApsaraGatewayBaseResponse<ListInstancesResponse.ResponseBody> {

    @Data
    public static class ResponseBody {
        private Integer code;
        private ResponseData data;
        private String msg;
    }

    @Data
    public static class ResponseData {
        private Integer current;
        private List<Record> records;
        private Integer size;
        private Integer total;
    }

    @Data
    public static class Record {
        private List<AccessMode> accessMode;
        private String brokerEngineType;
        private String brokerEngineVersion;
        private String brokerLatestEngineVersion;
        private String createTime;
        private String department;
        private String deployClusterAttribute;
        private String deployClusterCode;
        private String deployClusterName;
        private String deployClusterNamespace;
        private String deployMode;
        private String edasAppId;
        private List<EdasAppInfo> edasAppInfos;
        private String edasNamespaceId;
        private String gwInstanceId;
        private String ingressClassName;
        private String instanceClass;
        private String instanceName;
        private String k8sClusterId;
        private String k8sNamespace;
        private String k8sServiceName;
        private String modifyTime;
        private String name;
        private Integer nodeNumber;
        private String regionId;
        private String resourceGroup;
        private Boolean sharedInstance;
        private String sharedInstanceSource;
        private Integer status;
        private String tid;
        private String vSwitchId;
        private String vpcId;
        private String zoneId;
        private Observability o11y;
    }

    @Data
    public static class Observability {
        private Boolean slsEnabled;
        private Boolean prometheusEnabled;
    }

    @Data
    public static class AccessMode {
        private String accessModeType;
        private List<String> clusterIp;
        private List<String> externalIps;
        private List<String> ips;
        private String loadBalancerAddressType;
        private String loadBalancerNetworkType;
        private List<String> ports;
        private String serviceName;
        private String edasAppId;
    }

    @Data
    public static class EdasAppInfo {
        private String appId;
        private String edasNamespaceId;
        private String k8sClusterId;
        private String k8sNamespace;
    }
}
