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

package com.alibaba.himarket.support.api;

import java.util.List;
import java.util.Map;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** MCP Tool 配置 */
@Data
@EqualsAndHashCode(callSuper = true)
public class MCPToolConfig extends EndpointConfig {

    /** 输入参数 JSON Schema */
    private Map<String, Object> inputSchema;

    /** 输出结果 JSON Schema */
    private Map<String, Object> outputSchema;

    /** HTTP 请求模板 */
    private RequestTemplate requestTemplate;

    /** 响应处理模板 */
    private ResponseTemplate responseTemplate;

    @Data
    public static class RequestTemplate {
        /** 请求 URL（支持变量替换） */
        private String url;

        /** HTTP 方法 */
        private String method;

        /** 请求头 */
        private List<Header> headers;

        /** 请求体模板 */
        private String body;

        /** 查询参数 */
        private Map<String, String> queryParams;
    }

    @Data
    public static class ResponseTemplate {
        /** 响应前缀 */
        private String prependBody;

        /** 响应后缀 */
        private String appendBody;

        /** 响应体模板 */
        private String body;
    }

    @Data
    public static class Header {
        /** 请求头名称 */
        private String key;

        /** 请求头值 */
        private String value;
    }
}
