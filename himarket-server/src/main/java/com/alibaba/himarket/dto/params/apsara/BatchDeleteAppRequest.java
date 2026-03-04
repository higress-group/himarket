package com.alibaba.himarket.dto.params.apsara;

import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class BatchDeleteAppRequest extends ApsaraGatewayBaseRequest {

    private List<String> appIds;

    private String gwInstanceId;
}
