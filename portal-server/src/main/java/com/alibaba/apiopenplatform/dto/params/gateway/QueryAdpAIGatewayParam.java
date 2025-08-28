package com.alibaba.apiopenplatform.dto.params.gateway;

import com.alibaba.apiopenplatform.dto.converter.InputConverter;
import com.alibaba.apiopenplatform.support.gateway.AdpAIGatewayConfig;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

/**
 * 查询 ADP 网关实例列表所需的连接参数
 */
@Data
public class QueryAdpAIGatewayParam implements InputConverter<AdpAIGatewayConfig> {

    @NotBlank(message = "ADP网关baseUrl不能为空")
    private String baseUrl;

    @NotNull(message = "ADP网关端口不能为空")
    private Integer port;

    @NotBlank(message = "ADP网关认证种子不能为空")
    private String authSeed;
}
