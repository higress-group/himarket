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
import com.alibaba.himarket.support.api.spec.HttpConnection;
import com.alibaba.himarket.support.api.spec.McpServerSpec;
import com.alibaba.himarket.support.api.spec.SseConnection;
import com.alibaba.himarket.support.api.spec.StdioConnection;
import com.alibaba.himarket.support.api.spec.StreamableHttpConnection;
import com.alibaba.himarket.support.chat.mcp.MCPTransportConfig;
import com.alibaba.himarket.support.enums.MCPTransportMode;
import com.alibaba.himarket.support.enums.McpFromType;
import com.alibaba.himarket.support.enums.McpProtocolType;
import com.alibaba.himarket.utils.JsonUtil;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import lombok.Data;
import org.springframework.web.util.UriComponentsBuilder;

@Data
public class MCPConfigResult {

    protected String mcpServerName;

    protected MCPServerConfig mcpServerConfig;

    protected String tools;

    private McpFromType fromType;

    private McpProtocolType protocol;

    protected McpMetadata meta;

    public MCPTransportConfig toTransportConfig() {
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
                                Optional.ofNullable(domain.getPort())
                                        .filter(port -> port > 0)
                                        .map(String::valueOf)
                                        .orElse(null))
                        .build()
                        .toUriString();

        String url =
                Optional.ofNullable(mcpServerConfig.getPath())
                        .filter(StrUtil::isNotBlank)
                        .map(path -> path.startsWith("/") ? path : "/" + path)
                        .map(path -> baseUrl + path)
                        .orElse(baseUrl);

        String protocolStr =
                this.protocol != null
                        ? this.protocol.name()
                        : (meta != null ? meta.getProtocol() : null);
        MCPTransportMode transportMode = McpProtocolType.resolveTransportMode(protocolStr);

        if (transportMode == MCPTransportMode.SSE && !url.endsWith("/sse")) {
            url = url.endsWith("/") ? url + "sse" : url + "/sse";
        }

        return MCPTransportConfig.builder()
                .mcpServerName(mcpServerName)
                .transportMode(transportMode)
                .url(url)
                .build();
    }

    @Data
    public static class McpMetadata {

        /**
         * Source, e.g. APIG_AI, HIGRESS, NACOS
         */
        private String source;

        /**
         * Service type. Deprecated: use {@link MCPConfigResult#fromType} instead.
         * Kept for backward compatibility of serialized JSON.
         */
        @Deprecated private String createFromType;

        /**
         * Protocol. Deprecated: use {@link MCPConfigResult#protocol} instead.
         * Kept for backward compatibility of serialized JSON.
         */
        @Deprecated private String protocol;
    }

    @Data
    public static class MCPServerConfig {
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

    public static MCPConfigResult fromApiDefinition(ApiDefinition definition) {
        McpServerSpec spec = (McpServerSpec) definition.getSpec();

        MCPConfigResult result = new MCPConfigResult();
        result.setMcpServerName(definition.getName());

        MCPServerConfig serverConfig = new MCPServerConfig();

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

        McpMetadata meta = new McpMetadata();
        meta.setSource("API_DEFINITION");
        result.setMeta(meta);

        return result;
    }

    private static void parseUrlToServerConfig(String url, MCPServerConfig serverConfig) {
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
