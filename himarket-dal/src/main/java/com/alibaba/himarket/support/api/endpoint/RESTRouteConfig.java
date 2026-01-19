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

package com.alibaba.himarket.support.api.endpoint;

import java.util.List;
import java.util.Map;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** REST Route 配置 */
@Data
@EqualsAndHashCode(callSuper = true)
public class RESTRouteConfig extends EndpointConfig {

    /** 请求路径（支持路径参数） */
    private String path;

    /** HTTP 方法 */
    private String method;

    /** 请求参数列表 */
    private List<Parameter> parameters;

    /** 请求头列表 */
    private List<Parameter> headers;

    /** 路径参数列表 */
    private List<Parameter> pathParams;

    /** 请求体 definition */
    private Map<String, Object> requestBody;

    /** 响应定义（按状态码） */
    private Map<String, ResponseDef> responses;

    @Data
    public static class Parameter {
        /** 参数名 */
        private String name;

        /** 参数位置：query, path, header, cookie */
        private String in;

        /** 是否必填 */
        private Boolean required;

        /** 参数 JSON Schema */
        private Map<String, Object> schema;

        /** 参数描述 */
        private String description;
    }

    @Data
    public static class ResponseDef {
        /** 响应描述 */
        private String description;

        /** 响应 Schema */
        private Map<String, Object> schema;
    }
}
