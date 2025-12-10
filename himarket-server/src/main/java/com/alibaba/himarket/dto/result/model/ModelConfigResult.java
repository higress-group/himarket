package com.alibaba.himarket.dto.result.model;

import com.alibaba.himarket.dto.result.httpapi.HttpRouteResult;
import com.alibaba.himarket.dto.result.httpapi.ServiceResult;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * @author zh
 */
@Data
public class ModelConfigResult {

    private ModelAPIConfig modelAPIConfig;

    @Data
    @Builder
    public static class ModelAPIConfig {
        private String modelCategory;
        private List<String> aiProtocols;
        private List<HttpRouteResult> routes;
        private List<ServiceResult> services;
    }
}