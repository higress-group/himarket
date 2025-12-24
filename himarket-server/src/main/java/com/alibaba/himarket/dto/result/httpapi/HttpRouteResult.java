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

import cn.hutool.core.util.BooleanUtil;
import com.alibaba.himarket.dto.converter.OutputConverter;
import com.alibaba.himarket.dto.result.common.DomainResult;
import com.alibaba.himarket.service.gateway.HigressOperator;
import com.alibaba.himarket.service.gateway.SofaHigressOperator;
import com.aliyun.sdk.service.apig20240327.models.HttpRoute;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.Builder;
import lombok.Data;

@Data
public class HttpRouteResult implements OutputConverter<HttpRouteResult, HttpRoute> {

    private List<DomainResult> domains;
    private String description;
    private RouteMatchResult match;
    private BackendResult backend;
    private Boolean builtin;

    /**
     * Convert from AIGW HttpRoute
     *
     * @param route
     * @param domains
     * @return
     */
    public HttpRouteResult convertFrom(HttpRoute route, List<DomainResult> domains) {
        // path
        RouteMatchPath matchPath =
                Optional.ofNullable(route.getMatch().getPath())
                        .map(
                                path ->
                                        RouteMatchPath.builder()
                                                .value(path.getValue())
                                                .type(path.getType())
                                                .build())
                        .orElse(null);

        // headers
        List<RouteMatchHeader> matchHeaders =
                Optional.ofNullable(route.getMatch().getHeaders())
                        .map(
                                headers ->
                                        headers.stream()
                                                .map(
                                                        header ->
                                                                RouteMatchHeader.builder()
                                                                        .name(header.getName())
                                                                        .type(header.getType())
                                                                        .value(header.getValue())
                                                                        .build())
                                                .collect(Collectors.toList()))
                        .orElse(null);

        // queryParams
        List<RouteMatchQuery> matchQueries =
                Optional.ofNullable(route.getMatch().getQueryParams())
                        .map(
                                params ->
                                        params.stream()
                                                .map(
                                                        param ->
                                                                RouteMatchQuery.builder()
                                                                        .name(param.getName())
                                                                        .type(param.getType())
                                                                        .value(param.getValue())
                                                                        .build())
                                                .collect(Collectors.toList()))
                        .orElse(null);

        // build routeMatch
        RouteMatchResult routeMatchResult =
                RouteMatchResult.builder()
                        .methods(route.getMatch().getMethods())
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
        setBuiltin(BooleanUtil.toBooleanObject(route.getBuiltin()));

        return this;
    }

    /**
     * Convert from Higress AIRoute
     *
     * @param aiRoute
     * @return
     */
    public HttpRouteResult convertFrom(
            HigressOperator.HigressAIRoute aiRoute, List<DomainResult> domains) {
        // path
        HttpRouteResult.RouteMatchPath matchPath =
                Optional.ofNullable(aiRoute.getPathPredicate())
                        .map(
                                path ->
                                        HttpRouteResult.RouteMatchPath.builder()
                                                .value(path.getMatchValue())
                                                .type(path.getMatchType())
                                                .caseSensitive(path.getCaseSensitive())
                                                .build())
                        .orElse(null);

        // methods
        List<String> methods = Collections.singletonList("POST");

        // headers
        List<HttpRouteResult.RouteMatchHeader> matchHeaders =
                Optional.ofNullable(aiRoute.getHeaderPredicates())
                        .map(
                                headers ->
                                        headers.stream()
                                                .map(
                                                        header ->
                                                                HttpRouteResult.RouteMatchHeader
                                                                        .builder()
                                                                        .name(header.getKey())
                                                                        .type(header.getMatchType())
                                                                        .value(
                                                                                header
                                                                                        .getMatchValue())
                                                                        .caseSensitive(
                                                                                header
                                                                                        .getCaseSensitive())
                                                                        .build())
                                                .collect(Collectors.toList()))
                        .orElse(null);

        // queryParams
        List<HttpRouteResult.RouteMatchQuery> matchQueries =
                Optional.ofNullable(aiRoute.getUrlParamPredicates())
                        .map(
                                params ->
                                        params.stream()
                                                .map(
                                                        param ->
                                                                HttpRouteResult.RouteMatchQuery
                                                                        .builder()
                                                                        .name(param.getKey())
                                                                        .type(param.getMatchType())
                                                                        .value(
                                                                                param
                                                                                        .getMatchValue())
                                                                        .caseSensitive(
                                                                                param
                                                                                        .getCaseSensitive())
                                                                        .build())
                                                .collect(Collectors.toList()))
                        .orElse(null);

        // modelMatches
        List<HttpRouteResult.ModelMatch> modelMatches =
                Optional.ofNullable(aiRoute.getModelPredicates())
                        .map(
                                params ->
                                        params.stream()
                                                .map(
                                                        param ->
                                                                HttpRouteResult.ModelMatch.builder()
                                                                        .name("model")
                                                                        .type(param.getMatchType())
                                                                        .value(
                                                                                param
                                                                                        .getMatchValue())
                                                                        .caseSensitive(
                                                                                param
                                                                                        .getCaseSensitive())
                                                                        .build())
                                                .collect(Collectors.toList()))
                        .orElse(null);

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

public HttpRouteResult convertFrom(SofaHigressOperator.SofaHigressRouteConfig routeInfo, List<DomainResult> domainResults) {
    // path
    HttpRouteResult.RouteMatchPath matchPath =
            Optional.ofNullable(routeInfo.getRouteMatchConfig().getPath())
                    .map(path ->
                            HttpRouteResult.RouteMatchPath.builder()
                                    .value(path.getMatchValue())
                                    .type(path.getMatchType())
                                    .caseSensitive(path.getCaseSensitive())
                                    .build())
                    .orElse(null);

    // methods
    List<String> methods = Collections.singletonList("POST");

    // headers
    List<HttpRouteResult.RouteMatchHeader> matchHeaders =
            Optional.ofNullable(routeInfo.getRouteMatchConfig().getHeaders())
                    .map(
                            headers ->
                                    headers.stream()
                                            .map(
                                                    header ->
                                                            HttpRouteResult.RouteMatchHeader
                                                                    .builder()
                                                                    .name(header.getKey())
                                                                    .type(header.getMatchType())
                                                                    .value(
                                                                            header
                                                                                    .getMatchValue())
                                                                    .caseSensitive(
                                                                            header
                                                                                    .getCaseSensitive())
                                                                    .build())
                                            .collect(Collectors.toList()))
                    .orElse(null);

    // queryParams
    List<HttpRouteResult.RouteMatchQuery> matchQueries =
            Optional.ofNullable(routeInfo.getRouteMatchConfig().getUrlParams())
                    .map(
                            params ->
                                    params.stream()
                                            .map(
                                                    param ->
                                                            HttpRouteResult.RouteMatchQuery
                                                                    .builder()
                                                                    .name(param.getKey())
                                                                    .type(param.getMatchType())
                                                                    .value(
                                                                            param
                                                                                    .getMatchValue())
                                                                    .caseSensitive(
                                                                            param
                                                                                    .getCaseSensitive())
                                                                    .build())
                                            .collect(Collectors.toList()))
                    .orElse(null);

    // routeMatch
    HttpRouteResult.RouteMatchResult routeMatchResult =
            HttpRouteResult.RouteMatchResult.builder()
                    .methods(methods)
                    .path(matchPath)
                    .headers(matchHeaders)
                    .queryParams(matchQueries)
                    .build();

    setDomains(domains);
    setMatch(routeMatchResult);

    return this;
}

    @Data
    @Builder
    public static class RouteMatchResult {
        private List<String> methods;
        private RouteMatchPath path;

        private List<RouteMatchHeader> headers;
        private List<RouteMatchQuery> queryParams;

        private List<ModelMatch> modelMatches;
    }

    @Data
    @Builder
    public static class RouteMatchPath {
        private String value;
        private String type;
        private Boolean caseSensitive;
    }

    @Data
    @Builder
    public static class RouteMatchHeader {
        private String name;
        private String type;
        private String value;
        private Boolean caseSensitive;
    }

    @Data
    @Builder
    public static class RouteMatchQuery {
        private String name;
        private String type;
        private String value;
        private Boolean caseSensitive;
    }

    @Data
    @Builder
    public static class ModelMatch {
        private String name;
        private String type;
        private String value;
        private Boolean caseSensitive;
    }
}
