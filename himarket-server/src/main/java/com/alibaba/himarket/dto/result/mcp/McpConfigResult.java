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

package com.alibaba.himarket.dto.result.mcp;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.himarket.dto.result.common.DomainResult;
import com.alibaba.himarket.entity.ApiDefinition;
import com.alibaba.himarket.support.api.meta.ApiDefinitionMeta;
import com.alibaba.himarket.support.api.meta.ApiDefinitionSource;
import com.alibaba.himarket.support.api.spec.HttpConnection;
import com.alibaba.himarket.support.api.spec.McpServerSpec;
import com.alibaba.himarket.support.api.spec.SseConnection;
import com.alibaba.himarket.support.api.spec.StdioConnection;
import com.alibaba.himarket.support.api.spec.StreamableHttpConnection;
import com.alibaba.himarket.support.chat.mcp.McpTransportConfig;
import com.alibaba.himarket.support.enums.McpFromType;
import com.alibaba.himarket.support.enums.McpProtocolType;
import com.alibaba.himarket.support.enums.McpTransportMode;
import com.alibaba.himarket.utils.JsonUtil;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.web.util.UriComponentsBuilder;

@Data
public class McpConfigResult {

    protected String mcpServerName;

    protected McpServerConfig mcpServerConfig;

    protected String tools;

    private McpFromType fromType;

    private McpProtocolType protocol;

    protected McpMetadata meta;

    public McpTransportConfig toTransportConfig() {
        if (mcpServerConfig == null || CollUtil.isEmpty(mcpServerConfig.getDomains())) {
            return null;
        }

        DomainResult domain =
                mcpServerConfig.getDomains().stream()
                        .filter(d -> !StrUtil.equalsIgnoreCase(d.getNetworkType(), "intranet"))
                        .findFirst()
                        .orElse(null);

        if (domain == null) {
            return null;
        }

        String baseUrl =
                UriComponentsBuilder.newInstance()
                        .scheme(StrUtil.blankToDefault(domain.getProtocol(), "http"))
                        .host(domain.getDomain())
                        .port(
                                domain.getPort() != null && domain.getPort() > 0
                                        ? String.valueOf(domain.getPort())
                                        : null)
                        .build()
                        .toUriString();

        String url = baseUrl;
        String path = mcpServerConfig.getPath();
        if (StrUtil.isNotBlank(path)) {
            url = baseUrl + (path.startsWith("/") ? path : "/" + path);
        }

        String protocolStr =
                this.protocol != null
                        ? this.protocol.name()
                        : (meta != null ? meta.getProtocol() : null);
        McpTransportMode transportMode = McpProtocolType.resolveTransportMode(protocolStr);

        if (transportMode == McpTransportMode.SSE && !url.endsWith("/sse")) {
            url = url.endsWith("/") ? url + "sse" : url + "/sse";
        }

        return McpTransportConfig.builder()
                .mcpServerName(mcpServerName)
                .transportMode(transportMode)
                .url(url)
                .build();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class McpMetadata {

        /**
         * Source, e.g. APIG_AI, HIGRESS, NACOS
         */
        private String source;

        /**
         * Service type. Deprecated: use {@link McpConfigResult#fromType} instead.
         * Kept for backward compatibility of serialized JSON.
         */
        @Deprecated private String createFromType;

        /**
         * Protocol. Deprecated: use {@link McpConfigResult#protocol} instead.
         * Kept for backward compatibility of serialized JSON.
         */
        @Deprecated private String protocol;

        /**
         * Original source metadata, e.g. external marketplace provider and repository.
         */
        private McpOriginMetadata origin;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class McpOriginMetadata {

        /**
         * Original source type, e.g. EXTERNAL.
         */
        private String type;

        /**
         * Original provider, e.g. LOBEHUB.
         */
        private String provider;

        /**
         * Original repository URL.
         */
        private String repo;
    }

    @Data
    public static class McpServerConfig {
        /**
         * For gateway
         */
        private String path;

        private List<DomainResult> domains;

        /**
         * For nacos
         */
        private Object rawConfig;
    }

    public static McpConfigResult fromApiDefinition(ApiDefinition definition) {
        McpServerSpec spec = (McpServerSpec) definition.getSpec();

        McpConfigResult result = new McpConfigResult();
        result.setMcpServerName(definition.getName());

        McpServerConfig serverConfig = new McpServerConfig();

        if (spec.getConnection() instanceof StdioConnection stdio) {
            // Build stdio mcp server config
            ObjectNode serverNode = JsonUtil.createObjectNode();
            serverNode.put("command", stdio.getCommand());

            if (stdio.getArgs() != null) {
                ArrayNode argsNode = serverNode.putArray("args");
                stdio.getArgs().forEach(argsNode::add);
            }

            if (stdio.getCwd() != null) {
                serverNode.put("cwd", stdio.getCwd());
            }

            if (stdio.getEnv() != null) {
                ObjectNode envNode = serverNode.putObject("env");
                stdio.getEnv().forEach(envNode::put);
            }

            ObjectNode root = JsonUtil.createObjectNode();
            root.putObject("mcpServers").set(definition.getName(), serverNode);
            serverConfig.setRawConfig(root);

        } else {
            // Extract URL from supported HTTP-based connection types
            String url;
            if (spec.getConnection() instanceof SseConnection sse) {
                url = sse.getUrl();
            } else if (spec.getConnection() instanceof StreamableHttpConnection sh) {
                url = sh.getUrl();
            } else if (spec.getConnection() instanceof HttpConnection http) {
                url = http.getUrl();
            } else {
                throw new IllegalArgumentException(
                        "Unsupported connection type: "
                                + spec.getConnection().getClass().getName());
            }
            parseUrlToServerConfig(url, serverConfig);
        }

        result.setMcpServerConfig(serverConfig);

        if (spec.getToolsConfig() != null) {
            result.setTools(JsonUtil.toJson(spec.getToolsConfig()));
        }

        result.setFromType(spec.getFromType());
        result.setProtocol(spec.getProtocol());
        result.setMeta(
                McpMetadata.builder()
                        .source("API_DEFINITION")
                        .origin(buildOriginMetadata(definition))
                        .build());

        return result;
    }

    private static McpOriginMetadata buildOriginMetadata(ApiDefinition definition) {
        ApiDefinitionMeta meta = definition.getMeta();
        if (meta == null || meta.getSource() == null) {
            return null;
        }

        ApiDefinitionSource source = meta.getSource();
        return McpOriginMetadata.builder()
                .type(source.getType() == null ? null : source.getType().name())
                .provider(source.getProvider())
                .repo(source.getRepo())
                .build();
    }

    private static void parseUrlToServerConfig(String url, McpServerConfig serverConfig) {
        if (StrUtil.isBlank(url)) {
            return;
        }
        URI uri = URI.create(StrUtil.removeSuffix(url, "/sse"));
        serverConfig.setDomains(
                List.of(
                        DomainResult.builder()
                                .protocol(uri.getScheme())
                                .domain(uri.getHost())
                                .port(uri.getPort() > 0 ? uri.getPort() : null)
                                .build()));
        serverConfig.setPath(uri.getPath());
    }
}
