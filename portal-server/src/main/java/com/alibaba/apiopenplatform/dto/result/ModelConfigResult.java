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

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
public class ModelConfigResult {

    protected String modelApiName;

    protected List<String> aiProtocols;

    protected String basePath;

    protected List<Domain> domains;

    protected List<Service> services;

    protected List<Route> routes;


    @Data
    @Builder
    public static class Route {
        private String name;
        private List<String> methods;
        private List<Path> paths;
        private boolean ignoreUriCase;
    }


    @Data
    @Builder
    public static class Service {
        private String modelName;
        private String modelNamePattern;
        private String serviceName;
        private String protocol;
        private String address;
        private java.util.List<String> protocols;
    }

    @Data
    @Builder
    public static class Domain {
        private String domain;
        private String protocol;
    }

    @Data
    @Builder
    public static class Path {
        private String type;
        private String value;
    }

}
