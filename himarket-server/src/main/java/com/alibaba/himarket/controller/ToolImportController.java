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

import com.alibaba.himarket.core.exception.BusinessException;
import com.alibaba.himarket.core.exception.ErrorCode;
import com.alibaba.himarket.core.response.Response;
import com.alibaba.himarket.dto.params.api.ToolImportParam;
import com.alibaba.himarket.dto.result.api.APIEndpointVO;
import com.alibaba.himarket.dto.result.api.ImportToolsResult;
import com.alibaba.himarket.entity.APIEndpoint;
import com.alibaba.himarket.service.McpToolService;
import com.alibaba.himarket.service.NacosService;
import com.alibaba.himarket.service.api.SwaggerConverter;
import com.alibaba.himarket.support.enums.APIType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** MCP Tool 导入工具 Controller */
@RestController
@RequestMapping("tools/import")
@Tag(name = "MCP Tool 导入工具", description = "从不同来源导入 MCP Tool")
@Slf4j
@RequiredArgsConstructor
public class ToolImportController {

    private final NacosService nacosService;
    private final SwaggerConverter swaggerConverter;
    private final McpToolService mcpToolService;

    @PostMapping("/{source}")
    @Operation(summary = "统一导入接口", description = "根据 source (nacos, mcp-server, swagger) 导入 Tool")
    public Response<ImportToolsResult> importTools(
            @PathVariable("source") String source, @RequestBody ToolImportParam param) {
        validate(param, source);
        List<APIEndpoint> endpoints;

        switch (source) {
            case "nacos":
                log.info("Importing MCP Tools from Nacos: {}", param);
                endpoints =
                        nacosService.importMcpTools(
                                param.getNacosId(),
                                param.getNamespaceId(),
                                param.getMcpServerName());
                break;
            case "mcp-server":
                log.info("Importing MCP Tools from MCP Server: {}", param);
                String mcpType = StringUtils.hasText(param.getType()) ? param.getType() : "sse";
                endpoints =
                        mcpToolService.importFromMcpServer(
                                param.getEndpoint(), param.getToken(), mcpType);
                break;
            case "swagger":
                log.info("Converting Swagger document, name: {}", param.getName());
                APIType apiType =
                        StringUtils.hasText(param.getType())
                                ? APIType.valueOf(param.getType())
                                : null;
                endpoints = swaggerConverter.convertEndpoints(param.getSwaggerContent(), apiType);
                break;
            default:
                throw new BusinessException(
                        ErrorCode.INVALID_PARAMETER, "Unsupported import source: " + source);
        }

        List<APIEndpointVO> endpointVOs =
                endpoints.stream()
                        .map(endpoint -> new APIEndpointVO().convertFrom(endpoint))
                        .collect(Collectors.toList());

        return Response.ok(
                ImportToolsResult.builder()
                        .endpoints(endpointVOs)
                        .message("成功从 " + source + " 导入 " + endpointVOs.size() + " 个 Tool")
                        .build());
    }

    private void validate(ToolImportParam param, String source) {
        switch (source) {
            case "nacos":
                if (!StringUtils.hasText(param.getNacosId())) {
                    throw new BusinessException(ErrorCode.INVALID_PARAMETER, "Nacos实例ID不能为空");
                }
                if (!StringUtils.hasText(param.getNamespaceId())) {
                    throw new BusinessException(ErrorCode.INVALID_PARAMETER, "命名空间不能为空");
                }
                if (!StringUtils.hasText(param.getMcpServerName())) {
                    throw new BusinessException(ErrorCode.INVALID_PARAMETER, "MCP服务名称不能为空");
                }
                break;
            case "mcp-server":
                if (!StringUtils.hasText(param.getEndpoint())) {
                    throw new BusinessException(
                            ErrorCode.INVALID_PARAMETER, "MCP Server Endpoint不能为空");
                }
                break;
            case "swagger":
                if (!StringUtils.hasText(param.getSwaggerContent())) {
                    throw new BusinessException(ErrorCode.INVALID_PARAMETER, "Swagger内容不能为空");
                }
                break;
            default:
                break;
        }
    }
}
