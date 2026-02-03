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

package com.alibaba.himarket.support.api.v2;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.io.Serializable;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HttpRoute implements Serializable {

    private String name;

    private List<Domain> domains;

    private String description;

    private RouteMatch match;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Domain implements Serializable {
        private String domain;

        private String protocol;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RouteMatch implements Serializable {
        private List<String> methods;

        private PathMatch path;

        private List<HeaderMatch> headers;

        private List<QueryMatch> queryParams;

        private List<ModelMatch> modelMatches;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PathMatch implements Serializable {
        private String value;

        private String type;

        private Boolean caseSensitive;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HeaderMatch implements Serializable {
        private String name;

        private String type;

        private String value;

        private Boolean caseSensitive;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QueryMatch implements Serializable {
        private String name;

        private String type;

        private String value;

        private Boolean caseSensitive;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ModelMatch implements Serializable {
        private String name;

        private String type;

        private String value;

        private Boolean caseSensitive;
    }
}
