package com.alibaba.apiopenplatform.service;

import com.alibaba.apiopenplatform.dto.params.gateway.QueryAdpAIGatewayParam;
import com.alibaba.apiopenplatform.dto.result.GatewayResult;
import com.alibaba.apiopenplatform.dto.result.PageResult;
import org.springframework.data.domain.Pageable;

public interface AdpAIGatewayService {
    PageResult<GatewayResult> fetchGateways(QueryAdpAIGatewayParam param, int page, int size);
}