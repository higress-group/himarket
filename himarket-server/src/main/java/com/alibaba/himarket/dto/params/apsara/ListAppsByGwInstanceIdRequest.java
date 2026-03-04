package com.alibaba.himarket.dto.params.apsara;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ListAppsByGwInstanceIdRequest extends ApsaraGatewayBaseRequest {

    private String gwInstanceId;

    private Integer serviceType;

    private Boolean isDisasterRecovery;
}
