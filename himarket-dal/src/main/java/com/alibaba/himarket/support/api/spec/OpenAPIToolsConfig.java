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

package com.alibaba.himarket.support.api.spec;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@EqualsAndHashCode(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OpenAPIToolsConfig extends ToolsConfig {

    private Server server;

    @Builder.Default private List<OpenAPITool> tools = new ArrayList<>();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Server {
        private String name;

        @Builder.Default private Map<String, Object> config = new HashMap<>();

        @Builder.Default private List<String> allowTools = new ArrayList<>();

        private String type;
        private String transport;
        private String mcpServerURL;
        private Integer timeout;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OpenAPITool {
        private String name;
        private String description;

        @Builder.Default private List<Arg> args = new ArrayList<>();

        private RequestTemplate requestTemplate;
        private ResponseTemplate responseTemplate;
        private String errorResponseTemplate;
        private Map<String, Object> outputSchema;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Arg {
        private String name;
        private String description;
        private String type;
        private boolean required;

        @JsonProperty("default")
        private String defaultValue;

        @JsonProperty("enum")
        private List<String> enumValues;

        private String position;
        private Map<String, Object> items;
        private Map<String, Object> properties;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RequestTemplate {
        private String url;
        private String method;
        private Map<String, String> queryParams;

        @Builder.Default private List<Header> headers = new ArrayList<>();

        private String body;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ResponseTemplate {
        private String prependBody;
        private String appendBody;
        private String body;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Header {
        private String key;
        private String value;
    }
}
