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

package com.alibaba.apiopenplatform.service.impl;

import com.alibaba.apiopenplatform.core.constant.Resources;
import com.alibaba.apiopenplatform.core.exception.BusinessException;
import com.alibaba.apiopenplatform.core.exception.ErrorCode;
import com.alibaba.apiopenplatform.core.security.ContextHolder;
import com.alibaba.apiopenplatform.core.utils.IdGenerator;
import com.alibaba.apiopenplatform.dto.params.nacos.CreateNacosParam;
import com.alibaba.apiopenplatform.dto.params.nacos.QueryNacosParam;
import com.alibaba.apiopenplatform.dto.params.nacos.UpdateNacosParam;
import com.alibaba.apiopenplatform.dto.result.NacosMCPServerResult;
import com.alibaba.apiopenplatform.dto.result.NacosNamespaceResult;
import com.alibaba.apiopenplatform.dto.result.NacosResult;
import com.alibaba.apiopenplatform.dto.result.PageResult;
import com.alibaba.apiopenplatform.dto.result.MCPConfigResult;
import com.alibaba.apiopenplatform.dto.result.MseNacosResult;
import com.alibaba.apiopenplatform.entity.NacosInstance;
import com.alibaba.apiopenplatform.repository.NacosInstanceRepository;
import com.alibaba.apiopenplatform.service.NacosService;
import com.alibaba.apiopenplatform.support.enums.SourceType;
import com.alibaba.apiopenplatform.support.product.NacosRefConfig;
import com.alibaba.apiopenplatform.dto.converter.NacosToGatewayToolsConverter;
import cn.hutool.json.JSONUtil;
import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.ai.model.mcp.McpServerBasicInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import com.alibaba.nacos.maintainer.client.ai.AiMaintainerFactory;
import com.alibaba.nacos.maintainer.client.ai.McpMaintainerService;
import com.alibaba.nacos.maintainer.client.naming.NamingMaintainerFactory;
import com.alibaba.nacos.maintainer.client.naming.NamingMaintainerService;
import com.alibaba.nacos.api.exception.NacosException;
import com.aliyun.mse20190531.Client;
import com.aliyun.mse20190531.models.ListClustersRequest;
import com.aliyun.mse20190531.models.ListClustersResponse;
import com.aliyun.mse20190531.models.ListClustersResponseBody;
import com.aliyun.teautil.models.RuntimeOptions;
import com.alibaba.nacos.api.ai.model.mcp.McpServerDetailInfo;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class NacosServiceImpl implements NacosService {

    private static final String DEFAULT_CONTEXT_PATH = "nacos";

    private final NacosInstanceRepository nacosInstanceRepository;

    private final ContextHolder contextHolder;

    @Override
    public PageResult<NacosResult> listNacosInstances(Pageable pageable) {
        Page<NacosInstance> nacosInstances = nacosInstanceRepository.findAll(pageable);
        return new PageResult<NacosResult>().convertFrom(nacosInstances, nacosInstance -> new NacosResult().convertFrom(nacosInstance));
    }

    @Override
    public NacosResult getNacosInstance(String nacosId) {
        NacosInstance nacosInstance = findNacosInstance(nacosId);
        return new NacosResult().convertFrom(nacosInstance);
    }

    @Override
    public void createNacosInstance(CreateNacosParam param) {
        nacosInstanceRepository.findByNacosName(param.getNacosName())
                .ifPresent(nacos -> {
                    throw new BusinessException(ErrorCode.RESOURCE_EXIST, Resources.NACOS_INSTANCE, nacos.getNacosName());
                });

        NacosInstance nacosInstance = param.convertTo();

        // If client provided nacosId use it after checking uniqueness, otherwise generate one
        String providedId = param.getNacosId();
        if (providedId != null && !providedId.trim().isEmpty()) {
            // ensure not already exist
            boolean exists = nacosInstanceRepository.findByNacosId(providedId).isPresent();
            if (exists) {
                throw new BusinessException(ErrorCode.RESOURCE_EXIST, Resources.NACOS_INSTANCE, providedId);
            }
            nacosInstance.setNacosId(providedId);
        } else {
            nacosInstance.setNacosId(IdGenerator.genNacosId());
        }

        nacosInstance.setAdminId(contextHolder.getUser());

        nacosInstanceRepository.save(nacosInstance);
    }

    @Override
    public void updateNacosInstance(String nacosId, UpdateNacosParam param) {
        NacosInstance instance = findNacosInstance(nacosId);

        Optional.ofNullable(param.getNacosName())
                .filter(name -> !name.equals(instance.getNacosName()))
                .flatMap(nacosInstanceRepository::findByNacosName)
                .ifPresent(nacos -> {
                    throw new BusinessException(ErrorCode.RESOURCE_EXIST, Resources.NACOS_INSTANCE, nacos.getNacosName());
                });

        param.update(instance);
        nacosInstanceRepository.saveAndFlush(instance);
    }

    @Override
    public void deleteNacosInstance(String nacosId) {
        NacosInstance nacosInstance = findNacosInstance(nacosId);
        nacosInstanceRepository.delete(nacosInstance);
    }

    @Override
    public PageResult<MseNacosResult> fetchNacos(QueryNacosParam param, Pageable pageable) {
        try {
            // 创建MSE客户端
            Client client = new Client(param.toClientConfig());
            
            // 构建请求
            ListClustersRequest request = new ListClustersRequest()
                            .setRegionId(param.getRegionId())
                            .setPageNum(pageable.getPageNumber() + 1)
                            .setPageSize(pageable.getPageSize());
            
            RuntimeOptions runtime = new RuntimeOptions();
            
            // 调用MSE API获取集群列表
            ListClustersResponse response =
                    client.listClustersWithOptions(request, runtime);
            
            // 转换响应结果，并过滤掉 clusterType 为 "Nacos-Ans" 的实例
             Optional<List<MseNacosResult>> nacosResults = Optional.ofNullable(response.getBody())
                    .map(ListClustersResponseBody::getData)
                    .map(clusters -> clusters.stream()
                            .filter(cluster -> {
                                String type = cluster.getClusterType();
                                return (type == null || "Nacos-Ans".equalsIgnoreCase(type))
                                    && cluster.getVersionCode().startsWith("NACOS_3");
                            })
                            .map(MseNacosResult::fromListClustersResponseBodyData)
                            .collect(Collectors.toList())
                    );

            if (nacosResults.isPresent()) {
                    // 返回分页结果
                int total = response.getBody() != null && response.getBody().getTotalCount() != null ? 
                        response.getBody().getTotalCount().intValue() : 0;
                return PageResult.of(nacosResults.get(), pageable.getPageNumber(), pageable.getPageSize(), total);
            }
            return PageResult.empty(pageable.getPageNumber(), pageable.getPageSize());
        } catch (Exception e) {
            log.error("Error fetching Nacos clusters from MSE", e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Failed to fetch Nacos clusters from MSE: " + e.getMessage());
        }
    }

    @Override
    public PageResult<NacosMCPServerResult> fetchMcpServers(String nacosId, String namespaceId, Pageable pageable) throws Exception {
        NacosInstance nacosInstance = findNacosInstance(nacosId);
        McpMaintainerService service = buildDynamicMcpService(nacosInstance);
        String ns = namespaceId == null ? "" : namespaceId;
        com.alibaba.nacos.api.model.Page<McpServerBasicInfo> page = service.listMcpServer(ns, "", 1, Integer.MAX_VALUE);
        if (page == null || page.getPageItems() == null) {
            return PageResult.empty(pageable.getPageNumber(), pageable.getPageSize());
        }
        return page.getPageItems().stream()
                .map(basicInfo -> new NacosMCPServerResult().convertFrom(basicInfo))
                .skip(pageable.getOffset())
                .limit(pageable.getPageSize())
                .collect(Collectors.collectingAndThen(
                        Collectors.toList(),
                        list -> PageResult.of(list, pageable.getPageNumber(), pageable.getPageSize(), page.getPageItems().size())
                ));
    }

    @Override
    public PageResult<NacosNamespaceResult> fetchNamespaces(String nacosId, Pageable pageable) throws Exception {
        NacosInstance nacosInstance = findNacosInstance(nacosId);
        // 使用空 namespace 构建 (列出全部命名空间)
        NamingMaintainerService namingService = buildDynamicNamingService(nacosInstance, "");
        List<?> namespaces;
        try {
            namespaces = namingService.getNamespaceList();
        } catch (NacosException e) {
            log.error("Error fetching namespaces from Nacos by nacosId {}", nacosId, e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Failed to fetch namespaces: " + e.getErrMsg());
        }

        if (namespaces == null || namespaces.isEmpty()) {
            return PageResult.empty(pageable.getPageNumber(), pageable.getPageSize());
        }

        List<NacosNamespaceResult> list = namespaces.stream()
                .map(o -> new NacosNamespaceResult().convertFrom(o))
                .skip(pageable.getOffset())
                .limit(pageable.getPageSize())
                .collect(Collectors.toList());

        return PageResult.of(list, pageable.getPageNumber(), pageable.getPageSize(), namespaces.size());
    }

    @Override
    public String fetchMcpConfig(String nacosId, NacosRefConfig nacosRefConfig) {
        NacosInstance nacosInstance = findNacosInstance(nacosId);

        McpMaintainerService service = buildDynamicMcpService(nacosInstance);
        try {
            McpServerDetailInfo detail = service.getMcpServerDetail(nacosRefConfig.getNamespaceId(),
                    nacosRefConfig.getMcpServerName(), null);
            if (detail == null) {
                return null;
            }

            MCPConfigResult mcpConfig = buildMCPConfigResult(detail);
            return JSONUtil.toJsonStr(mcpConfig);
        } catch (Exception e) {
            log.error("Error fetching Nacos MCP servers", e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Failed to fetch Nacos MCP config");
        }
    }

    private MCPConfigResult buildMCPConfigResult(McpServerDetailInfo detail) {
        MCPConfigResult mcpConfig = new MCPConfigResult();
        mcpConfig.setMcpServerName(detail.getName());

        MCPConfigResult.MCPServerConfig serverConfig = new MCPConfigResult.MCPServerConfig();
        
        if (detail.getLocalServerConfig() != null) {
            serverConfig.setRawConfig(detail.getLocalServerConfig());
            serverConfig.setTransportMode(MCPConfigResult.MCPTransportMode.LOCAL);
        } else if (detail.getRemoteServerConfig() != null || (detail.getBackendEndpoints() != null && !detail.getBackendEndpoints().isEmpty())) {
            Object remoteConfig = buildRemoteConnectionConfig(detail);
            serverConfig.setRawConfig(remoteConfig);
        } else {
            Map<String, Object> defaultConfig = new HashMap<>();
            defaultConfig.put("type", "unknown");
            defaultConfig.put("name", detail.getName());
            serverConfig.setRawConfig(defaultConfig);
        }
        
        mcpConfig.setMcpServerConfig(serverConfig);

        if (detail.getToolSpec() != null) {
            try {
                NacosToGatewayToolsConverter converter = new NacosToGatewayToolsConverter();
                converter.convertFromNacos(detail);
                String gatewayFormatYaml = converter.toYaml();
                mcpConfig.setTools(gatewayFormatYaml);
            } catch (Exception e) {
                log.error("Error converting tools to gateway format", e);
                mcpConfig.setTools(null);
            }
        } else {
            mcpConfig.setTools(null);
        }

        MCPConfigResult.McpMetadata meta = new MCPConfigResult.McpMetadata();
        meta.setSource(SourceType.NACOS.name());
        mcpConfig.setMeta(meta);

        return mcpConfig;
    }
    
    private Object buildRemoteConnectionConfig(McpServerDetailInfo detail) {
        List<?> backendEndpoints = detail.getBackendEndpoints();
        
        if (backendEndpoints != null && !backendEndpoints.isEmpty()) {
            Object firstEndpoint = backendEndpoints.get(0);
            
            Map<String, Object> connectionConfig = new HashMap<>();
            Map<String, Object> mcpServers = new HashMap<>();
            Map<String, Object> serverConfig = new HashMap<>();

            String endpointUrl = extractEndpointUrl(firstEndpoint);
            if (endpointUrl != null) {
                serverConfig.put("url", endpointUrl);
            }

            mcpServers.put(detail.getName(), serverConfig);
            connectionConfig.put("mcpServers", mcpServers);

            return connectionConfig;
        }

        Map<String, Object> basicConfig = new HashMap<>();
        basicConfig.put("type", "remote");
        basicConfig.put("name", detail.getName());
        basicConfig.put("protocol", "http");
        return basicConfig;
    }
    
    private String extractEndpointUrl(Object endpoint) {
        if (endpoint == null) {
            return null;
        }

        if (endpoint instanceof String) {
            return (String) endpoint;
        }

        if (endpoint instanceof Map) {
            Map<?, ?> endpointMap = (Map<?, ?>) endpoint;
            
            String url = getStringValue(endpointMap, "url");
            if (url != null) return url;

            String endpointUrl = getStringValue(endpointMap, "endpointUrl");
            if (endpointUrl != null) return endpointUrl;

            String host = getStringValue(endpointMap, "host");
            String port = getStringValue(endpointMap, "port");
            String path = getStringValue(endpointMap, "path");
            
            if (host != null) {
                StringBuilder urlBuilder = new StringBuilder();
                String protocol = getStringValue(endpointMap, "protocol");
                urlBuilder.append(protocol != null ? protocol : "http").append("://");
                urlBuilder.append(host);
                
                if (port != null && !port.isEmpty()) {
                    urlBuilder.append(":").append(port);
                }
                
                if (path != null && !path.isEmpty()) {
                    if (!path.startsWith("/")) {
                        urlBuilder.append("/");
                    }
                    urlBuilder.append(path);
                }
                
                return urlBuilder.toString();
            }
        }

        if (endpoint.getClass().getName().contains("McpEndpointInfo")) {
            return extractUrlFromMcpEndpointInfo(endpoint);
        }

        return endpoint.toString();
    }

    private String getStringValue(Map<?, ?> map, String key) {
        Object value = map.get(key);
        return value != null ? value.toString() : null;
    }

    private String extractUrlFromMcpEndpointInfo(Object endpoint) {
        String[] possibleFieldNames = {"url", "endpointUrl", "address", "host", "endpoint"};
        
        for (String fieldName : possibleFieldNames) {
            try {
                java.lang.reflect.Field field = endpoint.getClass().getDeclaredField(fieldName);
                field.setAccessible(true);
                Object value = field.get(endpoint);
                if (value != null && !value.toString().trim().isEmpty()) {
                    if (value.toString().contains("://") || value.toString().contains(":")) {
                        return value.toString();
                    }
                }
            } catch (Exception e) {
                continue;
            }
        }
        
        java.lang.reflect.Field[] fields = endpoint.getClass().getDeclaredFields();
        
        String host = null;
        String port = null;
        String path = null;
        String protocol = null;
        
        for (java.lang.reflect.Field field : fields) {
            try {
                field.setAccessible(true);
                Object value = field.get(endpoint);
                if (value != null && !value.toString().trim().isEmpty()) {
                    String fieldName = field.getName().toLowerCase();
                    
                    if (fieldName.contains("host") || fieldName.contains("ip") || fieldName.contains("address")) {
                        host = value.toString();
                    } else if (fieldName.contains("port")) {
                        port = value.toString();
                    } else if (fieldName.contains("path") || fieldName.contains("endpoint") || fieldName.contains("uri")) {
                        path = value.toString();
                    } else if (fieldName.contains("protocol") || fieldName.contains("scheme")) {
                        protocol = value.toString();
                    }
                }
            } catch (Exception e) {
                continue;
            }
        }
        
        if (host != null) {
            StringBuilder urlBuilder = new StringBuilder();
            urlBuilder.append(protocol != null ? protocol : "http").append("://");
            urlBuilder.append(host);
            
            if (port != null && !port.isEmpty()) {
                urlBuilder.append(":").append(port);
            }
            
            if (path != null && !path.isEmpty()) {
                if (!path.startsWith("/")) {
                    urlBuilder.append("/");
                }
                urlBuilder.append(path);
            }
            
            return urlBuilder.toString();
        }
        
        return endpoint.toString();
    }

    private NacosInstance findNacosInstance(String nacosId) {
        return nacosInstanceRepository.findByNacosId(nacosId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, Resources.NACOS_INSTANCE, nacosId));
    }

    private McpMaintainerService buildDynamicMcpService(NacosInstance nacosInstance) {
        Properties properties = new Properties();
        properties.setProperty(PropertyKeyConst.SERVER_ADDR, nacosInstance.getServerUrl());
        if (Objects.nonNull(nacosInstance.getUsername())) {
            properties.setProperty(PropertyKeyConst.USERNAME, nacosInstance.getUsername());
        }

        if (Objects.nonNull(nacosInstance.getPassword())) {
            properties.setProperty(PropertyKeyConst.PASSWORD, nacosInstance.getPassword());
        }
        properties.setProperty(PropertyKeyConst.CONTEXT_PATH, DEFAULT_CONTEXT_PATH);
    // instance no longer stores namespace; leave namespace empty to let requests use default/public
    // if consumers need a specific namespace, they should call an overload that accepts it
        if (Objects.nonNull(nacosInstance.getAccessKey())) {
            properties.setProperty(PropertyKeyConst.ACCESS_KEY, nacosInstance.getAccessKey());
        }

        if (Objects.nonNull(nacosInstance.getSecretKey())) {
            properties.setProperty(PropertyKeyConst.SECRET_KEY, nacosInstance.getSecretKey());
        }

        try {
            return AiMaintainerFactory.createAiMaintainerService(properties);
        } catch (Exception e) {
            log.error("Error init Nacos AiMaintainerService", e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Error init Nacos AiMaintainerService");
        }
    }

    // removed unused no-namespace overload; use the runtime-namespace overload instead

    // overload to build NamingMaintainerService with a runtime namespace value
    private NamingMaintainerService buildDynamicNamingService(NacosInstance nacosInstance, String runtimeNamespace) {
        Properties properties = new Properties();
        properties.setProperty(PropertyKeyConst.SERVER_ADDR, nacosInstance.getServerUrl());
        if (Objects.nonNull(nacosInstance.getUsername())) {
            properties.setProperty(PropertyKeyConst.USERNAME, nacosInstance.getUsername());
        }

        if (Objects.nonNull(nacosInstance.getPassword())) {
            properties.setProperty(PropertyKeyConst.PASSWORD, nacosInstance.getPassword());
        }
        properties.setProperty(PropertyKeyConst.CONTEXT_PATH, DEFAULT_CONTEXT_PATH);
        properties.setProperty(PropertyKeyConst.NAMESPACE, runtimeNamespace == null ? "" : runtimeNamespace);

        if (Objects.nonNull(nacosInstance.getAccessKey())) {
            properties.setProperty(PropertyKeyConst.ACCESS_KEY, nacosInstance.getAccessKey());
        }

        if (Objects.nonNull(nacosInstance.getSecretKey())) {
            properties.setProperty(PropertyKeyConst.SECRET_KEY, nacosInstance.getSecretKey());
        }

        try {
            return NamingMaintainerFactory.createNamingMaintainerService(properties);
        } catch (Exception e) {
            log.error("Error init Nacos NamingMaintainerService", e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Error init Nacos NamingMaintainerService");
        }
    }
}