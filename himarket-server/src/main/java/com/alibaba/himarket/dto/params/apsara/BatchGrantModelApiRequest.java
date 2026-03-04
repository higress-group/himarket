package com.alibaba.himarket.dto.params.apsara;

import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class BatchGrantModelApiRequest extends ApsaraGatewayBaseRequest {

    private String gwInstanceId;

    private String modelApiId;

    private List<String> consumerIds;
}
