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

package com.alibaba.himarket.service.mcp;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.alibaba.himarket.core.exception.BusinessException;
import com.alibaba.himarket.core.exception.ErrorCode;
import com.alibaba.himarket.core.utils.IdGenerator;
import com.alibaba.himarket.dto.params.mcp.SaveMcpMetaParam;
import com.alibaba.himarket.dto.result.mcp.McpMetaResult;
import com.alibaba.himarket.entity.McpServerEndpoint;
import com.alibaba.himarket.entity.McpServerMeta;
import com.alibaba.himarket.entity.Product;
import com.alibaba.himarket.entity.ProductRef;
import com.alibaba.himarket.repository.McpServerEndpointRepository;
import com.alibaba.himarket.repository.McpServerMetaRepository;
import com.alibaba.himarket.repository.ProductRefRepository;
import com.alibaba.himarket.repository.ProductRepository;
import com.alibaba.himarket.service.GatewayService;
import com.alibaba.himarket.service.NacosService;
import com.alibaba.himarket.support.enums.McpEndpointStatus;
import com.alibaba.himarket.support.enums.McpHostingType;
import com.alibaba.himarket.support.enums.McpOrigin;
import com.alibaba.himarket.support.enums.McpProtocolType;
import com.alibaba.himarket.support.enums.ProductStatus;
import com.alibaba.himarket.support.enums.SourceType;
import com.alibaba.himarket.support.product.NacosRefConfig;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Helper for MCP configuration synchronization.
 *
 * <p>Extracted from McpServerServiceImpl to handle:
 * <ul>
 *   <li>ProductRef sync (create/update)</li>
 *   <li>Remote config fetch (Gateway/Nacos)</li>
 *   <li>Public endpoint sync for non-sandbox MCP</li>
 *   <li>Product display field enrichment</li>
 *   <li>Endpoint URL extraction from various connectionConfig formats</li>
 *   <li>connectionConfig format conversion</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class McpConfigSyncHelper {

    private final McpServerMetaRepository metaRepository;
    private final McpServerEndpointRepository endpointRepository;
    private final ProductRefRepository productRefRepository;
    private final ProductRepository productRepository;
    private final GatewayService gatewayService;
    private final NacosService nacosService;

    // ==================== ProductRef Sync ====================

    /**
     * Sync MCP meta to ProductRef table, making the product association visible.
     * Also fetches remote config for Gateway/Nacos sources.
     */
    public void syncProductRef(McpServerMeta meta, SaveMcpMetaParam param) {
        String productId = meta.getProductId();
        SourceType refSourceType = determineSourceType(param);

        if (refSourceType == SourceType.GATEWAY || refSourceType == SourceType.NACOS) {
            fetchAndSyncRemoteConfig(meta, param, refSourceType);
        }

        upsertProductRef(productId, param, refSourceType);
        markProductReady(productId);
    }

    SourceType determineSourceType(SaveMcpMetaParam param) {
        McpOrigin originEnum = McpOrigin.fromString(param.getOrigin());
        if (originEnum == McpOrigin.GATEWAY && StrUtil.isNotBlank(param.getGatewayId())) {
            return SourceType.GATEWAY;
        } else if (originEnum == McpOrigin.NACOS && StrUtil.isNotBlank(param.getNacosId())) {
            return SourceType.NACOS;
        }
        return SourceType.CUSTOM;
    }

    private void fetchAndSyncRemoteConfig(
            McpServerMeta meta, SaveMcpMetaParam param, SourceType sourceType) {
        String mcpConfigStr =
                sourceType == SourceType.GATEWAY
                        ? fetchGatewayConfig(param)
                        : fetchNacosConfig(param);

        if (StrUtil.isBlank(mcpConfigStr)) {
            return;
        }

        try {
            cn.hutool.json.JSONObject mcpJson = JSONUtil.parseObj(mcpConfigStr);
            String protocol = mcpJson.getByPath("meta.protocol", String.class);
            if (StrUtil.isNotBlank(protocol)) {
                meta.setProtocolType(McpProtocolUtils.normalize(protocol));
            }
            String tools = mcpJson.getStr("tools");
            if (StrUtil.isNotBlank(tools) && StrUtil.isBlank(meta.getToolsConfig())) {
                meta.setToolsConfig(McpToolsConfigParser.normalize(tools));
            }
            String standardConfig =
                    convertToStandardConnectionConfig(mcpJson, meta.getMcpName(), protocol);
            meta.setConnectionConfig(
                    StrUtil.isNotBlank(standardConfig) ? standardConfig : mcpConfigStr);
        } catch (Exception e) {
            log.warn("解析远端配置失败，保留原始格式: {}", e.getMessage());
            meta.setConnectionConfig(mcpConfigStr);
        }
        metaRepository.save(meta);
    }

    private String fetchGatewayConfig(SaveMcpMetaParam param) {
        Object refConfigObj = null;
        if (StrUtil.isNotBlank(param.getRefConfig())) {
            cn.hutool.json.JSONObject refJson = JSONUtil.parseObj(param.getRefConfig());
            String fromGatewayType = refJson.getStr("fromGatewayType");
            if ("HIGRESS".equals(fromGatewayType)) {
                refConfigObj =
                        JSONUtil.toBean(
                                param.getRefConfig(),
                                com.alibaba.himarket.support.product.HigressRefConfig.class);
            } else {
                refConfigObj =
                        JSONUtil.toBean(
                                param.getRefConfig(),
                                com.alibaba.himarket.support.product.APIGRefConfig.class);
            }
        }
        return gatewayService.fetchMcpConfig(param.getGatewayId(), refConfigObj);
    }

    private String fetchNacosConfig(SaveMcpMetaParam param) {
        NacosRefConfig nacosRef =
                StrUtil.isNotBlank(param.getRefConfig())
                        ? JSONUtil.toBean(param.getRefConfig(), NacosRefConfig.class)
                        : null;
        return nacosService.fetchMcpConfig(param.getNacosId(), nacosRef);
    }

    private void upsertProductRef(
            String productId, SaveMcpMetaParam param, SourceType refSourceType) {
        ProductRef ref = productRefRepository.findByProductId(productId).orElse(null);

        if (ref == null) {
            ref =
                    ProductRef.builder()
                            .productId(productId)
                            .sourceType(refSourceType)
                            .enabled(true)
                            .build();
        } else {
            ref.setSourceType(refSourceType);
            ref.setEnabled(true);
        }

        if (refSourceType == SourceType.GATEWAY) {
            ref.setGatewayId(param.getGatewayId());
            applyGatewayRefConfig(ref, param.getRefConfig());
        } else if (refSourceType == SourceType.NACOS) {
            ref.setNacosId(param.getNacosId());
            if (StrUtil.isNotBlank(param.getRefConfig())) {
                ref.setNacosRefConfig(JSONUtil.toBean(param.getRefConfig(), NacosRefConfig.class));
            }
        }

        productRefRepository.save(ref);
    }

    private void applyGatewayRefConfig(ProductRef ref, String refConfig) {
        if (StrUtil.isBlank(refConfig)) return;
        cn.hutool.json.JSONObject refJson = JSONUtil.parseObj(refConfig);
        String fromGatewayType = refJson.getStr("fromGatewayType");
        if ("HIGRESS".equals(fromGatewayType)) {
            ref.setHigressRefConfig(
                    JSONUtil.toBean(
                            refConfig,
                            com.alibaba.himarket.support.product.HigressRefConfig.class));
        } else if ("ADP_AI_GATEWAY".equals(fromGatewayType)) {
            ref.setAdpAIGatewayRefConfig(
                    JSONUtil.toBean(
                            refConfig, com.alibaba.himarket.support.product.APIGRefConfig.class));
        } else if ("APSARA_GATEWAY".equals(fromGatewayType)) {
            ref.setApsaraGatewayRefConfig(
                    JSONUtil.toBean(
                            refConfig, com.alibaba.himarket.support.product.APIGRefConfig.class));
        } else {
            ref.setApigRefConfig(
                    JSONUtil.toBean(
                            refConfig, com.alibaba.himarket.support.product.APIGRefConfig.class));
        }
    }

    public void deleteProductRef(String productId) {
        productRefRepository.deleteByProductId(productId);
    }

    public void markProductReady(String productId) {
        productRepository
                .findByProductId(productId)
                .ifPresent(
                        product -> {
                            if (product.getStatus() != ProductStatus.PUBLISHED) {
                                product.setStatus(ProductStatus.READY);
                                productRepository.save(product);
                            }
                        });
    }

    // ==================== Endpoint Operations ====================

    /** Find all endpoints for a given mcpServerId. */
    public List<McpServerEndpoint> findEndpointsByMcpServerId(String mcpServerId) {
        return endpointRepository.findByMcpServerId(mcpServerId);
    }

    /** Delete an endpoint and flush immediately (for unique constraint safety). */
    public void deleteEndpoint(McpServerEndpoint endpoint) {
        endpointRepository.delete(endpoint);
    }

    /** Flush pending deletes to avoid unique constraint conflicts on subsequent inserts. */
    public void flushEndpoints() {
        endpointRepository.flush();
    }

    /** Save a new endpoint (for sandbox pre-create). */
    public McpServerEndpoint saveEndpoint(McpServerEndpoint endpoint) {
        return endpointRepository.save(endpoint);
    }

    // ==================== Public Endpoint Sync ====================

    /**
     * For non-sandbox MCP: extract endpoint URL from connectionConfig and create/update
     * a public endpoint (userId=*).
     */
    public void syncPublicEndpoint(McpServerMeta meta) {
        String connectionConfig = meta.getConnectionConfig();
        if (StrUtil.isBlank(connectionConfig)) {
            return;
        }

        String endpointUrl;
        try {
            endpointUrl = extractEndpointUrlTyped(connectionConfig, meta.getMcpName());
        } catch (Exception e1) {
            try {
                cn.hutool.json.JSONObject connJson = JSONUtil.parseObj(connectionConfig);
                endpointUrl =
                        extractEndpointUrl(connJson, meta.getMcpName(), meta.getProtocolType());
            } catch (Exception e2) {
                log.debug(
                        "[syncPublicEndpoint] 无法从 connectionConfig 提取 URL，跳过:"
                                + " mcpServerId={}, error={}",
                        meta.getMcpServerId(),
                        e2.getMessage());
                return;
            }
        }

        if (StrUtil.isBlank(endpointUrl)) {
            return;
        }

        McpProtocolType protoType = McpProtocolType.fromString(meta.getProtocolType());
        boolean isStreamableHttp = protoType != null && protoType.isStreamableHttp();
        String protocol =
                isStreamableHttp
                        ? (protoType != null ? protoType.getValue() : meta.getProtocolType())
                        : McpProtocolType.SSE.getValue();

        endpointUrl = McpProtocolUtils.normalizeEndpointUrl(endpointUrl, meta.getProtocolType());

        McpOrigin metaOrigin = McpOrigin.fromString(meta.getOrigin());
        McpHostingType hostingType = McpHostingType.fromOrigin(metaOrigin);

        upsertEndpoint(
                meta.getMcpServerId(),
                meta.getMcpName(),
                endpointUrl,
                hostingType.name(),
                protocol,
                McpEndpointStatus.PUBLIC_USER_ID,
                "public",
                null,
                null);

        log.info(
                "[syncPublicEndpoint] 公共 endpoint 已同步: mcpServerId={}, protocol={}, url={}",
                meta.getMcpServerId(),
                protocol,
                endpointUrl);
    }

    /**
     * Upsert endpoint by mcpServerId + userId + hostingInstanceId unique constraint.
     */
    public McpServerEndpoint upsertEndpoint(
            String mcpServerId,
            String mcpName,
            String endpointUrl,
            String hostingType,
            String protocol,
            String userId,
            String hostingInstanceId,
            String hostingIdentifier,
            String subscribeParams) {
        McpServerEndpoint endpoint =
                endpointRepository
                        .findByMcpServerIdAndUserIdAndHostingInstanceId(
                                mcpServerId, userId, hostingInstanceId)
                        .orElse(null);

        if (endpoint == null) {
            endpoint =
                    McpServerEndpoint.builder()
                            .endpointId(IdGenerator.genEndpointId())
                            .mcpServerId(mcpServerId)
                            .mcpName(mcpName)
                            .endpointUrl(endpointUrl)
                            .hostingType(hostingType)
                            .protocol(protocol)
                            .userId(userId)
                            .hostingInstanceId(hostingInstanceId)
                            .hostingIdentifier(hostingIdentifier)
                            .subscribeParams(subscribeParams)
                            .status(McpEndpointStatus.ACTIVE.name())
                            .build();
        } else {
            endpoint.setEndpointUrl(endpointUrl);
            endpoint.setProtocol(protocol);
            endpoint.setHostingIdentifier(hostingIdentifier);
            endpoint.setSubscribeParams(subscribeParams);
            endpoint.setStatus(McpEndpointStatus.ACTIVE.name());
        }
        return endpointRepository.save(endpoint);
    }

    // ==================== Product Display Field Enrichment ====================

    public void syncDisplayFieldsToProduct(String productId, SaveMcpMetaParam param) {
        productRepository
                .findByProductId(productId)
                .ifPresent(
                        product -> {
                            boolean changed = false;
                            if (StrUtil.isNotBlank(param.getDisplayName())
                                    && !param.getDisplayName().equals(product.getName())) {
                                product.setName(param.getDisplayName());
                                changed = true;
                            }
                            if (param.getDescription() != null
                                    && !param.getDescription().equals(product.getDescription())) {
                                product.setDescription(param.getDescription());
                                changed = true;
                            }
                            if (StrUtil.isNotBlank(param.getIcon())) {
                                try {
                                    product.setIcon(
                                            JSONUtil.toBean(
                                                    param.getIcon(),
                                                    com.alibaba.himarket.support.product.Icon
                                                            .class));
                                    changed = true;
                                } catch (Exception e) {
                                    log.warn("解析 icon JSON 失败: {}", e.getMessage());
                                }
                            }
                            if (StrUtil.isNotBlank(param.getServiceIntro())
                                    && !param.getServiceIntro().equals(product.getDocument())) {
                                product.setDocument(param.getServiceIntro());
                                changed = true;
                            }
                            if (changed) {
                                productRepository.save(product);
                            }
                        });
    }

    public void enrichFromProduct(McpMetaResult result, String productId) {
        if (productId == null) return;
        productRepository.findByProductId(productId).ifPresent(p -> enrichFromProduct(result, p));
    }

    public void enrichFromProduct(McpMetaResult result, Product product) {
        if (product == null) return;
        result.setDisplayName(product.getName());
        result.setDescription(product.getDescription());
        result.setServiceIntro(product.getDocument());
        if (product.getStatus() == ProductStatus.PUBLISHED) {
            result.setPublishStatus("PUBLISHED");
            result.setVisibility("PUBLIC");
        } else {
            result.setPublishStatus(product.getStatus() == ProductStatus.READY ? "READY" : "DRAFT");
            result.setVisibility("PUBLIC");
        }
        if (product.getIcon() != null) {
            try {
                result.setIcon(JSONUtil.toJsonStr(product.getIcon()));
            } catch (Exception e) {
                // ignore
            }
        }
    }

    public McpMetaResult enrichedResult(McpServerMeta meta) {
        McpMetaResult result = new McpMetaResult().convertFrom(meta);
        enrichFromProduct(result, meta.getProductId());
        return result;
    }

    // ==================== Endpoint URL Extraction ====================

    public String extractEndpointUrlTyped(String connectionConfigJson, String mcpName)
            throws Exception {
        McpConnectionConfig cfg = McpConnectionConfig.parse(connectionConfigJson);
        if (cfg.isMcpServersFormat()) {
            for (McpConnectionConfig.McpServerEntry entry : cfg.getMcpServers().values()) {
                Object url = entry.getExtra().get("url");
                if (url != null && StrUtil.isNotBlank(url.toString())) {
                    return url.toString();
                }
            }
        } else if (cfg.isSingleServerFormat()) {
            Object url = cfg.getExtra().get("url");
            if (url != null && StrUtil.isNotBlank(url.toString())) {
                return url.toString();
            }
        } else if (cfg.isWrappedFormat()) {
            String rawJson = cfg.getRawConfigJson();
            if (rawJson != null) {
                return extractEndpointUrlTyped(rawJson, mcpName);
            }
        }
        throw new IllegalStateException("McpConnectionConfig 无法提取 URL");
    }

    public String extractEndpointUrl(
            cn.hutool.json.JSONObject connJson, String mcpName, String protocolType) {
        String url = connJson.getStr("url");
        if (StrUtil.isNotBlank(url)) return url;

        cn.hutool.json.JSONObject mcpServers = connJson.getJSONObject("mcpServers");
        if (mcpServers != null) {
            for (String key : mcpServers.keySet()) {
                cn.hutool.json.JSONObject server = mcpServers.getJSONObject(key);
                if (server != null && StrUtil.isNotBlank(server.getStr("url"))) {
                    return server.getStr("url");
                }
            }
        }

        cn.hutool.json.JSONObject serverConfig = connJson.getJSONObject("mcpServerConfig");
        if (serverConfig != null) {
            cn.hutool.json.JSONArray domains = serverConfig.getJSONArray("domains");
            if (domains != null && !domains.isEmpty()) {
                cn.hutool.json.JSONObject domain = domains.getJSONObject(0);
                String protocol = domain.getStr("protocol", "https");
                String domainName = domain.getStr("domain");
                Integer port = domain.getInt("port");
                String path = serverConfig.getStr("path", "");
                String portStr = (port != null && port != 443 && port != 80) ? ":" + port : "";
                return protocol + "://" + domainName + portStr + path;
            }
        }

        throw new BusinessException(ErrorCode.INVALID_REQUEST, "无法从连接配置中提取 endpoint URL");
    }

    // ==================== Config Format Conversion ====================

    public String convertToStandardConnectionConfig(
            cn.hutool.json.JSONObject mcpJson, String mcpName, String protocol) {
        String serverName =
                StrUtil.blankToDefault(mcpName, "mcp-server")
                        .toLowerCase()
                        .replaceAll("[^a-z0-9-]", "-");

        cn.hutool.json.JSONObject serverConfig = mcpJson.getJSONObject("mcpServerConfig");
        if (serverConfig != null && serverConfig.get("rawConfig") != null) {
            Object rawConfig = serverConfig.get("rawConfig");
            cn.hutool.json.JSONObject rawJson;
            try {
                rawJson =
                        rawConfig instanceof cn.hutool.json.JSONObject
                                ? (cn.hutool.json.JSONObject) rawConfig
                                : JSONUtil.parseObj(rawConfig.toString());
            } catch (Exception e) {
                return null;
            }
            if (rawJson.containsKey("mcpServers")) {
                return rawJson.toString();
            }
            return JSONUtil.createObj()
                    .set("mcpServers", JSONUtil.createObj().set(serverName, rawJson))
                    .toString();
        }

        if (serverConfig != null && serverConfig.getJSONArray("domains") != null) {
            cn.hutool.json.JSONArray domains = serverConfig.getJSONArray("domains");
            if (domains.isEmpty()) return null;

            cn.hutool.json.JSONObject domain = null;
            for (int i = 0; i < domains.size(); i++) {
                cn.hutool.json.JSONObject d = domains.getJSONObject(i);
                if (!"intranet".equalsIgnoreCase(d.getStr("networkType"))) {
                    domain = d;
                    break;
                }
            }
            if (domain == null) domain = domains.getJSONObject(0);

            String scheme = StrUtil.blankToDefault(domain.getStr("protocol"), "https");
            String host = domain.getStr("domain");
            Integer port = domain.getInt("port");
            String path = serverConfig.getStr("path", "");

            if (StrUtil.isBlank(host)) return null;

            StringBuilder urlBuilder = new StringBuilder(scheme).append("://").append(host);
            if (port != null && port > 0 && port != 443 && port != 80) {
                urlBuilder.append(":").append(port);
            }
            if (StrUtil.isNotBlank(path)) {
                if (!path.startsWith("/")) urlBuilder.append("/");
                urlBuilder.append(path);
            }

            String url = urlBuilder.toString();
            McpProtocolType proto = McpProtocolType.fromString(protocol);
            boolean isSse = proto == null || proto.isSse();
            if (isSse && !url.endsWith("/sse")) {
                url = url.endsWith("/") ? url + "sse" : url + "/sse";
            }

            cn.hutool.json.JSONObject serverEntry = JSONUtil.createObj().set("url", url);
            if (isSse) serverEntry.set("type", McpProtocolType.SSE.getValue());

            return JSONUtil.createObj()
                    .set("mcpServers", JSONUtil.createObj().set(serverName, serverEntry))
                    .toString();
        }

        return null;
    }

    // ==================== ResolvedConfig Fill ====================

    public void fillResolvedConfig(
            McpMetaResult result, McpServerMeta meta, McpServerEndpoint endpoint) {
        try {
            String serverName =
                    StrUtil.blankToDefault(meta.getMcpName(), "mcp-server")
                            .toLowerCase()
                            .replaceAll("[^a-z0-9-]", "-");

            if (endpoint != null && StrUtil.isNotBlank(endpoint.getEndpointUrl())) {
                McpProtocolType proto =
                        McpProtocolType.fromString(
                                StrUtil.blankToDefault(endpoint.getProtocol(), "sse"));
                boolean isSse = proto == null || proto.isSse();

                cn.hutool.json.JSONObject serverEntry =
                        JSONUtil.createObj().set("url", endpoint.getEndpointUrl());
                serverEntry.set("type", isSse ? "sse" : "streamable-http");
                result.setResolvedConfig(
                        JSONUtil.createObj()
                                .set(
                                        "mcpServers",
                                        JSONUtil.createObj().set(serverName, serverEntry))
                                .toString());
                return;
            }

            if (StrUtil.isBlank(meta.getConnectionConfig())) return;

            try {
                McpConnectionConfig cfg = McpConnectionConfig.parse(meta.getConnectionConfig());
                if (cfg.isMcpServersFormat() || cfg.isSingleServerFormat()) {
                    result.setResolvedConfig(cfg.toMcpServersJsonWithoutEnv(serverName));
                    return;
                }
            } catch (Exception ignored) {
            }

            cn.hutool.json.JSONObject connJson = JSONUtil.parseObj(meta.getConnectionConfig());
            String resolved =
                    convertToStandardConnectionConfig(
                            connJson, meta.getMcpName(), meta.getProtocolType());
            if (StrUtil.isNotBlank(resolved)) {
                result.setResolvedConfig(resolved);
            }
        } catch (Exception e) {
            log.debug(
                    "[fillResolvedConfig] 解析失败 mcpServerId={}: {}",
                    meta.getMcpServerId(),
                    e.getMessage());
        }
    }
}
