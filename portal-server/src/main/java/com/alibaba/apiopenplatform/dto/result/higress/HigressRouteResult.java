//package com.alibaba.apiopenplatform.dto.result.higress;
//
//import cn.hutool.core.collection.CollUtil;
//import com.alibaba.apiopenplatform.dto.converter.OutputConverter;
//import com.alibaba.apiopenplatform.dto.result.common.DomainResult;
//import com.alibaba.apiopenplatform.service.gateway.HigressOperator;
//import com.aliyun.sdk.service.apig20240327.models.HttpRoute;
//import lombok.Builder;
//import lombok.Data;
//
//import java.util.*;
//import java.util.stream.Collectors;
//
///**
// * @author zh
// */
//@Data
//public class HigressRouteResult implements OutputConverter<HigressRouteResult, HttpRoute> {
//
//    private List<DomainResult> domains;
//    private RouteMatchResult match;
//
//    public HigressRouteResult convertFrom(HigressOperator.HigressRouteConfig higressRoute) {
//        // domains
//        List<DomainResult> domains = higressRoute.getDomains().stream()
//                .map(domain -> DomainResult.builder()
//                        .domain(domain)
//                        .protocol("http")
//                        .build())
//                .collect(Collectors.toList());
//
//        // path
//        RouteMatchPath matchPath = Optional.ofNullable(higressRoute.getPathPredicate())
//                .map(path -> RouteMatchPath.builder()
//                        .value(path.getMatchValue())
//                        .type(path.getMatchType())
//                        .caseSensitive(path.getCaseSensitive())
//                        .build())
//                .orElse(null);
//
//        // methods
//        List<String> methods = CollUtil.isEmpty(higressRoute.getMethods()) ? Collections.singletonList("POST") : higressRoute.getMethods();
//
//
//        // headers
//        List<RouteMatchHeader> matchHeaders = Optional.ofNullable(higressRoute.getHeaderPredicates())
//                .map(headers -> headers.stream()
//                        .map(header -> RouteMatchHeader.builder()
//                                .name(header.getKey())
//                                .type(header.getMatchType())
//                                .value(header.getMatchValue())
//                                .caseSensitive(header.getCaseSensitive())
//                                .build())
//                        .collect(Collectors.toList()))
//                .orElse(null);
//
//        // queryParams
//        List<RouteMatchQuery> matchQueries = Optional.ofNullable(higressRoute.getUrlParamPredicates())
//                .map(params -> params.stream()
//                        .map(param -> RouteMatchQuery.builder()
//                                .name(param.getKey())
//                                .type(param.getMatchType())
//                                .value(param.getMatchValue())
//                                .caseSensitive(param.getCaseSensitive())
//                                .build())
//                        .collect(Collectors.toList()))
//                .orElse(null);
//
//        // modelMatches
//        List<ModelMatch> modelMatches = Optional.ofNullable(higressRoute.getModelPredicates())
//                .map(params -> params.stream()
//                        .map(param -> ModelMatch.builder()
//                                .name("model")
//                                .type(param.getMatchType())
//                                .value(param.getMatchValue())
//                                .caseSensitive(param.getCaseSensitive())
//                                .build())
//                        .collect(Collectors.toList()))
//                .orElse(null);
//
//        // routeMatch
//        RouteMatchResult routeMatchResult = RouteMatchResult.builder()
//                .methods(methods)
//                .path(matchPath)
//                .headers(matchHeaders)
//                .queryParams(matchQueries)
//                .modelMatches(modelMatches)
//                .build();
//
//        setDomains(domains);
//        setMatch(routeMatchResult);
//
//        return this;
//    }
//
//    @Data
//    @Builder
//    public static class RouteMatchResult {
//        private List<String> methods;
//        private RouteMatchPath path;
//
//        private List<RouteMatchHeader> headers;
//        private List<RouteMatchQuery> queryParams;
//
//        private List<ModelMatch> modelMatches;
//    }
//
//    @Data
//    @Builder
//    public static class RouteMatchPath {
//        private String value;
//        private String type;
//        private Boolean caseSensitive;
//    }
//
//    @Data
//    @Builder
//    public static class RouteMatchHeader {
//        private String name;
//        private String type;
//        private String value;
//        private Boolean caseSensitive;
//    }
//
//    @Data
//    @Builder
//    public static class RouteMatchQuery {
//        private String name;
//        private String type;
//        private String value;
//        private Boolean caseSensitive;
//    }
//
//    @Data
//    @Builder
//    public static class ModelMatch {
//        private String name;
//        private String type;
//        private String value;
//        private Boolean caseSensitive;
//    }
//}
