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

import com.alibaba.himarket.core.exception.BusinessException;
import com.alibaba.himarket.core.exception.ErrorCode;
import com.alibaba.himarket.dto.params.apsara.AddMcpServerConsumersRequest;
import com.alibaba.himarket.dto.params.apsara.BatchDeleteAppRequest;
import com.alibaba.himarket.dto.params.apsara.BatchGrantModelApiRequest;
import com.alibaba.himarket.dto.params.apsara.CreateAppRequest;
import com.alibaba.himarket.dto.params.apsara.DeleteMcpServerConsumersRequest;
import com.alibaba.himarket.dto.params.apsara.GetInstanceInfoRequest;
import com.alibaba.himarket.dto.params.apsara.GetMcpServerRequest;
import com.alibaba.himarket.dto.params.apsara.GetModelApiRequest;
import com.alibaba.himarket.dto.params.apsara.ListAppsByGwInstanceIdRequest;
import com.alibaba.himarket.dto.params.apsara.ListInstancesRequest;
import com.alibaba.himarket.dto.params.apsara.ListMcpServersRequest;
import com.alibaba.himarket.dto.params.apsara.ListModelApiConsumersRequest;
import com.alibaba.himarket.dto.params.apsara.ListModelApisRequest;
import com.alibaba.himarket.dto.params.apsara.ModifyAppRequest;
import com.alibaba.himarket.dto.params.apsara.RevokeModelApiGrantRequest;
import com.alibaba.himarket.dto.result.apsara.AddMcpServerConsumersResponse;
import com.alibaba.himarket.dto.result.apsara.BatchDeleteAppResponse;
import com.alibaba.himarket.dto.result.apsara.BatchGrantModelApiResponse;
import com.alibaba.himarket.dto.result.apsara.CreateAppResponse;
import com.alibaba.himarket.dto.result.apsara.DeleteMcpServerConsumersResponse;
import com.alibaba.himarket.dto.result.apsara.GetInstanceInfoResponse;
import com.alibaba.himarket.dto.result.apsara.GetMcpServerResponse;
import com.alibaba.himarket.dto.result.apsara.GetModelApiResponse;
import com.alibaba.himarket.dto.result.apsara.ListAppsByGwInstanceIdResponse;
import com.alibaba.himarket.dto.result.apsara.ListInstancesResponse;
import com.alibaba.himarket.dto.result.apsara.ListMcpServersResponse;
import com.alibaba.himarket.dto.result.apsara.ListModelApiConsumersResponse;
import com.alibaba.himarket.dto.result.apsara.ListModelApisResponse;
import com.alibaba.himarket.dto.result.apsara.ModifyAppResponse;
import com.alibaba.himarket.dto.result.apsara.RevokeModelApiGrantResponse;
import com.alibaba.himarket.support.gateway.ApsaraGatewayConfig;
import com.alibaba.himarket.utils.JsonUtil;
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
            log.error(
                    "Failed to create gateway client, dependency=ApsaraGateway,"
                            + " operation=createClient, regionId={}, endpoint={},"
                            + " errorType={}, errorMessage={}",
                    config.getRegionId(),
                    config.getDomain(),
                    e.getClass().getSimpleName(),
                    e.getMessage(),
                    e);
            throw new BusinessException(
                    ErrorCode.GATEWAY_ERROR,
                    e,
                    "Failed to create Apsara gateway client: " + e.getMessage());
        }
    }

    @Override
    public void close() {
        // Client doesn't need explicit closing
    }

    // Gateway instance operations.

    /**
     * Lists gateway instances.
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
                    data -> toResponse(data, ListInstancesResponse.class));
        } catch (Exception e) {
            throw toApsaraClientException("ListInstances", e);
        }
    }

    /**
     * Gets gateway instance details.
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
                    data -> toResponse(data, GetInstanceInfoResponse.class));
        } catch (Exception e) {
            throw toApsaraClientException("GetInstanceInfo", e);
        }
    }

    // MCP Server operations.

    /**
     * Lists MCP Servers.
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
                    data -> toResponse(data, ListMcpServersResponse.class));
        } catch (Exception e) {
            throw toApsaraClientException("ListMcpServers", e);
        }
    }

    /**
     * Gets MCP Server details.
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
                    data -> toResponse(data, GetMcpServerResponse.class));
        } catch (Exception e) {
            throw toApsaraClientException("GetMcpServer", e);
        }
    }

    /**
     * Adds authorized consumers to an MCP Server.
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
                    data -> toResponse(data, AddMcpServerConsumersResponse.class));
        } catch (Exception e) {
            throw toApsaraClientException("AddMcpServerConsumers", e);
        }
    }

    /**
     * Deletes authorized consumers from an MCP Server.
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
                    data -> toResponse(data, DeleteMcpServerConsumersResponse.class));
        } catch (Exception e) {
            throw toApsaraClientException("DeleteMcpServerConsumers", e);
        }
    }

    // Model API operations.

    /**
     * Lists Model APIs.
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
                    data -> toResponse(data, ListModelApisResponse.class));
        } catch (Exception e) {
            throw toApsaraClientException("ListModelApis", e);
        }
    }

    /**
     * Gets Model API details.
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
                    data -> toResponse(data, GetModelApiResponse.class));
        } catch (Exception e) {
            throw toApsaraClientException("GetModelApi", e);
        }
    }

    // Application and consumer operations.

    /**
     * Creates an application consumer.
     */
    public CreateAppResponse createApp(CreateAppRequest request) {
        try {
            return execute(
                    "CreateApp",
                    "/application/createApp",
                    MethodType.POST,
                    request.toJsonObject(),
                    data -> toResponse(data, CreateAppResponse.class));
        } catch (Exception e) {
            throw toApsaraClientException("CreateApp", e);
        }
    }

    /**
     * Updates an application consumer.
     */
    public ModifyAppResponse modifyApp(ModifyAppRequest request) {
        try {
            return execute(
                    "ModifyApp",
                    "/application/modifyApp",
                    MethodType.POST,
                    request.toJsonObject(),
                    data -> toResponse(data, ModifyAppResponse.class));
        } catch (Exception e) {
            throw toApsaraClientException("ModifyApp", e);
        }
    }

    /**
     * Deletes an application consumer.
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
                    data -> toResponse(data, BatchDeleteAppResponse.class));
        } catch (Exception e) {
            throw toApsaraClientException("BatchDeleteApp", e);
        }
    }

    /**
     * Lists applications, used to check whether a consumer exists.
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
                    data -> toResponse(data, ListAppsByGwInstanceIdResponse.class));
        } catch (Exception e) {
            throw toApsaraClientException("ListAppsByGwInstanceId", e);
        }
    }

    // Model API authorization operations.

    /**
     * Grants consumers to a Model API in batch.
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
                    data -> toResponse(data, BatchGrantModelApiResponse.class));
        } catch (Exception e) {
            throw toApsaraClientException("BatchGrantModelApi", e);
        }
    }

    /**
     * Revokes a consumer grant from a Model API.
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
                    data -> toResponse(data, RevokeModelApiGrantResponse.class));
        } catch (Exception e) {
            throw toApsaraClientException("RevokeModelApiGrant", e);
        }
    }

    /**
     * Lists consumer grants for a Model API.
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
                    data -> toResponse(data, ListModelApiConsumersResponse.class));
        } catch (Exception e) {
            throw toApsaraClientException("ListModelApiConsumers", e);
        }
    }

    // Common execution.

    /**
     * Executes an Apsara API request.
     *
     * @param action API action name
     * @param pathName API resource path
     * @param methodType HTTP method
     * @param body request body
     * @param converter response converter
     * @return converted result
     */
    public <E> E execute(
            String action,
            String pathName,
            MethodType methodType,
            Map<String, Object> body,
            Function<Map<String, Object>, E> converter) {
        Params params =
                new Params()
                        .setStyle("ROA")
                        .setVersion(config.getVersion())
                        .setAction(action)
                        .setPathname(pathName)
                        .setMethod(methodType.name())
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
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) (Map<?, ?>) response;
            return converter.apply(data);
        } catch (Exception e) {
            log.error(
                    "Failed to execute gateway request, dependency=ApsaraGateway,"
                            + " operation=execute, action={}, path={}, method={},"
                            + " body={}, errorType={}, errorMessage={}",
                    action,
                    pathName,
                    methodType,
                    body,
                    e.getClass().getSimpleName(),
                    e.getMessage(),
                    e);
            throw new BusinessException(
                    ErrorCode.GATEWAY_ERROR,
                    e,
                    "Failed to communicate with Apsara gateway: " + e.getMessage());
        }
    }

    private <E> E toResponse(Map<String, Object> data, Class<E> responseType) {
        return JsonUtil.convert(data, responseType);
    }

    private void logApsaraClientError(String operation, Exception e) {
        log.error(
                "Failed to execute gateway client operation, dependency=ApsaraGateway,"
                        + " operation={}, errorType={}, errorMessage={}",
                operation,
                e.getClass().getSimpleName(),
                e.getMessage(),
                e);
    }

    private RuntimeException toApsaraClientException(String operation, Exception e) {
        logApsaraClientError(operation, e);
        return new RuntimeException(
                "Failed to execute Apsara gateway operation " + operation + ": " + e.getMessage(),
                e);
    }
}
