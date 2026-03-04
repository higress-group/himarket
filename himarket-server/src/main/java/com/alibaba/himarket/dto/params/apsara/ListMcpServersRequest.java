package com.alibaba.himarket.dto.params.apsara;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * ListMcpServers 请求参数
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ListMcpServersRequest extends ApsaraGatewayBaseRequest {

    /**
     * 当前页码
     */
    private Integer current;

    /**
     * 每页数量
     */
    private Integer size;

    /**
     * 网关实例 ID
     */
    private String gwInstanceId;

    private String name;

    private String type;

    private String domainId;

    private String serviceId;
}
