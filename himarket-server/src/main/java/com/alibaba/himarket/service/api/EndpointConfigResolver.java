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

package com.alibaba.apiopenplatform.service.api;

import cn.hutool.json.JSONUtil;
import com.alibaba.apiopenplatform.core.exception.BusinessException;
import com.alibaba.apiopenplatform.core.exception.ErrorCode;
import com.alibaba.apiopenplatform.entity.APIEndpoint;
import com.alibaba.apiopenplatform.support.api.*;
import com.alibaba.apiopenplatform.support.enums.EndpointType;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Endpoint 配置解析工具类
 */
@Component
public class EndpointConfigResolver {

    private static final Map<EndpointType, Class<? extends EndpointConfig>> CONFIG_TYPE_MAP = Map.of(
            EndpointType.MCP_TOOL, MCPToolConfig.class,
            EndpointType.REST_ROUTE, RESTRouteConfig.class,
            EndpointType.AGENT, AgentConfig.class,
            EndpointType.MODEL, ModelConfig.class
    );

    /**
     * 解析 Endpoint 配置
     *
     * @param endpoint Endpoint 实体
     * @return 解析后的配置对象
     */
    public EndpointConfig parseConfig(APIEndpoint endpoint) {
        Class<? extends EndpointConfig> configClass = CONFIG_TYPE_MAP.get(endpoint.getType());
        if (configClass == null) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER,
                    "Unknown endpoint type: " + endpoint.getType());
        }
        return parseConfig(endpoint, configClass);
    }

    /**
     * 解析为指定类型的配置
     *
     * @param endpoint    Endpoint 实体
     * @param configClass 配置类类型
     * @param <T>         配置类型
     * @return 解析后的配置对象
     */
    public <T extends EndpointConfig> T parseConfig(APIEndpoint endpoint, Class<T> configClass) {
        try {
            return JSONUtil.toBean(endpoint.getConfig(), configClass);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                    "Failed to parse endpoint config: " + e.getMessage());
        }
    }

    /**
     * 序列化配置对象为 JSON 字符串
     *
     * @param config 配置对象
     * @return JSON 字符串
     */
    public String serializeConfig(EndpointConfig config) {
        try {
            return JSONUtil.toJsonStr(config);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                    "Failed to serialize endpoint config: " + e.getMessage());
        }
    }

    /**
     * 验证配置完整性
     *
     * @param endpoint Endpoint 实体
     */
    public void validateConfig(APIEndpoint endpoint) {
        EndpointConfig config = parseConfig(endpoint);
        switch (endpoint.getType()) {
            case MCP_TOOL:
                validateMCPToolConfig((MCPToolConfig) config);
                break;
            case REST_ROUTE:
                validateRESTRouteConfig((RESTRouteConfig) config);
                break;
            case AGENT:
                validateAgentConfig((AgentConfig) config);
                break;
            case MODEL:
                validateModelConfig((ModelConfig) config);
                break;
        }
    }

    private void validateMCPToolConfig(MCPToolConfig config) {
        if (config.getRequestTemplate() == null) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER,
                    "MCP Tool must have requestTemplate");
        }
        if (config.getRequestTemplate().getUrl() == null) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER,
                    "MCP Tool requestTemplate must have url");
        }
        if (config.getRequestTemplate().getMethod() == null) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER,
                    "MCP Tool requestTemplate must have method");
        }
    }

    private void validateRESTRouteConfig(RESTRouteConfig config) {
        if (config.getPath() == null || config.getPath().isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER,
                    "REST Route must have path");
        }
        if (config.getMethod() == null || config.getMethod().isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER,
                    "REST Route must have method");
        }
    }

    private void validateAgentConfig(AgentConfig config) {
        if (config.getProtocols() == null || config.getProtocols().isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER,
                    "Agent must have at least one protocol");
        }
    }

    private void validateModelConfig(ModelConfig config) {
        if (config.getModelName() == null || config.getModelName().isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER,
                    "Model must have modelName");
        }
        if (config.getMatchConfig() == null) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER,
                    "Model must have matchConfig");
        }
    }
}
