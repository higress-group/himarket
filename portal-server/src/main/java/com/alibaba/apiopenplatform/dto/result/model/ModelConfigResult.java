package com.alibaba.apiopenplatform.dto.result.model;

import com.alibaba.apiopenplatform.dto.result.higress.HigressRouteResult;
import com.alibaba.apiopenplatform.dto.result.httpapi.HttpRouteResult;
import com.alibaba.apiopenplatform.dto.result.httpapi.ServiceResult;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * @author zh
 */
@Data
public class ModelConfigResult {

    /**
     * for AI gateway
     */
    private AIGWModelAPIConfig aigwModelAPIConfig;

    /**
     * for Higress
     */
	private HigressModelConfig higressModelConfig;

    @Data
    @Builder
    public static class AIGWModelAPIConfig {
        private String modelCategory;
        private List<String> aiProtocols;
        private List<HttpRouteResult> routes;
        private List<ServiceResult> services;
    }

	@Data
	@Builder
	public static class HigressModelConfig {
      private HigressRouteResult aiRoute;
    }
}