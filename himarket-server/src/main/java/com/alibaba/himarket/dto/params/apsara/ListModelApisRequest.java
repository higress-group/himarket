package com.alibaba.himarket.dto.params.apsara;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ListModelApisRequest extends ApsaraGatewayBaseRequest {

    /**
     * 当前页码
     */
    private Integer current;

    /**
     * 每页数量
     */
    private Integer size;

    private String gwInstanceId;
    // 支持模糊检索
    private String apiName;

    private String protocol;

    private String sceneType;
}
