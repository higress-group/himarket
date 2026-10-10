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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;

import com.alibaba.himarket.core.security.ContextHolder;
import com.alibaba.himarket.dto.converter.NacosAgentConverter;
import com.alibaba.himarket.dto.result.common.DomainResult;
import com.alibaba.himarket.dto.result.mcp.McpConfigResult;
import com.alibaba.himarket.repository.NacosInstanceRepository;
import com.alibaba.himarket.support.enums.McpProtocolType;
import com.alibaba.nacos.api.ai.model.mcp.McpEndpointInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpServerDetailInfo;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class NacosServiceImplMcpConfigTest {

    private final NacosServiceImpl service =
            new NacosServiceImpl(
                    mock(NacosInstanceRepository.class),
                    mock(ContextHolder.class),
                    mock(NacosAgentConverter.class));

    @Test
    void remoteSseServerExposesProtocolDomainAndPath() {
        McpServerDetailInfo detail = remoteDetail("mcp-sse", null);
        detail.setBackendEndpoints(List.of(endpoint("10.0.0.5", 8080, "/sse-path")));

        McpConfigResult result = service.buildMCPConfigResult(detail);

        assertEquals(McpProtocolType.SSE, result.getProtocol());
        assertEquals("sse", result.getMeta().getProtocol());
        assertEquals("NACOS", result.getMeta().getSource());
        assertEquals("/sse-path", result.getMcpServerConfig().getPath());
        DomainResult domain = result.getMcpServerConfig().getDomains().get(0);
        assertEquals("10.0.0.5", domain.getDomain());
        assertEquals(8080, domain.getPort());
        assertEquals("http", domain.getProtocol());
    }

    @Test
    void remoteStreamableServerPrefersFrontProtocol() {
        McpServerDetailInfo detail = remoteDetail("http", "streamable-http");
        detail.setBackendEndpoints(List.of(endpoint("mcp.example.com", 443, "/mcp")));

        McpConfigResult result = service.buildMCPConfigResult(detail);

        assertEquals(McpProtocolType.STREAMABLE_HTTP, result.getProtocol());
        assertEquals("streamableHttp", result.getMeta().getProtocol());
    }

    @Test
    void addressWithSchemeAndPortIsParsed() {
        McpServerDetailInfo detail = remoteDetail("mcp-streamable", null);
        detail.setBackendEndpoints(List.of(endpoint("https://mcp.example.com:9443", 0, "mcp")));

        McpConfigResult result = service.buildMCPConfigResult(detail);

        DomainResult domain = result.getMcpServerConfig().getDomains().get(0);
        assertEquals("mcp.example.com", domain.getDomain());
        assertEquals(9443, domain.getPort());
        assertEquals("https", domain.getProtocol());
        assertEquals("mcp", result.getMcpServerConfig().getPath());
        assertEquals(
                "https://mcp.example.com:9443/mcp", urlFromRawConfig(result, detail.getName()));
    }

    @Test
    void rawConfigKeepsFullBackendUrl() {
        McpServerDetailInfo detail = remoteDetail("mcp-sse", null);
        detail.setBackendEndpoints(List.of(endpoint("10.0.0.5", 8080, "/sse")));

        McpConfigResult result = service.buildMCPConfigResult(detail);

        assertEquals("http://10.0.0.5:8080/sse", urlFromRawConfig(result, detail.getName()));
    }

    @Test
    void stdioServerKeepsRawConfigWithoutDomains() {
        McpServerDetailInfo detail = new McpServerDetailInfo();
        detail.setName("local-mcp");
        detail.setProtocol("stdio");
        detail.setLocalServerConfig(Map.of("command", "npx"));

        McpConfigResult result = service.buildMCPConfigResult(detail);

        assertEquals(McpProtocolType.STDIO, result.getProtocol());
        assertNotNull(result.getMcpServerConfig().getRawConfig());
        assertNull(result.getMcpServerConfig().getDomains());
    }

    @Test
    void localServerWithoutProtocolFallsBackToStdio() {
        McpServerDetailInfo detail = new McpServerDetailInfo();
        detail.setName("local-mcp");
        detail.setLocalServerConfig(Map.of("command", "npx"));

        McpConfigResult result = service.buildMCPConfigResult(detail);

        assertEquals(McpProtocolType.STDIO, result.getProtocol());
    }

    @Test
    void sseConfigProducesGatewayStyleTransportUrl() {
        McpServerDetailInfo detail = remoteDetail("mcp-sse", null);
        detail.setBackendEndpoints(List.of(endpoint("10.0.0.5", 8080, "/")));

        McpConfigResult result = service.buildMCPConfigResult(detail);

        assertEquals("http://10.0.0.5:8080/sse", result.toTransportConfig().getUrl());
    }

    private McpServerDetailInfo remoteDetail(String protocol, String frontProtocol) {
        McpServerDetailInfo detail = new McpServerDetailInfo();
        detail.setName("remote-mcp");
        detail.setProtocol(protocol);
        detail.setFrontProtocol(frontProtocol);
        return detail;
    }

    private McpEndpointInfo endpoint(String address, int port, String path) {
        McpEndpointInfo endpoint = new McpEndpointInfo();
        endpoint.setAddress(address);
        endpoint.setPort(port);
        endpoint.setPath(path);
        return endpoint;
    }

    @SuppressWarnings("unchecked")
    private String urlFromRawConfig(McpConfigResult result, String serverName) {
        Map<String, Object> raw = (Map<String, Object>) result.getMcpServerConfig().getRawConfig();
        Map<String, Object> servers = (Map<String, Object>) raw.get("mcpServers");
        Map<String, Object> server = (Map<String, Object>) servers.get(serverName);
        return (String) server.get("url");
    }
}
