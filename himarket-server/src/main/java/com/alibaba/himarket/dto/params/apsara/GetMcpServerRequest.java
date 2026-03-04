package com.alibaba.himarket.dto.params.apsara;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class GetMcpServerRequest extends ApsaraGatewayBaseRequest {

    private String gwInstanceId;

    private String mcpServerName;
}
