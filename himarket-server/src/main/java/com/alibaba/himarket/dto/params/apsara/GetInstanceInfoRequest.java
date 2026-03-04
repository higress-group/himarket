package com.alibaba.himarket.dto.params.apsara;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class GetInstanceInfoRequest extends ApsaraGatewayBaseRequest {

    private String gwInstanceId;
}
