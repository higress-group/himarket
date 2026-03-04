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
public class ListModelApisResponse
        extends ApsaraGatewayBaseResponse<ListModelApisResponse.ResponseBody> {

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
        private String id;
        private String apiName;
        private String description;
        private String basePath;
        private Boolean basePathRemove;
        private String protocol;
        private String sceneType;
        private List<String> domainNameList;
        private List<GetModelApiResponse.MethodPath> methodPathList;

        private GetModelApiResponse.RouteDispatcher routeDispatcher;

        private GetModelApiResponse.ObservationConfig observationConfig;

        private GetModelApiResponse.FallbackConfig fallback;

        private GetModelApiResponse.AuthenticationConfig authenticationConfig;
    }

    @Data
    public static class RouteDispatcher {
        private String strategyType;
        private List<GetModelApiResponse.WeightModelMappingRuleItem> rules;
    }

    @Data
    public static class WeightModelMappingRuleItem {
        private String serviceId;
        private String serviceName;
        private Integer weight;
        private Map<String, String> modelMapping;
    }

    @Data
    public static class ObservationConfig {
        private Boolean enable;
        private Boolean logRequestInfo;
        private Boolean logResponseInfo;
    }

    @Data
    public static class FallbackConfig {
        private Boolean enable;
        private List<String> responseCodes;
        private String fallbackServiceId;
        private String fallbackStrategy;
    }
}
