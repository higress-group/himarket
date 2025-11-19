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


package com.alibaba.apiopenplatform.service.gateway;

import com.alibaba.apiopenplatform.core.exception.BusinessException;
import com.alibaba.apiopenplatform.core.exception.ErrorCode;
import com.alibaba.apiopenplatform.dto.params.gateway.QueryAdpAIGatewayParam;
import com.alibaba.apiopenplatform.dto.result.httpapi.APIResult;
import com.alibaba.apiopenplatform.dto.result.common.PageResult;
import com.alibaba.apiopenplatform.dto.result.gateway.AdpGatewayInstanceResult;
import com.alibaba.apiopenplatform.dto.result.agent.AgentAPIResult;
import com.alibaba.apiopenplatform.dto.result.gateway.GatewayResult;
import com.alibaba.apiopenplatform.dto.result.common.DomainResult;
import com.alibaba.apiopenplatform.dto.result.mcp.AdpMcpServerListResult;
import com.alibaba.apiopenplatform.dto.result.mcp.GatewayMCPServerResult;
import com.alibaba.apiopenplatform.dto.result.model.GatewayModelAPIResult;
import com.alibaba.apiopenplatform.entity.Consumer;
import com.alibaba.apiopenplatform.entity.ConsumerCredential;
import com.alibaba.apiopenplatform.entity.Gateway;
import com.alibaba.apiopenplatform.support.consumer.AdpAIAuthConfig;
import com.alibaba.apiopenplatform.support.consumer.ConsumerAuthConfig;
import com.alibaba.apiopenplatform.support.enums.GatewayType;
import com.alibaba.apiopenplatform.support.gateway.AdpAIGatewayConfig;
import com.alibaba.apiopenplatform.service.gateway.client.AdpAIGatewayClient;
import com.alibaba.apiopenplatform.support.gateway.GatewayConfig;
import com.alibaba.apiopenplatform.support.product.APIGRefConfig;
import com.alibaba.apiopenplatform.dto.result.mcp.MCPConfigResult;
import cn.hutool.json.JSONUtil;
import com.aliyun.sdk.service.apig20240327.models.HttpApiApiInfo;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * ADP AI网关操作器
 */
@Service
@Slf4j
public class AdpAIGatewayOperator extends GatewayOperator {

    @Override
    public PageResult<APIResult> fetchHTTPAPIs(Gateway gateway, int page, int size) {
        return null;
    }

    @Override
    public PageResult<APIResult> fetchRESTAPIs(Gateway gateway, int page, int size) {
        return null;
    }

    @Override
    public PageResult<? extends GatewayMCPServerResult> fetchMcpServers(Gateway gateway, int page, int size) {
        AdpAIGatewayConfig config = gateway.getAdpAIGatewayConfig();
        if (config == null) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "ADP AI Gateway 配置缺失");
        }

        AdpAIGatewayClient client = new AdpAIGatewayClient(config);
        try {
            String url = client.getFullUrl("/mcpServer/listMcpServers");
            // 修复：添加必需的 gwInstanceId 参数
            String requestBody = String.format(
                "{\"current\": %d, \"size\": %d, \"gwInstanceId\": \"%s\"}", 
                page, 
                size, 
                gateway.getGatewayId()
            );
            HttpEntity<String> requestEntity = client.createRequestEntity(requestBody);

            ResponseEntity<AdpMcpServerListResult> response = client.getRestTemplate().exchange(
                    url, HttpMethod.POST, requestEntity, AdpMcpServerListResult.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                AdpMcpServerListResult result = response.getBody();
                if (result.getCode() != null && result.getCode() == 200 && result.getData() != null) {
                    List<GatewayMCPServerResult> items = new ArrayList<>();
                    if (result.getData().getRecords() != null) {
                        items.addAll(result.getData().getRecords());
                    }
                    int total = result.getData().getTotal() != null ? result.getData().getTotal() : 0;
                    return PageResult.of(items, page, size, total);
                }
                String msg = result.getMessage() != null ? result.getMessage() : result.getMsg();
                throw new BusinessException(ErrorCode.GATEWAY_ERROR, msg);
            }
            throw new BusinessException(ErrorCode.GATEWAY_ERROR, "调用 ADP /mcpServer/listMcpServers 失败");
        } catch (Exception e) {
            log.error("Error fetching ADP MCP servers", e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, e.getMessage());
        } finally {
            client.close();
        }
    }

    @Override
    public PageResult<AgentAPIResult> fetchAgentAPIs(Gateway gateway, int page, int size) {
        return null;
    }

    @Override
    public PageResult<? extends GatewayModelAPIResult> fetchModelAPIs(Gateway gateway, int page, int size) {
        return null;
    }

    @Override
    public String fetchAPIConfig(Gateway gateway, Object config) {
        return "";
    }

    @Override
    public String fetchMcpConfig(Gateway gateway, Object conf) {
        AdpAIGatewayConfig config = gateway.getAdpAIGatewayConfig();
        if (config == null) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "ADP AI Gateway 配置缺失");
        }

        // 从 conf 参数中获取 APIGRefConfig
        APIGRefConfig apigRefConfig = (APIGRefConfig) conf;
        if (apigRefConfig == null || apigRefConfig.getMcpServerName() == null) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "MCP Server 名称缺失");
        }

        AdpAIGatewayClient client = new AdpAIGatewayClient(config);
        try {
            String url = client.getFullUrl("/mcpServer/getMcpServer");
            
            // 构建请求体，包含 gwInstanceId 和 mcpServerName
            String requestBody = String.format(
                "{\"gwInstanceId\": \"%s\", \"mcpServerName\": \"%s\"}", 
                gateway.getGatewayId(), 
                apigRefConfig.getMcpServerName()
            );
            
            HttpEntity<String> requestEntity = client.createRequestEntity(requestBody);

            ResponseEntity<AdpMcpServerDetailResult> response = client.getRestTemplate().exchange(
                    url, HttpMethod.POST, requestEntity, AdpMcpServerDetailResult.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                AdpMcpServerDetailResult result = response.getBody();
                if (result.getCode() != null && result.getCode() == 200 && result.getData() != null) {
                    return convertToMCPConfig(result.getData(), config);
                }
                String msg = result.getMessage() != null ? result.getMessage() : result.getMsg();
                throw new BusinessException(ErrorCode.GATEWAY_ERROR, msg);
            }
            throw new BusinessException(ErrorCode.GATEWAY_ERROR, "调用 ADP /mcpServer/getMcpServer 失败");
        } catch (Exception e) {
            log.error("Error fetching ADP MCP config for server: {}", apigRefConfig.getMcpServerName(), e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, e.getMessage());
        } finally {
            client.close();
        }
    }

    @Override
    public String fetchAgentConfig(Gateway gateway, Object conf) {
        return "";
    }

    @Override
    public String fetchModelConfig(Gateway gateway, Object conf) {
        return "";
    }

    /**
     * 将 ADP MCP Server 详情转换为 MCPConfigResult 格式
     */
    private String convertToMCPConfig(AdpMcpServerDetailResult.AdpMcpServerDetail data, AdpAIGatewayConfig config) {
        MCPConfigResult mcpConfig = new MCPConfigResult();
        mcpConfig.setMcpServerName(data.getName());

        // 设置 MCP Server 配置
        MCPConfigResult.MCPServerConfig serverConfig = new MCPConfigResult.MCPServerConfig();
        serverConfig.setPath("/" + data.getName());
        
        // 获取网关实例访问信息并设置域名信息
        List<DomainResult> domains = getGatewayAccessDomains(data.getGwInstanceId(), config);
        if (domains != null && !domains.isEmpty()) {
            serverConfig.setDomains(domains);
        } else {
            // 如果无法获取网关访问信息，则使用原有的services信息作为备选
            if (data.getServices() != null && !data.getServices().isEmpty()) {
                List<DomainResult> fallbackDomains = data.getServices().stream()
                        .map(domain -> DomainResult.builder()
                                .domain(domain.getName() + ":" + domain.getPort())
                                .protocol("http")
                                .build())
                        .collect(Collectors.toList());
                serverConfig.setDomains(fallbackDomains);
            }
        }
        
        mcpConfig.setMcpServerConfig(serverConfig);

        // 设置工具配置
        mcpConfig.setTools(data.getRawConfigurations());

        // 设置元数据
        MCPConfigResult.McpMetadata meta = new MCPConfigResult.McpMetadata();
        meta.setSource(GatewayType.ADP_AI_GATEWAY.name());
        meta.setCreateFromType(data.getType());
        mcpConfig.setMeta(meta);

        return JSONUtil.toJsonStr(mcpConfig);
    }

    /**
     * 获取网关实例的访问信息并构建域名列表
     */
    private List<DomainResult> getGatewayAccessDomains(String gwInstanceId, AdpAIGatewayConfig config) {
        AdpAIGatewayClient client = new AdpAIGatewayClient(config);
        try {
            String url = client.getFullUrl("/gatewayInstance/getInstanceInfo");
            String requestBody = String.format("{\"gwInstanceId\": \"%s\"}", gwInstanceId);
            HttpEntity<String> requestEntity = client.createRequestEntity(requestBody);

            // 注意：getInstanceInfo 返回的 data 是单个实例对象（无 records 字段），直接从 data.accessMode 读取
            ResponseEntity<String> response = client.getRestTemplate().exchange(
                    url, HttpMethod.POST, requestEntity, String.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                cn.hutool.json.JSONObject root = JSONUtil.parseObj(response.getBody());
                Integer code = root.getInt("code");
                if (code != null && code == 200 && root.containsKey("data")) {
                    cn.hutool.json.JSONObject dataObj = root.getJSONObject("data");
                    if (dataObj != null && dataObj.containsKey("accessMode")) {
                        cn.hutool.json.JSONArray arr = dataObj.getJSONArray("accessMode");
                        List<AdpGatewayInstanceResult.AccessMode> accessModes = JSONUtil.toList(arr, AdpGatewayInstanceResult.AccessMode.class);
                        return buildDomainsFromAccessModes(accessModes);
                    }
                    log.warn("Gateway instance has no accessMode, instanceId={}", gwInstanceId);
                    return null;
                }
                String message = root.getStr("message", root.getStr("msg"));
                log.warn("Failed to get gateway instance access info: {}", message);
                return null;
            }
            log.warn("Failed to call gateway instance access API");
            return null;
        } catch (Exception e) {
            log.error("Error fetching gateway access info for instance: {}", gwInstanceId, e);
            return null;
        } finally {
            client.close();
        }
    }

    /**
     * 根据网关实例访问信息构建域名列表
     */
    private List<DomainResult> buildDomainsFromAccessInfo(AdpGatewayInstanceResult.AdpGatewayInstanceData data) {
        // 兼容 listInstances 调用：取第一条记录的 accessMode
        if (data != null && data.getRecords() != null && !data.getRecords().isEmpty()) {
            AdpGatewayInstanceResult.AdpGatewayInstance instance = data.getRecords().get(0);
            if (instance.getAccessMode() != null) {
                return buildDomainsFromAccessModes(instance.getAccessMode());
            }
        }
        return new ArrayList<>();
    }

    private List<DomainResult> buildDomainsFromAccessModes(List<AdpGatewayInstanceResult.AccessMode> accessModes) {
        List<DomainResult> domains = new ArrayList<>();
        if (accessModes == null || accessModes.isEmpty()) { return domains; }
        AdpGatewayInstanceResult.AccessMode accessMode = accessModes.get(0);

        // 1) LoadBalancer: externalIps:80
        if ("LoadBalancer".equalsIgnoreCase(accessMode.getAccessModeType())) {
            if (accessMode.getExternalIps() != null && !accessMode.getExternalIps().isEmpty()) {
                for (String externalIp : accessMode.getExternalIps()) {
                    if (externalIp == null || externalIp.isEmpty()) { continue; }
                    DomainResult domain = DomainResult.builder()
                            .domain(externalIp + ":80")
                            .protocol("http")
                            .build();
                    domains.add(domain);
                }
            }
        }

        // 2) NodePort: ips + ports → ip:nodePort
        if (domains.isEmpty() && "NodePort".equalsIgnoreCase(accessMode.getAccessModeType())) {
            List<String> ips = accessMode.getIps();
            List<String> ports = accessMode.getPorts();
            if (ips != null && !ips.isEmpty() && ports != null && !ports.isEmpty()) {
                for (String ip : ips) {
                    if (ip == null || ip.isEmpty()) { continue; }
                    for (String portMapping : ports) {
                        if (portMapping == null || portMapping.isEmpty()) { continue; }
                        String[] parts = portMapping.split(":");
                        if (parts.length >= 2) {
                            String nodePort = parts[1].split("/")[0];
                            DomainResult domain = DomainResult.builder()
                                    .domain(ip + ":" + nodePort)
                                    .protocol("http")
                                    .build();
                            domains.add(domain);
                        }
                    }
                }
            }
        }

        // 3) fallback: only externalIps → :80
        if (domains.isEmpty() && accessMode.getExternalIps() != null && !accessMode.getExternalIps().isEmpty()) {
            for (String externalIp : accessMode.getExternalIps()) {
                if (externalIp == null || externalIp.isEmpty()) { continue; }
                DomainResult domain = DomainResult.builder()
                        .domain(externalIp + ":80")
                        .protocol("http")
                        .build();
                domains.add(domain);
            }
        }

        return domains;
    }

    @Override
    public String createConsumer(Consumer consumer, ConsumerCredential credential, GatewayConfig config) {
        AdpAIGatewayConfig adpConfig = config.getAdpAIGatewayConfig();
        if (adpConfig == null) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "ADP AI Gateway配置缺失");
        }

        AdpAIGatewayClient client = new AdpAIGatewayClient(adpConfig);
        try {
            // 构建请求参数
            cn.hutool.json.JSONObject requestData = JSONUtil.createObj();
            requestData.set("authType", 5);

            // 从凭证中获取key
            if (credential.getApiKeyConfig() != null && 
                credential.getApiKeyConfig().getCredentials() != null &&
                !credential.getApiKeyConfig().getCredentials().isEmpty()) {
                String key = credential.getApiKeyConfig().getCredentials().get(0).getApiKey();
                requestData.set("key", key);
            }

            requestData.set("appName", consumer.getName());
            
            // 从 GatewayConfig 中获取 Gateway 实体，与 fetchMcpConfig 方法保持一致
            Gateway gateway = config.getGateway();
            if (gateway == null || gateway.getGatewayId() == null) {
                throw new BusinessException(ErrorCode.INVALID_PARAMETER, "网关实例ID缺失");
            }
            requestData.set("gwInstanceId", gateway.getGatewayId());

            String url = client.getFullUrl("/application/createApp");
            String requestBody = requestData.toString();
            HttpEntity<String> requestEntity = client.createRequestEntity(requestBody);

            log.info("Creating consumer in ADP gateway: url={}, requestBody={}", url, requestBody);

            ResponseEntity<String> response = client.getRestTemplate().exchange(
                    url, HttpMethod.POST, requestEntity, String.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                log.info("ADP gateway response: {}", response.getBody());
                // 对于ADP AI网关，返回的data就是appName，可以直接用于后续的MCP授权
                return extractConsumerIdFromResponse(response.getBody(), consumer.getName());
            }
            throw new BusinessException(ErrorCode.GATEWAY_ERROR, "Failed to create consumer in ADP gateway");
        } catch (BusinessException e) {
            log.error("Business error creating consumer in ADP gateway", e);
            throw e;
        } catch (Exception e) {
            log.error("Error creating consumer in ADP gateway", e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, 
                "Error creating consumer in ADP gateway: " + e.getMessage());
        } finally {
            client.close();
        }
    }

    /**
     * 从响应中提取消费者ID
     * 对于ADP AI网关，/application/createApp接口返回的data就是appName（应用名称）
     * 我们直接返回appName，这样在授权时可以直接使用
     */
    private String extractConsumerIdFromResponse(String responseBody, String defaultConsumerId) {
        try {
            cn.hutool.json.JSONObject responseJson = JSONUtil.parseObj(responseBody);
            // ADP AI网关的/application/createApp接口，成功时返回格式: {"code": 200, "data": "appName"}
            if (responseJson.getInt("code", 0) == 200 && responseJson.containsKey("data")) {
                Object dataObj = responseJson.get("data");
                if (dataObj != null) {
                    // data字段就是appName，直接返回
                    if (dataObj instanceof String) {
                        return (String) dataObj;
                    }
                    // 如果data是对象类型，则按原逻辑处理（兼容性考虑）
                    if (dataObj instanceof cn.hutool.json.JSONObject) {
                        cn.hutool.json.JSONObject data = (cn.hutool.json.JSONObject) dataObj;
                        if (data.containsKey("applicationId")) {
                            return data.getStr("applicationId");
                        }
                        // 如果没有applicationId字段，将整个data对象转为字符串
                        return data.toString();
                    }
                    // 其他类型直接转为字符串
                    return dataObj.toString();
                }
            }
            // 如果无法解析，使用应用名称作为fallback
            return defaultConsumerId; // 这里传入的是consumer.getName()
        } catch (Exception e) {
            log.warn("Failed to parse response body, using consumer name as fallback", e);
            return defaultConsumerId;
        }
    }

    @Override
    public void updateConsumer(String consumerId, ConsumerCredential credential, GatewayConfig config) {
        AdpAIGatewayConfig adpConfig = config.getAdpAIGatewayConfig();
        if (adpConfig == null) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "ADP AI Gateway配置缺失");
        }

        Gateway gateway = config.getGateway();
        if (gateway == null || gateway.getGatewayId() == null) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "网关实例ID缺失");
        }

        AdpAIGatewayClient client = new AdpAIGatewayClient(adpConfig);
        try {

            // 从凭据中提取API Key
            String apiKey = null;
            if (credential != null
                    && credential.getApiKeyConfig() != null
                    && credential.getApiKeyConfig().getCredentials() != null
                    && !credential.getApiKeyConfig().getCredentials().isEmpty()) {
                apiKey = credential.getApiKeyConfig().getCredentials().get(0).getApiKey();
            }

            String url = client.getFullUrl("/application/modifyApp");

            // 明确各参数含义，避免重复使用consumerId带来的混淆
            String appId = consumerId;  // 应用ID，用于标识要更新的应用
            String appName = consumerId;  // 应用名称，通常与appId保持一致
            String description = "Consumer managed by Portal";  // 语义化的描述信息
            Integer authType = 5;  // 认证类型：API_KEY
            String authTypeName = "API_KEY";  // 认证类型名称
            Boolean enable = true;  // 启用状态

            // 构建请求体
            cn.hutool.json.JSONObject requestData = JSONUtil.createObj();
            requestData.set("appId", appId);
            requestData.set("appName", appName);
            requestData.set("authType", authType);
            requestData.set("authTypeName", authTypeName);
            requestData.set("description", description);
            requestData.set("enable", enable);
            if (apiKey != null) {
                requestData.set("key", apiKey);
            }
            requestData.set("groups", Collections.singletonList("true"));
            requestData.set("gwInstanceId", gateway.getGatewayId());

            String requestBody = requestData.toString();
            HttpEntity<String> requestEntity = client.createRequestEntity(requestBody);

            log.info("Updating consumer in ADP gateway: url={}, requestBody={}", url, requestBody);

            ResponseEntity<String> response = client.getRestTemplate().exchange(
                    url, HttpMethod.POST, requestEntity, String.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                cn.hutool.json.JSONObject responseJson = JSONUtil.parseObj(response.getBody());
                Integer code = responseJson.getInt("code", 0);
                if (code != null && code == 200) {
                    log.info("Successfully updated consumer {} in ADP gateway instance {}", consumerId, gateway.getGatewayId());
                    return;
                }
                String message = responseJson.getStr("message", responseJson.getStr("msg", "Unknown error"));
                throw new BusinessException(ErrorCode.GATEWAY_ERROR, "更新ADP网关消费者失败: " + message);
            }
            throw new BusinessException(ErrorCode.GATEWAY_ERROR, "调用 ADP /application/modifyApp 失败");
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error updating consumer {} in ADP gateway instance {}", consumerId, 
                    gateway != null ? gateway.getGatewayId() : "unknown", e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "更新ADP网关消费者异常: " + e.getMessage());
        } finally {
            client.close();
        }
    }

    @Override
    public void deleteConsumer(String consumerId, GatewayConfig config) {
        AdpAIGatewayConfig adpConfig = config.getAdpAIGatewayConfig();
        if (adpConfig == null) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "ADP AI Gateway配置缺失");
        }

        Gateway gateway = config.getGateway();
        if (gateway == null || gateway.getGatewayId() == null) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "网关实例ID缺失");
        }

        AdpAIGatewayClient client = new AdpAIGatewayClient(adpConfig);
        try {

            String url = client.getFullUrl("/application/deleteApp");
            String requestBody = String.format(
                "{\"appId\": \"%s\", \"gwInstanceId\": \"%s\"}",
                consumerId, gateway.getGatewayId()
            );
            HttpEntity<String> requestEntity = client.createRequestEntity(requestBody);

            log.info("Deleting consumer in ADP gateway: url={}, requestBody={}", url, requestBody);

            ResponseEntity<String> response = client.getRestTemplate().exchange(
                    url, HttpMethod.POST, requestEntity, String.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                cn.hutool.json.JSONObject responseJson = JSONUtil.parseObj(response.getBody());
                Integer code = responseJson.getInt("code", 0);
                if (code != null && code == 200) {
                    log.info("Successfully deleted consumer {} from ADP gateway instance {}", 
                             consumerId, gateway.getGatewayId());
                    return;
                }
                String message = responseJson.getStr("message", responseJson.getStr("msg", "Unknown error"));
                throw new BusinessException(ErrorCode.GATEWAY_ERROR, "删除ADP网关消费者失败: " + message);
            }
            throw new BusinessException(ErrorCode.GATEWAY_ERROR, "调用 ADP /application/deleteApp 失败");
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error deleting consumer {} from ADP gateway instance {}", 
                      consumerId, gateway != null ? gateway.getGatewayId() : "unknown", e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "删除ADP网关消费者异常: " + e.getMessage());
        } finally {
            client.close();
        }
    }

    @Override
    public boolean isConsumerExists(String consumerId, GatewayConfig config) {
        AdpAIGatewayConfig adpConfig = config.getAdpAIGatewayConfig();
        if (adpConfig == null) {
            log.warn("ADP AI Gateway配置缺失，无法检查消费者存在性");
            return false;
        }

        AdpAIGatewayClient client = new AdpAIGatewayClient(adpConfig);
        try {
            // 从 GatewayConfig 中获取 Gateway 实体
            Gateway gateway = config.getGateway();
            if (gateway == null || gateway.getGatewayId() == null) {
                log.warn("网关实例ID缺失，无法检查消费者存在性");
                return false;
            }

            String url = client.getFullUrl("/application/getApp");
            String requestBody = String.format(
                "{\"%s\": \"%s\", \"%s\": \"%s\"}", 
                "gwInstanceId", gateway.getGatewayId(),
                "appId", consumerId
            );
            HttpEntity<String> requestEntity = client.createRequestEntity(requestBody);

            ResponseEntity<String> response = client.getRestTemplate().exchange(
                    url, HttpMethod.POST, requestEntity, String.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                cn.hutool.json.JSONObject responseJson = JSONUtil.parseObj(response.getBody());
                Integer code = responseJson.getInt("code", 0);
                // 如果返回200且有data，说明消费者存在
                return code == 200 && responseJson.containsKey("data") && responseJson.get("data") != null;
            }
            return false;
        } catch (Exception e) {
            log.warn("检查ADP网关消费者存在性失败: consumerId={}", consumerId, e);
            return false;
        } finally {
            client.close();
        }
    }

    @Override
    public ConsumerAuthConfig authorizeConsumer(Gateway gateway, String consumerId, Object refConfig) {
        AdpAIGatewayConfig adpConfig = gateway.getAdpAIGatewayConfig();
        if (adpConfig == null) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "ADP AI Gateway配置缺失");
        }

        // 解析MCP Server配置
        APIGRefConfig apigRefConfig = (APIGRefConfig) refConfig;
        if (apigRefConfig == null || apigRefConfig.getMcpServerName() == null) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "MCP Server名称缺失");
        }

        AdpAIGatewayClient client = new AdpAIGatewayClient(adpConfig);
        try {
            // 构建授权请求参数
            // 由于createConsumer返回的就是appName，所以consumerId就是应用名称
            cn.hutool.json.JSONObject requestData = JSONUtil.createObj();
            requestData.set("mcpServerName", apigRefConfig.getMcpServerName());
            requestData.set("consumers", Collections.singletonList(consumerId)); // consumerId就是appName
            requestData.set("gwInstanceId", gateway.getGatewayId());

            String url = client.getFullUrl("/mcpServer/addMcpServerConsumers");
            String requestBody = requestData.toString();
            HttpEntity<String> requestEntity = client.createRequestEntity(requestBody);

            log.info("Authorizing consumer to MCP server: url={}, requestBody={}", url, requestBody);

            ResponseEntity<String> response = client.getRestTemplate().exchange(
                    url, HttpMethod.POST, requestEntity, String.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                cn.hutool.json.JSONObject responseJson = JSONUtil.parseObj(response.getBody());
                Integer code = responseJson.getInt("code", 0);
                
                if (code == 200) {
                    log.info("Successfully authorized consumer {} to MCP server {}", 
                        consumerId, apigRefConfig.getMcpServerName());
                    
                    // 构建授权配置返回结果
                    AdpAIAuthConfig authConfig = AdpAIAuthConfig.builder()
                            .mcpServerName(apigRefConfig.getMcpServerName())
                            .consumerId(consumerId)
                            .gwInstanceId(gateway.getGatewayId())
                            .build();
                    
                    return ConsumerAuthConfig.builder()
                            .adpAIAuthConfig(authConfig)
                            .build();
                } else {
                    String message = responseJson.getStr("message", responseJson.getStr("msg", "Unknown error"));
                    throw new BusinessException(ErrorCode.GATEWAY_ERROR, 
                        "Failed to authorize consumer to MCP server: " + message);
                }
            }
            throw new BusinessException(ErrorCode.GATEWAY_ERROR, "Failed to authorize consumer to MCP server");
        } catch (BusinessException e) {
            log.error("Business error authorizing consumer to MCP server", e);
            throw e;
        } catch (Exception e) {
            log.error("Error authorizing consumer {} to MCP server {}", 
                consumerId, apigRefConfig.getMcpServerName(), e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, 
                "Error authorizing consumer to MCP server: " + e.getMessage());
        } finally {
            client.close();
        }
    }

    @Override
    public void revokeConsumerAuthorization(Gateway gateway, String consumerId, ConsumerAuthConfig authConfig) {
        AdpAIAuthConfig adpAIAuthConfig = authConfig.getAdpAIAuthConfig();
        if (adpAIAuthConfig == null) {
            log.warn("ADP AI 授权配置为空，无法撤销授权");
            return;
        }

        AdpAIGatewayConfig adpConfig = gateway.getAdpAIGatewayConfig();
        if (adpConfig == null) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "ADP AI Gateway配置缺失");
        }

        AdpAIGatewayClient client = new AdpAIGatewayClient(adpConfig);
        try {
            // 构建撤销授权请求参数
            // 由于createConsumer返回的就是appName，所以consumerId就是应用名称
            cn.hutool.json.JSONObject requestData = JSONUtil.createObj();
            requestData.set("mcpServerName", adpAIAuthConfig.getMcpServerName());
            requestData.set("consumers", Collections.singletonList(consumerId)); // consumerId就是appName
            requestData.set("gwInstanceId", gateway.getGatewayId());

            String url = client.getFullUrl("/mcpServer/deleteMcpServerConsumers");
            String requestBody = requestData.toString();
            HttpEntity<String> requestEntity = client.createRequestEntity(requestBody);

            log.info("Revoking consumer authorization from MCP server: url={}, requestBody={}", url, requestBody);

            ResponseEntity<String> response = client.getRestTemplate().exchange(
                    url, HttpMethod.POST, requestEntity, String.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                cn.hutool.json.JSONObject responseJson = JSONUtil.parseObj(response.getBody());
                Integer code = responseJson.getInt("code", 0);
                
                if (code == 200) {
                    log.info("Successfully revoked consumer {} authorization from MCP server {}", 
                        consumerId, adpAIAuthConfig.getMcpServerName());
                    return;
                }

                // 获取错误信息
                String message = responseJson.getStr("message", responseJson.getStr("msg", "Unknown error"));

                // 如果是资源不存在（已被删除），只记录警告，不抛异常
                if (message != null && (message.contains("not found") || message.contains("不存在")
                        || message.contains("NotFound") || code == 404)) {
                    log.warn("Consumer authorization already removed or not found: consumerId={}, mcpServer={}, message={}",
                        consumerId, adpAIAuthConfig.getMcpServerName(), message);
                    return;
                }

                // 其他错误抛出异常
                String errorMsg = "Failed to revoke consumer authorization from MCP server: " + message;
                log.error(errorMsg);
                throw new BusinessException(ErrorCode.GATEWAY_ERROR, errorMsg);
            }

            throw new BusinessException(ErrorCode.GATEWAY_ERROR,
                "Failed to revoke consumer authorization, HTTP status: " + response.getStatusCode());
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error revoking consumer {} authorization from MCP server {}", 
                consumerId, adpAIAuthConfig.getMcpServerName(), e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                "Error revoking consumer authorization: " + e.getMessage());
        } finally {
            client.close();
        }
    }

    @Override
    public HttpApiApiInfo fetchAPI(Gateway gateway, String apiId) {
        return null;
    }

    @Override
    public GatewayType getGatewayType() {
        return GatewayType.ADP_AI_GATEWAY;
    }

    @Override
    public String getDashboard(Gateway gateway,String type) {
        return null;
    }

    @Override
    public List<String> fetchGatewayIps(Gateway gateway) {
        return Collections.emptyList();
    }

    @Override
    public PageResult<GatewayResult> fetchGateways(Object param, int page, int size) {
        if (!(param instanceof QueryAdpAIGatewayParam)) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "param");
        }
        return fetchGateways((QueryAdpAIGatewayParam) param, page, size);
    }

    public PageResult<GatewayResult> fetchGateways(QueryAdpAIGatewayParam param, int page, int size) {
        AdpAIGatewayConfig config = new AdpAIGatewayConfig();
        config.setBaseUrl(param.getBaseUrl());
        config.setPort(param.getPort());
        
        // 根据认证类型设置不同的认证信息
        if ("Seed".equals(param.getAuthType())) {
            if (param.getAuthSeed() == null || param.getAuthSeed().trim().isEmpty()) {
                throw new BusinessException(ErrorCode.INVALID_PARAMETER, "Seed认证方式下authSeed不能为空");
            }
            config.setAuthSeed(param.getAuthSeed());
        } else if ("Header".equals(param.getAuthType())) {
            if (param.getAuthHeaders() == null || param.getAuthHeaders().isEmpty()) {
                throw new BusinessException(ErrorCode.INVALID_PARAMETER, "Header认证方式下authHeaders不能为空");
            }
            // 将authHeaders转换为配置
            List<AdpAIGatewayConfig.AuthHeader> configHeaders = new ArrayList<>();
            for (QueryAdpAIGatewayParam.AuthHeader paramHeader : param.getAuthHeaders()) {
                AdpAIGatewayConfig.AuthHeader configHeader = new AdpAIGatewayConfig.AuthHeader();
                configHeader.setKey(paramHeader.getKey());
                configHeader.setValue(paramHeader.getValue());
                configHeaders.add(configHeader);
            }
            config.setAuthHeaders(configHeaders);
        } else {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "不支持的认证类型: " + param.getAuthType());
        }

        AdpAIGatewayClient client = new AdpAIGatewayClient(config);
        try {
            String url = client.getFullUrl("/gatewayInstance/listInstances");
            String requestBody = String.format("{\"current\": %d, \"size\": %d}", page, size);
            HttpEntity<String> requestEntity = client.createRequestEntity(requestBody);

            ResponseEntity<AdpGatewayInstanceResult> response = client.getRestTemplate().exchange(
                    url, HttpMethod.POST, requestEntity, AdpGatewayInstanceResult.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                AdpGatewayInstanceResult result = response.getBody();
                if (result.getCode() == 200 && result.getData() != null) {
                    return convertToGatewayResult(result.getData(), page, size);
                }
                String msg = result.getMessage() != null ? result.getMessage() : result.getMsg();
                throw new BusinessException(ErrorCode.GATEWAY_ERROR, msg);
            }
            throw new BusinessException(ErrorCode.GATEWAY_ERROR, "Failed to call ADP gateway API");
        } catch (Exception e) {
            log.error("Error fetching ADP gateways", e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, e.getMessage());
        } finally {
            client.close();
        }
    }

    private PageResult<GatewayResult> convertToGatewayResult(AdpGatewayInstanceResult.AdpGatewayInstanceData data, int page, int size) {
        List<GatewayResult> gateways = new ArrayList<>();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        if (data.getRecords() != null) {
            for (AdpGatewayInstanceResult.AdpGatewayInstance instance : data.getRecords()) {
                LocalDateTime createTime = null;
                try {
                    if (instance.getCreateTime() != null) {
                        createTime = LocalDateTime.parse(instance.getCreateTime(), formatter);
                    }
                } catch (Exception e) {
                    log.warn("Failed to parse create time: {}", instance.getCreateTime(), e);
                }
                GatewayResult gateway = GatewayResult.builder()
                        .gatewayId(instance.getGwInstanceId())
                        .gatewayName(instance.getName())
                        .gatewayType(GatewayType.ADP_AI_GATEWAY)
                        .createAt(createTime)
                        .build();
                gateways.add(gateway);
            }
        }
        return PageResult.of(gateways, page, size, data.getTotal() != null ? data.getTotal() : 0);
    }

    @Data
    public static class AdpMcpServerDetailResult {
        private Integer code;
        private String msg;
        private String message;
        private AdpMcpServerDetail data;

        @Data
        public static class AdpMcpServerDetail {
            private String gwInstanceId;
            private String name;
            private String description;
            private List<String> domains;
            private List<Service> services;
            private ConsumerAuthInfo consumerAuthInfo;
            private String rawConfigurations;
            private String type;
            private String dsn;
            private String dbType;
            private String upstreamPathPrefix;

            @Data
            public static class Service {
                private String name;
                private Integer port;
                private String version;
                private Integer weight;
            }

            @Data
            public static class ConsumerAuthInfo {
                private String type;
                private Boolean enable;
                private List<String> allowedConsumers;
            }
        }
    }
}
