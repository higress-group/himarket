package com.alibaba.himarket.dto.params.apsara;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * ListInstances 请求参数
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ListInstancesRequest extends ApsaraGatewayBaseRequest {

    /**
     * 当前页码
     */
    private Integer current;

    /**
     * 每页数量
     */
    private Integer size;

    /**
     * Broker 引擎类型
     */
    private String brokerEngineType;

    /**
     * 部署模式
     */
    private String deployMode;

    /**
     * 网关实例 ID
     */
    private String gwInstanceId;

    /**
     * 实例名称
     */
    private String name;

    /**
     * 状态
     */
    private Integer status;
}
