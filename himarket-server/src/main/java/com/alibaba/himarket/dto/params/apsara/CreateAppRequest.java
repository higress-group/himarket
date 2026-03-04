package com.alibaba.himarket.dto.params.apsara;

import java.util.List;
import java.util.Map;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class CreateAppRequest extends ApsaraGatewayBaseRequest {

    private String accessKey;

    private String appCode;

    private String appId;

    private String appKey;

    private String appName;

    private String appSecret;

    private Integer authType;

    private String description;

    private Long expireTime;

    private List<String> groups;

    private String gwInstanceId;

    private String ipWhiteList;

    private String key;

    private Map<String, ?> oauth2Payload;

    private String password;

    private Map<String, ?> payload;

    private String requestHeader;

    private String secretKey;

    private String token;

    private Boolean useAuth;

    private Boolean useWhiteList;

    private String userId;

    private String userName;

    private Boolean isDisasterRecovery;

    /**
     * API Key位置类型 (HEADER/QUERY/BEARER)
     * higress 引擎使用
     */
    private String apiKeyLocationType;

    /**
     * API Key在Header/Query中的名称
     * 当 apiKeyLocationType 为 HEADER 或 QUERY 时需要提供
     * BEARER 类型不需要此字段（固定使用 Authorization）
     */
    private String keyName;
}
