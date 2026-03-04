package com.alibaba.himarket.dto.params.apsara;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ListModelApiConsumersRequest extends ApsaraGatewayBaseRequest {

    private String gwInstanceId;

    private String modelApiId;

    private String consumerName;

    private String engineType;

    private Integer current;

    private Integer size;
}
