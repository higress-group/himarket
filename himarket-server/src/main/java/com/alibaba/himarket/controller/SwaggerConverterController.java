/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.alibaba.himarket.controller;

import com.alibaba.himarket.core.response.Response;
import com.alibaba.himarket.dto.params.api.ImportSwaggerParam;
import com.alibaba.himarket.dto.result.api.APIDefinitionVO;
import com.alibaba.himarket.entity.APIDefinition;
import com.alibaba.himarket.entity.APIEndpoint;
import com.alibaba.himarket.service.api.SwaggerConverter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Swagger/OpenAPI 转换工具 Controller
 * 提供 Swagger 文档转换为 API Definition 的功能
 */
@RestController
@RequestMapping("tools/swagger")
@Tag(name = "Swagger 转换工具", description = "Swagger/OpenAPI 文档转换接口")
@Slf4j
@RequiredArgsConstructor
public class SwaggerConverterController {

    private final SwaggerConverter swaggerConverter;

    /**
     * 转换 Swagger/OpenAPI 文档
     * 将 Swagger 2.0 或 OpenAPI 3.0 文档转换为 API Definition 和 Endpoints
     *
     * @param param 导入参数
     * @return 转换后的 API Definition 和 Endpoints 信息
     */
    @PostMapping("/convert")
    @Operation(summary = "转换 Swagger 文档", description = "将 Swagger/OpenAPI 文档转换为 API Definition")
    public Response<Map<String, Object>> convertSwagger(@Valid @RequestBody ImportSwaggerParam param) {
        log.info("Converting Swagger document, name: {}", param.getName());

        // 转换 API Definition
        APIDefinition apiDefinition = swaggerConverter.convert(
                param.getSwaggerContent(),
                param.getName(),
                param.getDescription(),
                param.getVersion()
        );

        // 转换 Endpoints
        List<APIEndpoint> endpoints = swaggerConverter.convertEndpoints(
                param.getSwaggerContent(),
                apiDefinition.getApiDefinitionId()
        );

        // 构建返回结果
        Map<String, Object> result = new HashMap<>();
        
        // 转换 APIDefinition 为 VO
        APIDefinitionVO apiDefinitionVO = new APIDefinitionVO().convertFrom(apiDefinition);
        result.put("apiDefinition", apiDefinitionVO);
        result.put("endpointsCount", endpoints.size());
        result.put("message", String.format("成功转换 %d 个 Endpoints", endpoints.size()));

        log.info("Swagger document converted successfully, endpoints count: {}", endpoints.size());

        return Response.ok(result);
    }
}
