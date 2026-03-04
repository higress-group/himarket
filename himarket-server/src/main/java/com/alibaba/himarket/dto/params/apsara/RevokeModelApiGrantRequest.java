package com.alibaba.himarket.dto.params.apsara;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class RevokeModelApiGrantRequest extends ApsaraGatewayBaseRequest {

    private String gwInstanceId;

    private String authId;
}
