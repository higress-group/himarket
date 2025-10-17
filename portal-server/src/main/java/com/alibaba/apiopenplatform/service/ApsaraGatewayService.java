package com.alibaba.apiopenplatform.service;

import com.alibaba.apiopenplatform.dto.params.gateway.QueryApsaraGatewayParam;
import com.alibaba.apiopenplatform.dto.result.GatewayResult;
import com.alibaba.apiopenplatform.dto.result.PageResult;

public interface ApsaraGatewayService {
    PageResult<GatewayResult> fetchGateways(QueryApsaraGatewayParam param, int page, int size);
}


