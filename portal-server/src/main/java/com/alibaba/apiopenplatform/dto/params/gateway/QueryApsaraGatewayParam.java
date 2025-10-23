package com.alibaba.apiopenplatform.dto.params.gateway;

import com.alibaba.apiopenplatform.dto.converter.InputConverter;
import com.alibaba.apiopenplatform.support.gateway.ApsaraGatewayConfig;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import javax.validation.constraints.NotBlank;

@Data
public class QueryApsaraGatewayParam implements InputConverter<ApsaraGatewayConfig> {

    @NotBlank(message = "RegionId不能为空")
    @JsonProperty("regionId")
    private String regionId;

    @NotBlank(message = "AccessKeyId不能为空")
    @JsonProperty("accessKeyId")
    private String accessKeyId;

    @NotBlank(message = "AccessKeySecret不能为空")
    @JsonProperty("accessKeySecret")
    private String accessKeySecret;

    @JsonProperty("securityToken")
    private String securityToken;

    @NotBlank(message = "Domain不能为空")
    @JsonProperty("domain")
    private String domain;

    @NotBlank(message = "Product不能为空")
    @JsonProperty("product")
    private String product;

    @NotBlank(message = "Version不能为空")
    @JsonProperty("version")
    private String version;

    @NotBlank(message = "x-acs-organizationid不能为空")
    @JsonProperty("xAcsOrganizationId")
    private String xAcsOrganizationId;

    @JsonProperty("xAcsCallerSdkSource")
    private String xAcsCallerSdkSource;
    @JsonProperty("xAcsResourceGroupId")
    private String xAcsResourceGroupId;
    @JsonProperty("xAcsCallerType")
    private String xAcsCallerType;

    @JsonProperty("brokerEngineType")
    private String brokerEngineType;
}


