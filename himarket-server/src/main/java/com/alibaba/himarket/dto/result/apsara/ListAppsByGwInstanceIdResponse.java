package com.alibaba.himarket.dto.result.apsara;

import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ListAppsByGwInstanceIdResponse
        extends ApsaraGatewayBaseResponse<ListAppsByGwInstanceIdResponse.ResponseBody> {

    @Data
    public static class ResponseBody {
        private Integer code;
        private List<AppItem> data;
        private String msg;
    }

    @Data
    public static class AppItem {
        private String accessKey;
        private String appCode;
        private String appId;
        private String appName;
        private String appSecret;
        private List<AppTag> appTags;
        private Integer authType;
        private String authTypeName;
        private String description;
        private Boolean enable;
        private Long expireTime;
        private List<String> groups;
        private String ipWhiteList;
        private Boolean isDisable;
        private String key;
        private Oauth2Payload oauth2Payload;
        private String password;
        private Payload payload;
        private String requestHeader;
        private String secretKey;
        private List<StrategyConfig> strategyConfigs;
        private String token;
        private Boolean useAuth;
        private Boolean useWhiteList;

        /**
         * API Key位置类型 (HEADER/QUERY/BEARER)
         */
        private String apiKeyLocationType;

        /**
         * API Key在Header/Query中的名称
         */
        private String keyName;
    }

    @Data
    public static class AppTag {}

    @Data
    public static class Oauth2Payload {
        private Boolean authorizationCode;
        private Boolean clientCredentials;
        private String clientId;
        private String clientSecret;
        private Boolean implicitGrant;
        private Boolean passwordGrant;
        private Boolean pkce;
        private String redirectUris;
        private Long refreshTokenExpiration;
        private String scopes;
        private Long tokenExpiration;
    }

    @Data
    public static class Payload {
        private String issuer;
        private String subject;
    }

    @Data
    public static class StrategyConfig {
        private String createTime;
        private List<String> scopes;
        private Boolean status;
        private String strategyConfigId;
        private String strategyConfigName;
        private String strategyName;
        private String updateTime;
        private Object scope;
    }
}
