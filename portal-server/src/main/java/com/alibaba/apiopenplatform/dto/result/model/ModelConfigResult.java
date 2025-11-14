package com.alibaba.apiopenplatform.dto.result.model;

import com.alibaba.apiopenplatform.dto.result.httpapi.HttpRouteResult;
import com.alibaba.apiopenplatform.dto.result.httpapi.ServiceResult;
import com.alibaba.apiopenplatform.service.gateway.HigressOperator.AiUpstream;
import com.alibaba.apiopenplatform.service.gateway.HigressOperator.HigressModelConfig;
import com.alibaba.apiopenplatform.service.gateway.HigressOperator.HigressRouteConfig;
import com.alibaba.apiopenplatform.service.gateway.HigressOperator.KeyedRoutePredicate;
import com.alibaba.apiopenplatform.service.gateway.HigressOperator.RoutePredicate;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * @author zh
 */
@Data
public class ModelConfigResult {

    private ModelAPIConfig modelAPIConfig;

	private HigressModelAPIConfig higressModelAPIConfig;

    @Data
    @Builder
    public static class ModelAPIConfig {
        /**
         * for AI gateway
         */
        private String modelCategory;
        private List<String> aiProtocols;
        private List<HttpRouteResult> routes;
        private List<ServiceResult> services;

		/**
		 * for higress
		 */
		private HigressRouteConfig higressModelConfig;
    }

	@Data
	@Builder
	public static class HigressModelAPIConfig {
	  private String name;
	  private List<String> domains;
	  private RoutePredicate pathPredicate;
	  private List<KeyedRoutePredicate> headerPredicates;
	  private List<KeyedRoutePredicate> urlParamPredicates;
	  private List<AiUpstream> upstreams;
    }
}