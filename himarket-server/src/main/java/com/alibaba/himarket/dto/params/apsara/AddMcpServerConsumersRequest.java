package com.alibaba.himarket.dto.params.apsara;

import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class AddMcpServerConsumersRequest extends ApsaraGatewayBaseRequest {

    private List<String> consumers;

    private String gwInstanceId;

    private String mcpServerName;
}
