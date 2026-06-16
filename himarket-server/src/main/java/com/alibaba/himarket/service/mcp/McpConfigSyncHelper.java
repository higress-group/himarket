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
import com.alibaba.himarket.support.common.Strings;
import com.alibaba.himarket.support.enums.McpEndpointStatus;
import com.alibaba.himarket.support.enums.McpHostingType;
import com.alibaba.himarket.support.enums.McpOrigin;
import com.alibaba.himarket.support.enums.McpProtocolType;
import com.alibaba.himarket.support.enums.ProductStatus;
import com.alibaba.himarket.support.enums.SourceType;
import com.alibaba.himarket.support.product.NacosRefConfig;
import com.alibaba.himarket.utils.JsonUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Iterator;
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
@Slf4j
@RequiredArgsConstructor
public class McpConfigSyncHelper {

    private final McpServerMetaRepository metaRepository;
    private final McpServerEndpointRepository endpointRepository;
    private final ProductRefRepository productRefRepository;
    private final ProductRepository productRepository;
    private final GatewayService gatewayService;
    private final NacosService nacosService;

    // ProductRef sync.

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
        if (originEnum == McpOrigin.GATEWAY && Strings.isNotBlank(param.getGatewayId())) {
            return SourceType.GATEWAY;
        } else if (originEnum == McpOrigin.NACOS && Strings.isNotBlank(param.getNacosId())) {
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

        if (Strings.isBlank(mcpConfigStr)) {
            return;
        }

        try {
            ObjectNode mcpJson = JsonUtil.readObjectNode(mcpConfigStr);
            JsonNode protocolNode = mcpJson.path("meta").path("protocol");
            if (!protocolNode.isMissingNode()) {
                meta.setProtocolType(McpProtocolUtils.normalize(protocolNode.asText()));
            }
            String tools = mcpJson.path("tools").asText();
            if (Strings.isNotBlank(tools) && Strings.isBlank(meta.getToolsConfig())) {
                meta.setToolsConfig(McpToolsConfigParser.normalize(tools));
            }
            String standardConfig =
                    convertToStandardConnectionConfig(
                            mcpJson, meta.getMcpName(), protocolNode.asText());
            meta.setConnectionConfig(
                    Strings.isNotBlank(standardConfig) ? standardConfig : mcpConfigStr);
        } catch (Exception e) {
            log.warn(
                    "Failed to parse remote config, keeping the original format, errorMessage={}",
                    e.getMessage(),
                    e);
            meta.setConnectionConfig(mcpConfigStr);
        }
        metaRepository.save(meta);
    }

    private String fetchGatewayConfig(SaveMcpMetaParam param) {
        Object refConfigObj = null;
        if (Strings.isNotBlank(param.getRefConfig())) {
            ObjectNode refJson = JsonUtil.readObjectNode(param.getRefConfig());
            String fromGatewayType = refJson.path("fromGatewayType").asText();
            if ("HIGRESS".equals(fromGatewayType)) {
                refConfigObj =
                        JsonUtil.parse(
                                param.getRefConfig(),
                                com.alibaba.himarket.support.product.HigressRefConfig.class);
            } else {
                refConfigObj =
                        JsonUtil.parse(
                                param.getRefConfig(),
                                com.alibaba.himarket.support.product.APIGRefConfig.class);
            }
        }
        return gatewayService.fetchMcpConfig(param.getGatewayId(), refConfigObj);
    }

    private String fetchNacosConfig(SaveMcpMetaParam param) {
        NacosRefConfig nacosRef =
                Strings.isNotBlank(param.getRefConfig())
                        ? JsonUtil.parse(param.getRefConfig(), NacosRefConfig.class)
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
            if (Strings.isNotBlank(param.getRefConfig())) {
                ref.setNacosRefConfig(JsonUtil.parse(param.getRefConfig(), NacosRefConfig.class));
            }
        }

        productRefRepository.save(ref);
    }

    private void applyGatewayRefConfig(ProductRef ref, String refConfig) {
        if (Strings.isBlank(refConfig)) {
            return;
        }
        ObjectNode refJson = JsonUtil.readObjectNode(refConfig);
        String fromGatewayType = refJson.path("fromGatewayType").asText();
        if ("HIGRESS".equals(fromGatewayType)) {
            ref.setHigressRefConfig(
                    JsonUtil.parse(
                            refConfig,
                            com.alibaba.himarket.support.product.HigressRefConfig.class));
        } else if ("ADP_AI_GATEWAY".equals(fromGatewayType)) {
            ref.setAdpAIGatewayRefConfig(
                    JsonUtil.parse(
                            refConfig, com.alibaba.himarket.support.product.APIGRefConfig.class));
        } else if ("APSARA_GATEWAY".equals(fromGatewayType)) {
            ref.setApsaraGatewayRefConfig(
                    JsonUtil.parse(
                            refConfig, com.alibaba.himarket.support.product.APIGRefConfig.class));
        } else {
            ref.setApigRefConfig(
                    JsonUtil.parse(
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

    // Endpoint operations.

    /**
     * Finds all endpoints for a given mcpServerId.
     */
    public List<McpServerEndpoint> findEndpointsByMcpServerId(String mcpServerId) {
        return endpointRepository.findByMcpServerId(mcpServerId);
    }

    /**
     * Deletes an endpoint.
     */
    public void deleteEndpoint(McpServerEndpoint endpoint) {
        endpointRepository.delete(endpoint);
    }

    /**
     * Flushes pending deletes to avoid unique constraint conflicts on subsequent inserts.
     */
    public void flushEndpoints() {
        endpointRepository.flush();
    }

    /**
     * Saves a new endpoint for sandbox pre-create.
     */
    public McpServerEndpoint saveEndpoint(McpServerEndpoint endpoint) {
        return endpointRepository.save(endpoint);
    }

    // Public endpoint sync.

    /**
     * For non-sandbox MCP: extract endpoint URL from connectionConfig and create/update
     * a public endpoint (userId=*).
     */
    public void syncPublicEndpoint(McpServerMeta meta) {
        String connectionConfig = meta.getConnectionConfig();
        if (Strings.isBlank(connectionConfig)) {
            return;
        }

        String endpointUrl;
        try {
            endpointUrl = extractEndpointUrlTyped(connectionConfig, meta.getMcpName());
        } catch (Exception e1) {
            try {
                ObjectNode connJson = JsonUtil.readObjectNode(connectionConfig);
                endpointUrl =
                        extractEndpointUrl(connJson, meta.getMcpName(), meta.getProtocolType());
            } catch (Exception e2) {
                log.debug(
                        "Unable to extract endpoint URL from connection config, mcpServerId={},"
                                + " errorMessage={}",
                        meta.getMcpServerId(),
                        e2.getMessage());
                return;
            }
        }

        if (Strings.isBlank(endpointUrl)) {
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
                "Public MCP endpoint synced, mcpServerId={}, protocol={}, endpoint={}",
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

    // Product display field enrichment.

    public void syncDisplayFieldsToProduct(String productId, SaveMcpMetaParam param) {
        productRepository
                .findByProductId(productId)
                .ifPresent(
                        product -> {
                            boolean changed = false;
                            if (Strings.isNotBlank(param.getDisplayName())
                                    && !param.getDisplayName().equals(product.getName())) {
                                product.setName(param.getDisplayName());
                                changed = true;
                            }
                            if (param.getDescription() != null
                                    && !param.getDescription().equals(product.getDescription())) {
                                product.setDescription(param.getDescription());
                                changed = true;
                            }
                            if (Strings.isNotBlank(param.getIcon())) {
                                try {
                                    product.setIcon(
                                            JsonUtil.parse(
                                                    param.getIcon(),
                                                    com.alibaba.himarket.support.product.Icon
                                                            .class));
                                    changed = true;
                                } catch (Exception e) {
                                    log.warn(
                                            "Failed to parse icon JSON, errorMessage={}",
                                            e.getMessage());
                                }
                            }
                            if (Strings.isNotBlank(param.getServiceIntro())
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
                result.setIcon(JsonUtil.toJson(product.getIcon()));
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

    // Endpoint URL extraction.

    public String extractEndpointUrlTyped(String connectionConfigJson, String mcpName)
            throws Exception {
        McpConnectionConfig cfg = McpConnectionConfig.parse(connectionConfigJson);
        if (cfg.isMcpServersFormat()) {
            for (McpConnectionConfig.McpServerEntry entry : cfg.getMcpServers().values()) {
                Object url = entry.getExtra().get("url");
                if (url != null && Strings.isNotBlank(url.toString())) {
                    return url.toString();
                }
            }
        } else if (cfg.isSingleServerFormat()) {
            Object url = cfg.getExtra().get("url");
            if (url != null && Strings.isNotBlank(url.toString())) {
                return url.toString();
            }
        } else if (cfg.isWrappedFormat()) {
            String rawJson = cfg.getRawConfigJson();
            if (rawJson != null) {
                return extractEndpointUrlTyped(rawJson, mcpName);
            }
        }
        throw new IllegalStateException("Unable to extract URL from McpConnectionConfig");
    }

    public String extractEndpointUrl(ObjectNode connJson, String mcpName, String protocolType) {
        String url = connJson.path("url").asText();
        if (Strings.isNotBlank(url)) {
            return url;
        }

        JsonNode mcpServersNode = connJson.get("mcpServers");
        if (mcpServersNode != null && mcpServersNode.isObject()) {
            ObjectNode mcpServers = (ObjectNode) mcpServersNode;
            Iterator<String> fieldNames = mcpServers.fieldNames();
            while (fieldNames.hasNext()) {
                String key = fieldNames.next();
                JsonNode serverNode = mcpServers.get(key);
                if (serverNode != null && serverNode.isObject()) {
                    ObjectNode server = (ObjectNode) serverNode;
                    String serverUrl = server.path("url").asText();
                    if (Strings.isNotBlank(serverUrl)) {
                        return serverUrl;
                    }
                }
            }
        }

        JsonNode serverConfigNode = connJson.get("mcpServerConfig");
        if (serverConfigNode != null && serverConfigNode.isObject()) {
            ObjectNode serverConfig = (ObjectNode) serverConfigNode;
            JsonNode domainsNode = serverConfig.get("domains");
            if (domainsNode != null && domainsNode.isArray() && domainsNode.size() > 0) {
                ObjectNode domain = (ObjectNode) domainsNode.get(0);
                String protocol = domain.path("protocol").asText("https");
                String domainName = domain.path("domain").asText();
                int port = domain.has("port") ? domain.get("port").asInt() : 0;
                String path = serverConfig.path("path").asText("");
                String portStr = (port != 0 && port != 443 && port != 80) ? ":" + port : "";
                return protocol + "://" + domainName + portStr + path;
            }
        }

        throw new BusinessException(
                ErrorCode.INVALID_REQUEST, "Unable to extract endpoint URL from connection config");
    }

    // Config format conversion.

    public String convertToStandardConnectionConfig(
            ObjectNode mcpJson, String mcpName, String protocol) {
        String serverName =
                Strings.blankToDefault(mcpName, "mcp-server")
                        .toLowerCase()
                        .replaceAll("[^a-z0-9-]", "-");

        JsonNode serverConfigNode = mcpJson.get("mcpServerConfig");
        if (serverConfigNode != null && serverConfigNode.isObject()) {
            ObjectNode serverConfig = (ObjectNode) serverConfigNode;
            if (serverConfig.get("rawConfig") != null) {
                JsonNode rawConfig = serverConfig.get("rawConfig");
                ObjectNode rawJson;
                try {
                    rawJson =
                            rawConfig.isObject()
                                    ? (ObjectNode) rawConfig
                                    : JsonUtil.readObjectNode(rawConfig.asText());
                } catch (Exception e) {
                    return null;
                }
                if (rawJson.has("mcpServers")) {
                    return rawJson.toString();
                }
                ObjectNode result = JsonUtil.createObjectNode();
                ObjectNode mcpServers = JsonUtil.createObjectNode();
                mcpServers.set(serverName, rawJson);
                result.set("mcpServers", mcpServers);
                return result.toString();
            }

            JsonNode domainsNode = serverConfig.get("domains");
            if (domainsNode != null && domainsNode.isArray()) {
                ArrayNode domains = (ArrayNode) domainsNode;
                if (domains.size() == 0) return null;

                ObjectNode domain = null;
                for (int i = 0; i < domains.size(); i++) {
                    ObjectNode d = (ObjectNode) domains.get(i);
                    if (!"intranet".equalsIgnoreCase(d.path("networkType").asText())) {
                        domain = d;
                        break;
                    }
                }
                if (domain == null) domain = (ObjectNode) domains.get(0);

                String scheme = Strings.blankToDefault(domain.path("protocol").asText(), "https");
                String host = domain.path("domain").asText();
                int port = domain.has("port") ? domain.get("port").asInt() : 0;
                String path = serverConfig.path("path").asText("");

                if (Strings.isBlank(host)) {
                    return null;
                }

                StringBuilder urlBuilder = new StringBuilder(scheme).append("://").append(host);
                if (port != 0 && port != 443 && port != 80) {
                    urlBuilder.append(":").append(port);
                }
                if (Strings.isNotBlank(path)) {
                    if (!path.startsWith("/")) urlBuilder.append("/");
                    urlBuilder.append(path);
                }

                String url = urlBuilder.toString();
                McpProtocolType proto = McpProtocolType.fromString(protocol);
                boolean isSse = proto == null || proto.isSse();
                if (isSse && !url.endsWith("/sse")) {
                    url = url.endsWith("/") ? url + "sse" : url + "/sse";
                }

                ObjectNode serverEntry = JsonUtil.createObjectNode();
                serverEntry.put("url", url);
                if (isSse) serverEntry.put("type", McpProtocolType.SSE.getValue());

                ObjectNode result = JsonUtil.createObjectNode();
                ObjectNode mcpServers = JsonUtil.createObjectNode();
                mcpServers.set(serverName, serverEntry);
                result.set("mcpServers", mcpServers);
                return result.toString();
            }
        }

        return null;
    }

    // ResolvedConfig fill.

    public void fillResolvedConfig(
            McpMetaResult result, McpServerMeta meta, McpServerEndpoint endpoint) {
        try {
            String serverName =
                    Strings.blankToDefault(meta.getMcpName(), "mcp-server")
                            .toLowerCase()
                            .replaceAll("[^a-z0-9-]", "-");

            if (endpoint != null && Strings.isNotBlank(endpoint.getEndpointUrl())) {
                McpProtocolType proto =
                        McpProtocolType.fromString(
                                Strings.blankToDefault(endpoint.getProtocol(), "sse"));
                boolean isSse = proto == null || proto.isSse();

                ObjectNode serverEntry = JsonUtil.createObjectNode();
                serverEntry.put("url", endpoint.getEndpointUrl());
                serverEntry.put("type", isSse ? "sse" : "streamable-http");
                ObjectNode resolved = JsonUtil.createObjectNode();
                ObjectNode mcpServers = JsonUtil.createObjectNode();
                mcpServers.set(serverName, serverEntry);
                resolved.set("mcpServers", mcpServers);
                result.setResolvedConfig(resolved.toString());
                return;
            }

            if (Strings.isBlank(meta.getConnectionConfig())) {
                return;
            }

            try {
                McpConnectionConfig cfg = McpConnectionConfig.parse(meta.getConnectionConfig());
                if (cfg.isMcpServersFormat() || cfg.isSingleServerFormat()) {
                    result.setResolvedConfig(cfg.toMcpServersJsonWithoutEnv(serverName));
                    return;
                }
            } catch (Exception ignored) {
            }

            ObjectNode connJson = JsonUtil.readObjectNode(meta.getConnectionConfig());
            String resolved =
                    convertToStandardConnectionConfig(
                            connJson, meta.getMcpName(), meta.getProtocolType());
            if (Strings.isNotBlank(resolved)) {
                result.setResolvedConfig(resolved);
            }
        } catch (Exception e) {
            log.debug(
                    "Failed to resolve MCP config, mcpServerId={}, errorMessage={}",
                    meta.getMcpServerId(),
                    e.getMessage());
        }
    }
}
