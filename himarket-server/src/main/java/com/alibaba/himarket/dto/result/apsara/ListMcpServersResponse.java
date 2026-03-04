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
import java.util.Map;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ListMcpServersResponse
        extends ApsaraGatewayBaseResponse<ListMcpServersResponse.ResponseBody> {

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
        private ConsumerAuthInfo consumerAuthInfo;
        private DbConfig dbConfig;
        private String dbType;
        private String description;
        private DirectRouteConfig directRouteConfig;
        private List<String> domains;
        private String dsn;
        private String gwInstanceId;
        private String id;
        private String name;
        private String rawConfigurations;
        private List<Service> services;
        private String type;
        private String upstreamPathPrefix;
    }

    @Data
    public static class Service {
        private String serviceId;
        private String name;
    }

    @Data
    public static class ConsumerAuthInfo {
        private List<String> allowedConsumers;
        private Boolean enable;
        private String type;
    }

    @Data
    public static class DbConfig {
        private String dbname;
        private String host;
        private Map<String, ?> otherParams;
        private String password;
        private String port;
        private String username;
    }

    @Data
    public static class DirectRouteConfig {
        private String path;
        private String transportType;
    }
}
