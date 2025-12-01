package com.alibaba.apiopenplatform.dto.result.httpapi;

import cn.hutool.core.util.BooleanUtil;
import com.alibaba.apiopenplatform.dto.converter.OutputConverter;
import com.alibaba.apiopenplatform.dto.result.common.DomainResult;
import com.alibaba.apiopenplatform.service.gateway.HigressOperator;
import lombok.Builder;
import lombok.Data;
import com.aliyun.sdk.service.apig20240327.models.HttpRoute;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * @author zh
 */
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
        RouteMatchPath matchPath = Optional.ofNullable(route.getMatch().getPath())
                .map(path -> RouteMatchPath.builder()
                        .value(path.getValue())
                        .type(path.getType())
                        .build())
                .orElse(null);

        // headers
        List<RouteMatchHeader> matchHeaders = Optional.ofNullable(route.getMatch().getHeaders())
                .map(headers -> headers.stream()
                        .map(header -> RouteMatchHeader.builder()
                                .name(header.getName())
                                .type(header.getType())
                                .value(header.getValue())
                                .build())
                        .collect(Collectors.toList()))
                .orElse(null);

        // queryParams
        List<RouteMatchQuery> matchQueries = Optional.ofNullable(route.getMatch().getQueryParams())
                .map(params -> params.stream()
                        .map(param -> RouteMatchQuery.builder()
                                .name(param.getName())
                                .type(param.getType())
                                .value(param.getValue())
                                .build())
                        .collect(Collectors.toList()))
                .orElse(null);

        // build routeMatch
        RouteMatchResult routeMatchResult = RouteMatchResult.builder()
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
    public HttpRouteResult convertFrom(HigressOperator.HigressAIRoute aiRoute, List<DomainResult> domains) {
        // path
        HttpRouteResult.RouteMatchPath matchPath = Optional.ofNullable(aiRoute.getPathPredicate())
                .map(path -> HttpRouteResult.RouteMatchPath.builder()
                        .value(path.getMatchValue())
                        .type(path.getMatchType())
                        .caseSensitive(path.getCaseSensitive())
                        .build())
                .orElse(null);

        // methods
        List<String> methods = Collections.singletonList("POST");

        // headers
        List<HttpRouteResult.RouteMatchHeader> matchHeaders = Optional.ofNullable(aiRoute.getHeaderPredicates())
                .map(headers -> headers.stream()
                        .map(header -> HttpRouteResult.RouteMatchHeader.builder()
                                .name(header.getKey())
                                .type(header.getMatchType())
                                .value(header.getMatchValue())
                                .caseSensitive(header.getCaseSensitive())
                                .build())
                        .collect(Collectors.toList()))
                .orElse(null);

        // queryParams
        List<HttpRouteResult.RouteMatchQuery> matchQueries = Optional.ofNullable(aiRoute.getUrlParamPredicates())
                .map(params -> params.stream()
                        .map(param -> HttpRouteResult.RouteMatchQuery.builder()
                                .name(param.getKey())
                                .type(param.getMatchType())
                                .value(param.getMatchValue())
                                .caseSensitive(param.getCaseSensitive())
                                .build())
                        .collect(Collectors.toList()))
                .orElse(null);

        // modelMatches
        List<HttpRouteResult.ModelMatch> modelMatches = Optional.ofNullable(aiRoute.getModelPredicates())
                .map(params -> params.stream()
                        .map(param -> HttpRouteResult.ModelMatch.builder()
                                .name("model")
                                .type(param.getMatchType())
                                .value(param.getMatchValue())
                                .caseSensitive(param.getCaseSensitive())
                                .build())
                        .collect(Collectors.toList()))
                .orElse(null);

        // routeMatch
        HttpRouteResult.RouteMatchResult routeMatchResult = HttpRouteResult.RouteMatchResult.builder()
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
