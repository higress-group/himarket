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

package com.alibaba.himarket.service.impl;

import com.alibaba.himarket.core.constant.Resources;
import com.alibaba.himarket.core.exception.BusinessException;
import com.alibaba.himarket.core.exception.ErrorCode;
import com.alibaba.himarket.core.security.ContextHolder;
import com.alibaba.himarket.core.utils.IdGenerator;
import com.alibaba.himarket.dto.converter.NacosAgentConverter;
import com.alibaba.himarket.dto.converter.NacosToGatewayToolsConverter;
import com.alibaba.himarket.dto.params.nacos.CreateNacosParam;
import com.alibaba.himarket.dto.params.nacos.QueryNacosParam;
import com.alibaba.himarket.dto.params.nacos.UpdateNacosParam;
import com.alibaba.himarket.dto.result.agent.AgentConfigResult;
import com.alibaba.himarket.dto.result.agent.NacosAgentResult;
import com.alibaba.himarket.dto.result.common.PageResult;
import com.alibaba.himarket.dto.result.mcp.McpConfigResult;
import com.alibaba.himarket.dto.result.mcp.NacosMCPServerResult;
import com.alibaba.himarket.dto.result.nacos.MseNacosResult;
import com.alibaba.himarket.dto.result.nacos.NacosNamespaceResult;
import com.alibaba.himarket.dto.result.nacos.NacosResult;
import com.alibaba.himarket.dto.result.nacos.NacosSkillResult;
import com.alibaba.himarket.entity.NacosInstance;
import com.alibaba.himarket.repository.NacosInstanceRepository;
import com.alibaba.himarket.service.NacosService;
import com.alibaba.himarket.support.common.Strings;
import com.alibaba.himarket.support.enums.SourceType;
import com.alibaba.himarket.support.product.NacosRefConfig;
import com.alibaba.himarket.utils.JsonUtil;
import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.ai.model.a2a.AgentCard;
import com.alibaba.nacos.api.ai.model.a2a.AgentCardVersionInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpServerBasicInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpServerDetailInfo;
import com.alibaba.nacos.api.ai.model.skills.SkillSummary;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.response.Namespace;
import com.alibaba.nacos.maintainer.client.ai.AiMaintainerFactory;
import com.alibaba.nacos.maintainer.client.ai.AiMaintainerService;
import com.alibaba.nacos.maintainer.client.ai.McpMaintainerService;
import com.alibaba.nacos.maintainer.client.naming.NamingMaintainerFactory;
import com.alibaba.nacos.maintainer.client.naming.NamingMaintainerService;
import com.alibaba.nacos.maintainer.client.utils.ParamUtil;
import com.aliyun.mse20190531.Client;
import com.aliyun.mse20190531.models.ListClustersRequest;
import com.aliyun.mse20190531.models.ListClustersResponse;
import com.aliyun.mse20190531.models.ListClustersResponseBody;
import com.aliyun.teautil.models.RuntimeOptions;
import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.DigestUtils;

@Service
@Slf4j
@RequiredArgsConstructor
public class NacosServiceImpl implements NacosService {

    private static final String DEFAULT_CONTEXT_PATH = "nacos";

    private final NacosInstanceRepository nacosInstanceRepository;

    private final ContextHolder contextHolder;

    private final NacosAgentConverter nacosAgentConverter;

    // Cache AiMaintainerService instances by connection properties.
    private final Map<String, AiMaintainerService> aiServiceCache = new ConcurrentHashMap<>();

    @PostConstruct
    void initNacosClientTimeout() {
        ParamUtil.setReadTimeout(90_000);
        log.info("Nacos maintainer-client read timeout set to 90s");
    }

    @Override
    public PageResult<NacosResult> listNacosInstances(Pageable pageable) {
        Page<NacosInstance> nacosInstances = nacosInstanceRepository.findAll(pageable);
        return new PageResult<NacosResult>()
                .convertFrom(
                        nacosInstances,
                        nacosInstance -> new NacosResult().convertFrom(nacosInstance));
    }

    @Override
    public NacosResult getNacosInstance(String nacosId) {
        NacosInstance nacosInstance = findNacosInstance(nacosId);
        return new NacosResult().convertFrom(nacosInstance);
    }

    @Override
    public void createNacosInstance(CreateNacosParam param) {
        nacosInstanceRepository
                .findByNacosName(param.getNacosName())
                .ifPresent(
                        nacos -> {
                            throw new BusinessException(
                                    ErrorCode.CONFLICT,
                                    String.format(
                                            "%s already exists, name=%s",
                                            Resources.NACOS_INSTANCE, param.getNacosName()));
                        });

        NacosInstance nacosInstance = param.convertTo();

        // If client provided nacosId use it after checking uniqueness, otherwise generate one
        String providedId = param.getNacosId();
        if (providedId != null && !providedId.trim().isEmpty()) {
            // ensure not already exist
            boolean exists = nacosInstanceRepository.findByNacosId(providedId).isPresent();
            if (exists) {
                throw new BusinessException(
                        ErrorCode.CONFLICT,
                        String.format(
                                "%s already exists, nacosId=%s",
                                Resources.NACOS_INSTANCE, providedId));
            }
            nacosInstance.setNacosId(providedId);
        } else {
            nacosInstance.setNacosId(IdGenerator.genNacosId());
        }

        nacosInstance.setAdminId(contextHolder.getUser());

        // Mark the first imported instance as default.
        if (nacosInstanceRepository.findByIsDefaultTrue().isEmpty()) {
            nacosInstance.setIsDefault(true);
        }

        nacosInstanceRepository.save(nacosInstance);
    }

    @Override
    public void updateNacosInstance(String nacosId, UpdateNacosParam param) {
        NacosInstance instance = findNacosInstance(nacosId);

        String requestedName = param.getNacosName();
        if (requestedName != null && !requestedName.equals(instance.getNacosName())) {
            nacosInstanceRepository
                    .findByNacosName(requestedName)
                    .ifPresent(
                            nacos -> {
                                throw new BusinessException(
                                        ErrorCode.CONFLICT,
                                        String.format(
                                                "%s already exists, name=%s",
                                                Resources.NACOS_INSTANCE, requestedName));
                            });
        }

        param.update(instance);
        nacosInstanceRepository.saveAndFlush(instance);
    }

    @Override
    public void deleteNacosInstance(String nacosId) {
        NacosInstance nacosInstance = findNacosInstance(nacosId);
        if (Boolean.TRUE.equals(nacosInstance.getIsDefault())) {
            throw new BusinessException(
                    ErrorCode.INVALID_PARAMETER,
                    "Default Nacos instance cannot be deleted. Switch the default instance first.");
        }
        // Evict cached services for the deleted instance.
        String cacheKey = buildCacheKey(nacosInstance);
        aiServiceCache.remove(cacheKey);
        nacosInstanceRepository.delete(nacosInstance);
    }

    @Override
    public PageResult<MseNacosResult> fetchNacos(QueryNacosParam param, Pageable pageable) {
        try {
            // Create MSE client.
            Client client = new Client(param.toClientConfig());

            // Build request.
            ListClustersRequest request =
                    new ListClustersRequest()
                            .setRegionId(param.getRegionId())
                            .setPageNum(pageable.getPageNumber() + 1)
                            .setPageSize(pageable.getPageSize());

            RuntimeOptions runtime = new RuntimeOptions();

            // Fetch cluster list from MSE.
            ListClustersResponse response = client.listClustersWithOptions(request, runtime);
            ListClustersResponseBody body = response.getBody();
            if (body == null || body.getData() == null) {
                return PageResult.empty(pageable.getPageNumber(), pageable.getPageSize());
            }

            // Convert response and filter Nacos 3 clusters.
            List<MseNacosResult> nacosResults =
                    body.getData().stream()
                            .filter(
                                    cluster -> {
                                        String type = cluster.getClusterType();
                                        return (type == null || "Nacos-Ans".equalsIgnoreCase(type))
                                                && cluster.getVersionCode().startsWith("NACOS_3");
                                    })
                            .map(MseNacosResult::fromListClustersResponseBodyData)
                            .toList();

            int total = body.getTotalCount() == null ? 0 : body.getTotalCount().intValue();
            return PageResult.of(
                    nacosResults, pageable.getPageNumber(), pageable.getPageSize(), total);
        } catch (Exception e) {
            log.error(
                    "Failed to fetch Nacos clusters, dependency=MSE, operation=listClusters,"
                            + " errorMessage={}",
                    e.getMessage(),
                    e);
            throw new BusinessException(
                    ErrorCode.INTERNAL_ERROR,
                    String.format("Failed to fetch Nacos clusters from MSE: %s", e.getMessage()));
        }
    }

    @Override
    public PageResult<NacosMCPServerResult> fetchMcpServers(
            String nacosId, String namespaceId, Pageable pageable) throws Exception {
        NacosInstance nacosInstance = findNacosInstance(nacosId);
        McpMaintainerService service = buildDynamicAiService(nacosInstance);
        String ns = namespaceId == null ? "" : namespaceId;
        com.alibaba.nacos.api.model.Page<McpServerBasicInfo> page =
                service.listMcpServer(ns, "", 1, Integer.MAX_VALUE);
        if (page == null || page.getPageItems() == null) {
            return PageResult.empty(pageable.getPageNumber(), pageable.getPageSize());
        }
        List<NacosMCPServerResult> results =
                page.getPageItems().stream()
                        .map(basicInfo -> new NacosMCPServerResult().convertFrom(basicInfo))
                        .skip(pageable.getOffset())
                        .limit(pageable.getPageSize())
                        .toList();
        return PageResult.of(
                results,
                pageable.getPageNumber(),
                pageable.getPageSize(),
                page.getPageItems().size());
    }

    @Override
    public PageResult<NacosNamespaceResult> fetchNamespaces(String nacosId, Pageable pageable)
            throws Exception {
        NacosInstance nacosInstance = findNacosInstance(nacosId);
        // Use an empty namespace to list all namespaces.
        NamingMaintainerService namingService = buildDynamicNamingService(nacosInstance, "");
        List<?> namespaces;
        try {
            namespaces = namingService.getNamespaceList();
        } catch (NacosException e) {
            log.error(
                    "Failed to fetch Nacos namespaces, dependency=Nacos, operation=listNamespaces,"
                            + " nacosId={}",
                    nacosId,
                    e);
            throw new BusinessException(
                    ErrorCode.INTERNAL_ERROR,
                    String.format("Failed to fetch namespaces: %s", e.getErrMsg()));
        }

        if (namespaces == null || namespaces.isEmpty()) {
            return PageResult.empty(pageable.getPageNumber(), pageable.getPageSize());
        }

        List<NacosNamespaceResult> list =
                namespaces.stream()
                        .map(o -> new NacosNamespaceResult().convertFrom(o))
                        .skip(pageable.getOffset())
                        .limit(pageable.getPageSize())
                        .toList();

        return PageResult.of(
                list, pageable.getPageNumber(), pageable.getPageSize(), namespaces.size());
    }

    @Override
    public String fetchMcpConfig(String nacosId, NacosRefConfig nacosRefConfig) {
        NacosInstance nacosInstance = findNacosInstance(nacosId);

        McpMaintainerService service = buildDynamicAiService(nacosInstance);
        try {
            McpServerDetailInfo detail =
                    service.getMcpServerDetail(
                            nacosRefConfig.getNamespaceId(),
                            nacosRefConfig.getMcpServerName(),
                            null);
            if (detail == null) {
                return null;
            }

            McpConfigResult mcpConfig = buildMCPConfigResult(detail);
            return JsonUtil.toJson(mcpConfig);
        } catch (Exception e) {
            log.error(
                    "Failed to fetch Nacos MCP config, dependency=Nacos,"
                            + " operation=getMcpServerDetail, nacosId={}, namespaceId={},"
                            + " mcpServerName={}",
                    nacosId,
                    nacosRefConfig.getNamespaceId(),
                    nacosRefConfig.getMcpServerName(),
                    e);
            throw new BusinessException(
                    ErrorCode.INTERNAL_ERROR, "Failed to fetch Nacos MCP config");
        }
    }

    private McpConfigResult buildMCPConfigResult(McpServerDetailInfo detail) {
        McpConfigResult mcpConfig = new McpConfigResult();
        mcpConfig.setMcpServerName(detail.getName());

        McpConfigResult.McpServerConfig serverConfig = new McpConfigResult.McpServerConfig();

        if (detail.getLocalServerConfig() != null) {
            serverConfig.setRawConfig(detail.getLocalServerConfig());
        } else if (detail.getRemoteServerConfig() != null
                || (detail.getBackendEndpoints() != null
                        && !detail.getBackendEndpoints().isEmpty())) {
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
                log.error(
                        "Failed to convert Nacos tools to gateway format,"
                                + " operation=convertToolsConfig, mcpServerName={}",
                        detail.getName(),
                        e);
                mcpConfig.setTools(null);
            }
        } else {
            mcpConfig.setTools(null);
        }

        McpConfigResult.McpMetadata meta = new McpConfigResult.McpMetadata();
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

                    if (fieldName.contains("host")
                            || fieldName.contains("ip")
                            || fieldName.contains("address")) {
                        host = value.toString();
                    } else if (fieldName.contains("port")) {
                        port = value.toString();
                    } else if (fieldName.contains("path")
                            || fieldName.contains("endpoint")
                            || fieldName.contains("uri")) {
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
        return nacosInstanceRepository
                .findByNacosId(nacosId)
                .orElseThrow(
                        () ->
                                new BusinessException(
                                        ErrorCode.NOT_FOUND, Resources.NACOS_INSTANCE, nacosId));
    }

    private AiMaintainerService buildDynamicAiService(NacosInstance nacosInstance) {
        String cacheKey = buildCacheKey(nacosInstance);
        AiMaintainerService cachedService = aiServiceCache.get(cacheKey);
        if (cachedService != null) {
            return cachedService;
        }

        Properties properties = buildMaintainerProperties(nacosInstance);

        try {
            AiMaintainerService service = AiMaintainerFactory.createAiMaintainerService(properties);
            aiServiceCache.put(cacheKey, service);
            return service;
        } catch (Exception e) {
            log.error(
                    "Failed to initialize Nacos AI maintainer service, dependency=Nacos,"
                            + " operation=createAiMaintainerService, nacosId={}",
                    nacosInstance.getNacosId(),
                    e);
            throw new BusinessException(
                    ErrorCode.INTERNAL_ERROR, "Failed to initialize Nacos AI maintainer service");
        }
    }

    private Properties buildMaintainerProperties(NacosInstance nacosInstance) {
        Properties properties = new Properties();
        properties.setProperty(PropertyKeyConst.SERVER_ADDR, nacosInstance.getServerUrl());
        if (Objects.nonNull(nacosInstance.getUsername())) {
            properties.setProperty(PropertyKeyConst.USERNAME, nacosInstance.getUsername());
        }

        if (Objects.nonNull(nacosInstance.getPassword())) {
            properties.setProperty(PropertyKeyConst.PASSWORD, nacosInstance.getPassword());
        }
        properties.setProperty(PropertyKeyConst.CONTEXT_PATH, DEFAULT_CONTEXT_PATH);
        // Instance records no longer store a namespace; request-level callers pass one explicitly.
        if (Objects.nonNull(nacosInstance.getAccessKey())) {
            properties.setProperty(PropertyKeyConst.ACCESS_KEY, nacosInstance.getAccessKey());
        }

        if (Objects.nonNull(nacosInstance.getSecretKey())) {
            properties.setProperty(PropertyKeyConst.SECRET_KEY, nacosInstance.getSecretKey());
        }
        return properties;
    }

    /**
     * Builds a cache key from the connection-sensitive Nacos instance properties.
     *
     * @param nacosInstance Nacos instance
     * @return cache key
     */
    private String buildCacheKey(NacosInstance nacosInstance) {
        StringBuilder keyBuilder = new StringBuilder();
        keyBuilder.append(nacosInstance.getServerUrl());

        if (Objects.nonNull(nacosInstance.getUsername())) {
            keyBuilder.append("|").append(nacosInstance.getUsername());
        }

        if (Objects.nonNull(nacosInstance.getPassword())) {
            keyBuilder.append("|").append(nacosInstance.getPassword());
        }

        if (Objects.nonNull(nacosInstance.getAccessKey())) {
            keyBuilder.append("|").append(nacosInstance.getAccessKey());
        }

        if (Objects.nonNull(nacosInstance.getSecretKey())) {
            keyBuilder.append("|").append(nacosInstance.getSecretKey());
        }

        return DigestUtils.md5DigestAsHex(keyBuilder.toString().getBytes(StandardCharsets.UTF_8));
    }

    // Build a NamingMaintainerService for the namespace used by the current request.
    private NamingMaintainerService buildDynamicNamingService(
            NacosInstance nacosInstance, String runtimeNamespace) {
        Properties properties = new Properties();
        properties.setProperty(PropertyKeyConst.SERVER_ADDR, nacosInstance.getServerUrl());
        if (Objects.nonNull(nacosInstance.getUsername())) {
            properties.setProperty(PropertyKeyConst.USERNAME, nacosInstance.getUsername());
        }

        if (Objects.nonNull(nacosInstance.getPassword())) {
            properties.setProperty(PropertyKeyConst.PASSWORD, nacosInstance.getPassword());
        }
        properties.setProperty(PropertyKeyConst.CONTEXT_PATH, DEFAULT_CONTEXT_PATH);
        properties.setProperty(
                PropertyKeyConst.NAMESPACE, runtimeNamespace == null ? "" : runtimeNamespace);

        if (Objects.nonNull(nacosInstance.getAccessKey())) {
            properties.setProperty(PropertyKeyConst.ACCESS_KEY, nacosInstance.getAccessKey());
        }

        if (Objects.nonNull(nacosInstance.getSecretKey())) {
            properties.setProperty(PropertyKeyConst.SECRET_KEY, nacosInstance.getSecretKey());
        }

        try {
            return NamingMaintainerFactory.createNamingMaintainerService(properties);
        } catch (Exception e) {
            log.error(
                    "Failed to initialize Nacos naming maintainer service, dependency=Nacos,"
                        + " operation=createNamingMaintainerService, nacosId={}, namespaceId={}",
                    nacosInstance.getNacosId(),
                    runtimeNamespace,
                    e);
            throw new BusinessException(
                    ErrorCode.INTERNAL_ERROR,
                    "Failed to initialize Nacos naming maintainer service");
        }
    }

    @Override
    public PageResult<NacosAgentResult> fetchAgents(
            String nacosId, String namespaceId, Pageable pageable) throws Exception {

        NacosInstance nacosInstance = findNacosInstance(nacosId);
        AiMaintainerService aiService = buildDynamicAiService(nacosInstance);
        String ns = Strings.isBlank(namespaceId) ? "" : namespaceId;

        com.alibaba.nacos.api.model.Page<AgentCardVersionInfo> agentPage;
        try {
            // Nacos SDK uses 1-based page numbers.
            int pageNo = pageable.getPageNumber() + 1;
            int pageSize = pageable.getPageSize();

            agentPage = aiService.listAgentCards(ns, pageNo, pageSize);
        } catch (NacosException e) {
            log.error(
                    "Failed to fetch Nacos agents, dependency=Nacos, operation=listAgentCards,"
                            + " nacosId={}, namespaceId={}",
                    nacosId,
                    namespaceId,
                    e);
            throw new BusinessException(
                    ErrorCode.INTERNAL_ERROR,
                    String.format("Failed to fetch agents: %s", e.getErrMsg()));
        }

        if (agentPage == null
                || agentPage.getPageItems() == null
                || agentPage.getPageItems().isEmpty()) {
            return PageResult.empty(pageable.getPageNumber(), pageable.getPageSize());
        }

        List<NacosAgentResult> agentResults =
                nacosAgentConverter.convertToAgentResults(agentPage.getPageItems(), ns);

        return PageResult.of(
                agentResults,
                pageable.getPageNumber(),
                pageable.getPageSize(),
                agentPage.getTotalCount());
    }

    @Override
    public PageResult<NacosSkillResult> fetchSkills(
            String nacosId, String namespaceId, Pageable pageable) throws Exception {
        AiMaintainerService service = getAiMaintainerService(nacosId);
        com.alibaba.nacos.api.model.Page<SkillSummary> page =
                service.skill()
                        .listSkills(
                                Strings.blankToDefault(namespaceId, "public"),
                                null,
                                null,
                                1,
                                Integer.MAX_VALUE);
        if (page == null || page.getPageItems() == null) {
            return PageResult.empty(pageable.getPageNumber(), pageable.getPageSize());
        }
        List<NacosSkillResult> results =
                page.getPageItems().stream()
                        .map(
                                skill ->
                                        NacosSkillResult.builder()
                                                .name(skill.getName())
                                                .description(skill.getDescription())
                                                .downloadCount(skill.getDownloadCount())
                                                .build())
                        .skip(pageable.getOffset())
                        .limit(pageable.getPageSize())
                        .toList();
        return PageResult.of(
                results,
                pageable.getPageNumber(),
                pageable.getPageSize(),
                page.getPageItems().size());
    }

    /**
     * Fetches the latest Agent detail for internal configuration synchronization.
     *
     * <p>Returning the standard {@link AgentCard} keeps the serialized config aligned with A2A and
     * avoids depending on Nacos-specific extension fields.
     */
    private AgentCard fetchAgentDetailInternal(String nacosId, String agentName, String namespaceId)
            throws Exception {

        NacosInstance nacosInstance = findNacosInstance(nacosId);
        AiMaintainerService aiService = buildDynamicAiService(nacosInstance);
        String ns = Strings.isBlank(namespaceId) ? "" : namespaceId;

        AgentCard agentCard;
        try {
            agentCard = aiService.getAgentCard(agentName, ns);
        } catch (NacosException e) {
            log.error(
                    "Failed to fetch Nacos agent detail, dependency=Nacos, operation=getAgentCard,"
                            + " nacosId={}, agentName={}",
                    nacosId,
                    agentName,
                    e);
            throw new BusinessException(
                    ErrorCode.INTERNAL_ERROR,
                    String.format("Failed to fetch agent detail: %s", e.getErrMsg()));
        }

        if (agentCard == null) {
            throw new BusinessException(
                    ErrorCode.NOT_FOUND, String.format("Agent not found: %s", agentName));
        }

        return agentCard;
    }

    @Override
    public String fetchAgentConfig(String nacosId, NacosRefConfig nacosRefConfig) {
        if (Strings.isBlank(nacosRefConfig.getAgentName())) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Agent name is required");
        }

        try {
            AgentCard agentCard =
                    fetchAgentDetailInternal(
                            nacosId,
                            nacosRefConfig.getAgentName(),
                            nacosRefConfig.getNamespaceId());

            // A2A protocol does not use route definitions.
            AgentConfigResult.AgentAPIConfig apiConfig =
                    AgentConfigResult.AgentAPIConfig.builder()
                            .agentProtocols(List.of("a2a"))
                            .agentCard(agentCard)
                            .routes(null)
                            .build();

            AgentConfigResult.AgentMetadata meta =
                    AgentConfigResult.AgentMetadata.builder()
                            .source(SourceType.NACOS.name())
                            .build();

            AgentConfigResult result = new AgentConfigResult();
            result.setAgentAPIConfig(apiConfig);
            result.setMeta(meta);

            return JsonUtil.toJson(result);

        } catch (Exception e) {
            log.error(
                    "Failed to fetch Nacos agent config, dependency=Nacos,"
                            + " operation=fetchAgentConfig, nacosId={}, agentName={}",
                    nacosId,
                    nacosRefConfig.getAgentName(),
                    e);
            throw new BusinessException(
                    ErrorCode.INTERNAL_ERROR,
                    String.format("Failed to fetch agent config: %s", e.getMessage()));
        }
    }

    @Override
    public AiMaintainerService getAiMaintainerService(String nacosId) {
        NacosInstance nacosInstance = findNacosInstance(nacosId);
        return buildDynamicAiService(nacosInstance);
    }

    @Override
    public NacosInstance findNacosInstanceById(String nacosId) {
        return findNacosInstance(nacosId);
    }

    @Override
    public NacosResult getDefaultNacosInstance() {
        return nacosInstanceRepository
                .findByIsDefaultTrue()
                .map(instance -> new NacosResult().convertFrom(instance))
                .orElse(null);
    }

    @Override
    @Transactional
    public void setDefaultNacos(String nacosId, String namespaceId) {
        NacosInstance newDefault = findNacosInstance(nacosId);
        // Clear the previous default before setting the new one.
        nacosInstanceRepository
                .findByIsDefaultTrue()
                .ifPresent(
                        old -> {
                            old.setIsDefault(false);
                            nacosInstanceRepository.save(old);
                        });
        newDefault.setIsDefault(true);
        nacosInstanceRepository.save(newDefault);

        // Verify the namespace before storing it as the default namespace.
        if (Strings.isNotBlank(namespaceId)) {
            NamingMaintainerService namingService = buildDynamicNamingService(newDefault, "");
            try {
                List<Namespace> namespaces = namingService.getNamespaceList();
                boolean exists =
                        namespaces != null
                                && namespaces.stream()
                                        .anyMatch(
                                                ns ->
                                                        Strings.equals(
                                                                        namespaceId,
                                                                        ns.getNamespace())
                                                                || (Strings.isBlank(
                                                                                ns.getNamespace())
                                                                        && Strings.equalsIgnoreCase(
                                                                                namespaceId,
                                                                                "public")));
                if (!exists) {
                    throw new BusinessException(
                            ErrorCode.NOT_FOUND, Resources.NACOS_NAMESPACE, namespaceId);
                }
            } catch (NacosException e) {
                log.error(
                        "Error verifying namespace from Nacos, nacosId={}, namespaceId={}",
                        nacosId,
                        namespaceId,
                        e);
                throw new BusinessException(
                        ErrorCode.INTERNAL_ERROR,
                        String.format("Failed to verify namespace from Nacos: %s", e.getErrMsg()));
            }

            newDefault.setDefaultNamespace(namespaceId);
            nacosInstanceRepository.save(newDefault);
        }
    }
}
