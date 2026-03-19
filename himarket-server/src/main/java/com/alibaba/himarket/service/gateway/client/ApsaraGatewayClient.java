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

package com.alibaba.himarket.service.gateway.client;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.alibaba.himarket.core.exception.BusinessException;
import com.alibaba.himarket.core.exception.ErrorCode;
import com.alibaba.himarket.dto.params.apsara.*;
import com.alibaba.himarket.dto.result.apsara.*;
import com.alibaba.himarket.support.gateway.ApsaraGatewayConfig;
import com.aliyun.teaopenapi.Client;
import com.aliyun.teaopenapi.models.Config;
import com.aliyun.teaopenapi.models.OpenApiRequest;
import com.aliyun.teaopenapi.models.Params;
import com.aliyun.teautil.models.RuntimeOptions;
import com.aliyuncs.http.MethodType;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ApsaraGatewayClient extends GatewayClient {

    private final ApsaraGatewayConfig config;
    private final Client client;
    private final RuntimeOptions runtime;

    public ApsaraGatewayClient(ApsaraGatewayConfig config) {
        this.config = config;
        this.client = createClient(config);
        this.runtime = new RuntimeOptions();
        this.runtime.ignoreSSL = true;
        this.runtime.setConnectTimeout(3000);
        this.runtime.setReadTimeout(30000);
    }

    private Client createClient(ApsaraGatewayConfig config) {
        try {
            Config clientConfig =
                    new Config()
                            .setRegionId(config.getRegionId())
                            .setAccessKeyId(config.getAccessKeyId())
                            .setAccessKeySecret(config.getAccessKeySecret())
                            .setEndpoint(config.getDomain());
            if (config.getSecurityToken() != null && !config.getSecurityToken().isEmpty()) {
                clientConfig.setSecurityToken(config.getSecurityToken());
            }
            return new Client(clientConfig);
        } catch (Exception e) {
            log.error("Error creating client", e);
            throw new BusinessException(ErrorCode.GATEWAY_ERROR, "Error creating client");
        }
    }

    @Override
    public void close() {
        // Client doesn't need explicit closing
    }

    // ==================== 网关实例管理 ====================

    /**
     * 获取网关实例列表
     */
    public ListInstancesResponse listInstances(int current, int size, String brokerEngineType) {
        try {
            ListInstancesRequest request = new ListInstancesRequest();
            request.setCurrent(current);
            request.setSize(size);
            request.setBrokerEngineType(brokerEngineType);
            return execute(
                    "ListInstances",
                    "/gatewayInstance/listInstances",
                    MethodType.POST,
                    request.toJsonObject(),
                    data -> {
                        return BeanUtil.toBean(data, ListInstancesResponse.class);
                    });
        } catch (Exception e) {
            log.error("Error listing instances", e);
            throw new RuntimeException(e);
        }
    }

    /**
     * 获取网关实例详情
     */
    public GetInstanceInfoResponse getInstance(String gwInstanceId) {
        try {
            GetInstanceInfoRequest request = new GetInstanceInfoRequest();
            request.setGwInstanceId(gwInstanceId);

            return execute(
                    "GetInstanceInfo",
                    "/gatewayInstance/getInstanceInfo",
                    MethodType.POST,
                    request.toJsonObject(),
                    data -> {
                        return BeanUtil.toBean(data, GetInstanceInfoResponse.class);
                    });
        } catch (Exception e) {
            log.error("Error getting instance info", e);
            throw new RuntimeException(e);
        }
    }

    // ==================== MCP Server 管理 ====================

    /**
     * 获取 MCP Server 列表
     */
    public ListMcpServersResponse listMcpServers(String gwInstanceId, int current, int size) {
        try {
            ListMcpServersRequest request = new ListMcpServersRequest();
            request.setCurrent(current);
            request.setSize(size);
            request.setGwInstanceId(gwInstanceId);

            return execute(
                    "ListMcpServers",
                    "/mcpServer/listMcpServers",
                    MethodType.POST,
                    request.toJsonObject(),
                    data -> {
                        return BeanUtil.toBean(data, ListMcpServersResponse.class);
                    });
        } catch (Exception e) {
            log.error("Error listing MCP servers", e);
            throw new RuntimeException(e);
        }
    }

    /**
     * 获取 MCP Server 详情
     */
    public GetMcpServerResponse getMcpServer(String gwInstanceId, String mcpServerName) {
        try {
            GetMcpServerRequest request = new GetMcpServerRequest();
            request.setGwInstanceId(gwInstanceId);
            request.setMcpServerName(mcpServerName);

            return execute(
                    "GetMcpServer",
                    "/mcpServer/getMcpServer",
                    MethodType.POST,
                    request.toJsonObject(),
                    data -> {
                        return BeanUtil.toBean(data, GetMcpServerResponse.class);
                    });
        } catch (Exception e) {
            log.error("Error getting MCP server details", e);
            throw new RuntimeException(e);
        }
    }

    /**
     * 为 MCP Server 添加授权的消费者
     */
    public AddMcpServerConsumersResponse addMcpServerConsumers(
            String gwInstanceId, String mcpServerName, List<String> consumerNames) {
        try {
            AddMcpServerConsumersRequest request = new AddMcpServerConsumersRequest();
            request.setGwInstanceId(gwInstanceId);
            request.setMcpServerName(mcpServerName);
            request.setConsumers(consumerNames);

            return execute(
                    "AddMcpServerConsumers",
                    "/mcpServer/addMcpServerConsumers",
                    MethodType.POST,
                    request.toJsonObject(),
                    data -> {
                        return BeanUtil.toBean(data, AddMcpServerConsumersResponse.class);
                    });
        } catch (Exception e) {
            log.error("Error adding MCP server consumers", e);
            throw new RuntimeException(e);
        }
    }

    /**
     * 删除 MCP Server 的授权消费者
     */
    public DeleteMcpServerConsumersResponse deleteMcpServerConsumers(
            String gwInstanceId, String mcpServerName, List<String> consumerNames) {
        try {
            DeleteMcpServerConsumersRequest request = new DeleteMcpServerConsumersRequest();
            request.setGwInstanceId(gwInstanceId);
            request.setMcpServerName(mcpServerName);
            request.setConsumers(consumerNames);

            return execute(
                    "DeleteMcpServerConsumers",
                    "/mcpServer/deleteMcpServerConsumers",
                    MethodType.POST,
                    request.toJsonObject(),
                    data -> {
                        return BeanUtil.toBean(data, DeleteMcpServerConsumersResponse.class);
                    });
        } catch (Exception e) {
            log.error("Error deleting MCP server consumers", e);
            throw new RuntimeException(e);
        }
    }

    // ==================== Model API 管理 ====================

    /**
     * 获取 Model API 列表
     */
    public ListModelApisResponse listModelApis(String gwInstanceId, int current, int size) {
        try {
            ListModelApisRequest request = new ListModelApisRequest();
            request.setCurrent(current);
            request.setSize(size);
            request.setGwInstanceId(gwInstanceId);

            return execute(
                    "ListModelApis",
                    "/modelapi/listModelApis",
                    MethodType.POST,
                    request.toJsonObject(),
                    data -> {
                        return BeanUtil.toBean(data, ListModelApisResponse.class);
                    });
        } catch (Exception e) {
            log.error("Error listing Model APIs", e);
            throw new RuntimeException(e);
        }
    }

    /**
     * 获取 Model API 详情
     */
    public GetModelApiResponse getModelApi(String gwInstanceId, String modelApiId) {
        try {
            GetModelApiRequest request = new GetModelApiRequest();
            request.setGwInstanceId(gwInstanceId);
            request.setId(modelApiId);

            return execute(
                    "GetModelApi",
                    "/modelapi/getModelApi",
                    MethodType.POST,
                    request.toJsonObject(),
                    data -> {
                        return BeanUtil.toBean(data, GetModelApiResponse.class);
                    });
        } catch (Exception e) {
            log.error("Error getting Model API details", e);
            throw new RuntimeException(e);
        }
    }

    // ==================== 应用（Consumer）管理 ====================

    /**
     * 创建应用（Consumer）
     */
    public CreateAppResponse createApp(CreateAppRequest request) {
        try {
            return execute(
                    "CreateApp",
                    "/application/createApp",
                    MethodType.POST,
                    request.toJsonObject(),
                    data -> {
                        return BeanUtil.toBean(data, CreateAppResponse.class);
                    });
        } catch (Exception e) {
            log.error("Error creating app", e);
            throw new RuntimeException(e);
        }
    }

    /**
     * 更新应用（Consumer）
     */
    public ModifyAppResponse modifyApp(ModifyAppRequest request) {
        try {
            return execute(
                    "ModifyApp",
                    "/application/modifyApp",
                    MethodType.POST,
                    request.toJsonObject(),
                    data -> {
                        return BeanUtil.toBean(data, ModifyAppResponse.class);
                    });
        } catch (Exception e) {
            log.error("Error modifying app", e);
            throw new RuntimeException(e);
        }
    }

    /**
     * 删除应用（Consumer）
     */
    public BatchDeleteAppResponse deleteApp(String gwInstanceId, String appId) {
        try {
            BatchDeleteAppRequest request = new BatchDeleteAppRequest();
            request.setGwInstanceId(gwInstanceId);
            request.setAppIds(Collections.singletonList(appId));

            return execute(
                    "BatchDeleteApp",
                    "/application/batchDeleteApp",
                    MethodType.POST,
                    request.toJsonObject(),
                    data -> {
                        return BeanUtil.toBean(data, BatchDeleteAppResponse.class);
                    });
        } catch (Exception e) {
            log.error("Error deleting app", e);
            throw new RuntimeException(e);
        }
    }

    /**
     * 查询应用列表（用于检查 Consumer 是否存在）
     */
    public ListAppsByGwInstanceIdResponse listAppsByGwInstanceId(
            String gwInstanceId, Integer serviceType) {
        try {
            ListAppsByGwInstanceIdRequest request = new ListAppsByGwInstanceIdRequest();
            request.setGwInstanceId(gwInstanceId);
            request.setServiceType(serviceType);

            return execute(
                    "ListAppsByGwInstanceId",
                    "/application/listAppsByGwInstanceId",
                    MethodType.POST,
                    request.toJsonObject(),
                    data -> {
                        return BeanUtil.toBean(data, ListAppsByGwInstanceIdResponse.class);
                    });
        } catch (Exception e) {
            log.error("Error listing apps", e);
            throw new RuntimeException(e);
        }
    }

    // ==================== Model API 授权管理 ====================

    /**
     * 批量授权消费者到 Model API
     */
    public BatchGrantModelApiResponse batchGrantModelApi(
            String gwInstanceId, String modelApiId, List<String> consumerIds) {
        try {
            BatchGrantModelApiRequest request = new BatchGrantModelApiRequest();
            request.setGwInstanceId(gwInstanceId);
            request.setModelApiId(modelApiId);
            request.setConsumerIds(consumerIds);

            return execute(
                    "BatchGrantModelApi",
                    "/modelapi/batchGrantModelApi",
                    MethodType.POST,
                    request.toJsonObject(),
                    data -> BeanUtil.toBean(data, BatchGrantModelApiResponse.class));
        } catch (Exception e) {
            log.error("Error granting model API consumers", e);
            throw new RuntimeException(e);
        }
    }

    /**
     * 撤销消费者对 Model API 的授权
     */
    public RevokeModelApiGrantResponse revokeModelApiGrant(String gwInstanceId, String authId) {
        try {
            RevokeModelApiGrantRequest request = new RevokeModelApiGrantRequest();
            request.setGwInstanceId(gwInstanceId);
            request.setAuthId(authId);

            return execute(
                    "RevokeModelApiGrant",
                    "/modelapi/revokeModelApiGrant",
                    MethodType.POST,
                    request.toJsonObject(),
                    data -> BeanUtil.toBean(data, RevokeModelApiGrantResponse.class));
        } catch (Exception e) {
            log.error("Error revoking model API grant", e);
            throw new RuntimeException(e);
        }
    }

    /**
     * 查询 Model API 的消费者授权列表
     */
    public ListModelApiConsumersResponse listModelApiConsumers(
            String gwInstanceId, String modelApiId, int current, int size) {
        try {
            ListModelApiConsumersRequest request = new ListModelApiConsumersRequest();
            request.setGwInstanceId(gwInstanceId);
            request.setModelApiId(modelApiId);
            request.setEngineType("higress");
            request.setCurrent(current);
            request.setSize(size);

            return execute(
                    "ListModelApiConsumers",
                    "/modelapi/listModelApiConsumers",
                    MethodType.POST,
                    request.toJsonObject(),
                    data -> BeanUtil.toBean(data, ListModelApiConsumersResponse.class));
        } catch (Exception e) {
            log.error("Error listing model API consumers", e);
            throw new RuntimeException(e);
        }
    }

    // ==================== 通用执行方法 ====================

    /**
     * 执行 Apsara API 请求
     *
     * @param action API 动作名称
     * @param pathName API 资源路径
     * @param methodType HTTP 方法类型
     * @param body 请求体
     * @param converter 响应转换器
     * @return 转换后的结果
     */
    public <E> E execute(
            String action,
            String pathName,
            MethodType methodType,
            JSONObject body,
            Function<JSONObject, E> converter) {
        Params params =
                new Params()
                        .setStyle("ROA") // API 风格
                        .setVersion(config.getVersion()) // API 版本号
                        .setAction(action) // API 名称
                        .setPathname(pathName) // API 资源路径
                        .setMethod(methodType.name()) // 请求方法
                        .setProtocol("HTTPS")
                        .setAuthType("AK")
                        .setReqBodyType("json")
                        .setBodyType("json");

        Map<String, String> headers = new HashMap<>();
        headers.put("Accept", "application/json");
        if (config.getRegionId() != null) {
            headers.put("x-acs-regionId", config.getRegionId());
        }
        if (config.getXAcsCallerSdkSource() != null) {
            headers.put("x-acs-caller-sdk-source", config.getXAcsCallerSdkSource());
        }
        if (config.getXAcsResourceGroupId() != null) {
            headers.put("x-acs-resourcegroupid", config.getXAcsResourceGroupId());
        }
        if (config.getXAcsOrganizationId() != null) {
            headers.put("x-acs-organizationid", config.getXAcsOrganizationId());
        }
        if (config.getXAcsCallerType() != null) {
            headers.put("x-acs-caller-type", config.getXAcsCallerType());
        }

        OpenApiRequest request = new OpenApiRequest().setHeaders(headers).setBody(body);

        try {
            Map<String, ?> response = client.callApi(params, request, runtime);
            JSONObject data = JSONUtil.parseObj(response);
            return converter.apply(data);
        } catch (Exception e) {
            log.error("Error executing Apsara request", e);
            throw new BusinessException(
                    ErrorCode.GATEWAY_ERROR,
                    e,
                    "Failed to communicate with Apsara gateway: " + e.getMessage());
        }
    }
}
