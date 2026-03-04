package com.alibaba.himarket.dto.result.apsara;

import java.util.List;
import java.util.Map;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class GetModelApiResponse
        extends ApsaraGatewayBaseResponse<GetModelApiResponse.ResponseBody> {

    @Data
    public static class ResponseBody {
        private Integer code;
        private ResponseData data;
        private String msg;
    }

    @Data
    public static class ResponseData {
        private String id;
        private String apiName;
        private String description;
        private String basePath;
        private Boolean basePathRemove;
        private String protocol;
        private String sceneType;
        private List<String> domainNameList;

        private List<MethodPath> methodPathList;

        private RouteDispatcher routeDispatcher;

        private ObservationConfig observationConfig;

        private FallbackConfig fallback;

        private AuthenticationConfig authenticationConfig;
    }

    @Data
    public static class MethodPath {
        private String path;
        private String method;
    }

    @Data
    public static class AuthenticationConfig {
        private Boolean enable;
        private List<String> credentialTypeList;
    }

    @Data
    public static class RouteDispatcher {
        private String strategyType;
        private List<WeightModelMappingRuleItem> rules;
    }

    @Data
    public static class WeightModelMappingRuleItem {
        private String serviceId;
        private String serviceName;
        private Integer weight;
        private Map<String, String> modelMapping;
    }

    @Data
    public static class ObservationConfig {
        private Boolean enable;
        private Boolean logRequestInfo;
        private Boolean logResponseInfo;
    }

    @Data
    public static class FallbackConfig {
        private Boolean enable;
        private List<String> responseCodes;
        private String fallbackServiceId;
        private String fallbackStrategy;
    }
}
