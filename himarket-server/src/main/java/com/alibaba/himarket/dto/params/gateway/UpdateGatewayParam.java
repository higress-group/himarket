package com.alibaba.himarket.dto.params.gateway;

import com.alibaba.himarket.dto.converter.InputConverter;
import com.alibaba.himarket.entity.Gateway;
import com.alibaba.himarket.support.enums.GatewayType;
import com.alibaba.himarket.support.gateway.APIGConfig;
import com.alibaba.himarket.support.gateway.AdpAIGatewayConfig;
import com.alibaba.himarket.support.gateway.ApsaraGatewayConfig;
import com.alibaba.himarket.support.gateway.HigressConfig;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * @author zh
 */
@Data
public class UpdateGatewayParam implements InputConverter<Gateway> {

    @NotNull(message = "Gateway type cannot be null")
    private GatewayType gatewayType;

    private String gatewayName;

    private String description;

    private APIGConfig apigConfig;

    private AdpAIGatewayConfig adpAIGatewayConfig;

    private ApsaraGatewayConfig apsaraGatewayConfig;

    private HigressConfig higressConfig;

    @AssertTrue(message = "Invalid gateway config")
    private boolean isGatewayConfigValid() {
        return gatewayType.isAPIG() && (apigConfig == null || apigConfig.validate())
                || gatewayType.isAdpAIGateway() && (adpAIGatewayConfig == null || adpAIGatewayConfig.validate())
                || gatewayType.isApsaraGateway() && (apsaraGatewayConfig == null || apsaraGatewayConfig.validate())
                || gatewayType.isHigress() && (higressConfig == null || higressConfig.validate());
    }
}