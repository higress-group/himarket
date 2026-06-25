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

package com.alibaba.himarket.dto.result.httpapi;

import com.alibaba.himarket.dto.converter.OutputConverter;
import com.alibaba.himarket.dto.result.common.DomainResult;
import com.alibaba.himarket.service.gateway.HigressOperator;
import com.aliyun.sdk.service.apig20240327.models.HttpRoute;
import java.util.Collections;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
public class HttpRouteResult implements OutputConverter<HttpRouteResult, HttpRoute> {

    private List<DomainResult> domains;
    private String description;
    private RouteMatchResult match;
    private BackendResult backend;
    private Boolean builtin;

    /**
     * Converts an APIG HTTP route response into this result.
     *
     * @param route APIG HTTP route response
     * @param domains route domain results
     * @return converted HTTP route result
     */
    public HttpRouteResult convertFrom(HttpRoute route, List<DomainResult> domains) {
        var routeMatch = route.getMatch();

        // path
        RouteMatchPath matchPath = null;
        var path = routeMatch.getPath();
        if (path != null) {
            matchPath =
                    RouteMatchPath.builder().value(path.getValue()).type(path.getType()).build();
        }

        // headers
        List<RouteMatchHeader> matchHeaders = null;
        var headers = routeMatch.getHeaders();
        if (headers != null) {
            matchHeaders =
                    headers.stream()
                            .map(
                                    header ->
                                            RouteMatchHeader.builder()
                                                    .name(header.getName())
                                                    .type(header.getType())
                                                    .value(header.getValue())
                                                    .build())
                            .toList();
        }

        // queryParams
        List<RouteMatchQuery> matchQueries = null;
        var queryParams = routeMatch.getQueryParams();
        if (queryParams != null) {
            matchQueries =
                    queryParams.stream()
                            .map(
                                    param ->
                                            RouteMatchQuery.builder()
                                                    .name(param.getName())
                                                    .type(param.getType())
                                                    .value(param.getValue())
                                                    .build())
                            .toList();
        }

        // build routeMatch
        RouteMatchResult routeMatchResult =
                RouteMatchResult.builder()
                        .methods(routeMatch.getMethods())
                        .path(matchPath)
                        .headers(matchHeaders)
                        .queryParams(matchQueries)
                        .build();

        // backend
        BackendResult backendResult = new BackendResult().convertFrom(route.getBackend());

        setDomains(domains);
        setMatch(routeMatchResult);
        setBackend(backendResult);
        setDescription(route.getDescription());
        setBuiltin(route.getBuiltin() == null ? null : Boolean.valueOf(route.getBuiltin()));

        return this;
    }

    /**
     * Converts a Higress AI route response into this result.
     *
     * @param aiRoute Higress AI route response
     * @param domains route domain results
     * @return converted HTTP route result
     */
    public HttpRouteResult convertFrom(
            HigressOperator.HigressAIRoute aiRoute, List<DomainResult> domains) {
        // path
        HttpRouteResult.RouteMatchPath matchPath = null;
        var path = aiRoute.getPathPredicate();
        if (path != null) {
            matchPath =
                    HttpRouteResult.RouteMatchPath.builder()
                            .value(path.getMatchValue())
                            .type(path.getMatchType())
                            .caseSensitive(path.getCaseSensitive())
                            .build();
        }

        // methods
        List<String> methods = Collections.singletonList("POST");

        // headers
        List<HttpRouteResult.RouteMatchHeader> matchHeaders = null;
        var headers = aiRoute.getHeaderPredicates();
        if (headers != null) {
            matchHeaders =
                    headers.stream()
                            .map(
                                    header ->
                                            HttpRouteResult.RouteMatchHeader.builder()
                                                    .name(header.getKey())
                                                    .type(header.getMatchType())
                                                    .value(header.getMatchValue())
                                                    .caseSensitive(header.getCaseSensitive())
                                                    .build())
                            .toList();
        }

        // queryParams
        List<HttpRouteResult.RouteMatchQuery> matchQueries = null;
        var queryParams = aiRoute.getUrlParamPredicates();
        if (queryParams != null) {
            matchQueries =
                    queryParams.stream()
                            .map(
                                    param ->
                                            HttpRouteResult.RouteMatchQuery.builder()
                                                    .name(param.getKey())
                                                    .type(param.getMatchType())
                                                    .value(param.getMatchValue())
                                                    .caseSensitive(param.getCaseSensitive())
                                                    .build())
                            .toList();
        }

        // modelMatches
        List<HttpRouteResult.ModelMatch> modelMatches = null;
        var modelPredicates = aiRoute.getModelPredicates();
        if (modelPredicates != null) {
            modelMatches =
                    modelPredicates.stream()
                            .map(
                                    param ->
                                            HttpRouteResult.ModelMatch.builder()
                                                    .name("model")
                                                    .type(param.getMatchType())
                                                    .value(param.getMatchValue())
                                                    .caseSensitive(param.getCaseSensitive())
                                                    .build())
                            .toList();
        }

        // routeMatch
        HttpRouteResult.RouteMatchResult routeMatchResult =
                HttpRouteResult.RouteMatchResult.builder()
                        .methods(methods)
                        .path(matchPath)
                        .headers(matchHeaders)
                        .queryParams(matchQueries)
                        .modelMatches(modelMatches)
                        .build();

        setDomains(domains);
        setMatch(routeMatchResult);

        return this;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class RouteMatchResult {
        private List<String> methods;
        private RouteMatchPath path;

        private List<RouteMatchHeader> headers;
        private List<RouteMatchQuery> queryParams;

        private List<ModelMatch> modelMatches;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class RouteMatchPath {
        private String value;
        private String type;
        private Boolean caseSensitive;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class RouteMatchHeader {
        private String name;
        private String type;
        private String value;
        private Boolean caseSensitive;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class RouteMatchQuery {
        private String name;
        private String type;
        private String value;
        private Boolean caseSensitive;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ModelMatch {
        private String name;
        private String type;
        private String value;
        private Boolean caseSensitive;
    }
}
