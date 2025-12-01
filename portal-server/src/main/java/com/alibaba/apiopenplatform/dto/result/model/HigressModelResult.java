package com.alibaba.apiopenplatform.dto.result.model;

import lombok.Builder;
import lombok.Data;

/**
 * @author zh
 */
@Data
@Builder
public class HigressModelResult extends GatewayModelAPIResult {

    private String modelRouteName;
}
