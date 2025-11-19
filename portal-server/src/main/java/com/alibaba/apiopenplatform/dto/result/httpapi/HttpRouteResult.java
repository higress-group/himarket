package com.alibaba.apiopenplatform.dto.result.httpapi;

import cn.hutool.core.util.BooleanUtil;
import com.alibaba.apiopenplatform.dto.converter.OutputConverter;
import com.alibaba.apiopenplatform.dto.result.common.DomainResult;
import lombok.Builder;
import lombok.Data;
import com.aliyun.sdk.service.apig20240327.models.HttpRoute;

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

    @Data
    @Builder
    public static class RouteMatchResult {
        private List<String> methods;
        private RouteMatchPath path;

        private List<RouteMatchHeader> headers;
        private List<RouteMatchQuery> queryParams;
    }

    @Data
    @Builder
    public static class RouteMatchPath {
        private String value;
        private String type;
    }

    @Data
    @Builder
    public static class RouteMatchHeader {
        private String name;
        private String type;
        private String value;
    }

    @Data
    @Builder
    public static class RouteMatchQuery {
        private String name;
        private String type;
        private String value;
    }
}
