package com.alibaba.apiopenplatform.dto.result.model;

import lombok.Builder;
import lombok.Data;

/**
 * @author zh
 */
@Data
@Builder
public class AIGWModelAPIResult extends GatewayModelAPIResult {

    private String modelApiId;

    private String modelApiName;
}
