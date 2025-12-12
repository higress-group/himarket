package com.alibaba.himarket.service.impl;

import static org.junit.jupiter.api.Assertions.*;

import com.alibaba.nacos.api.ai.model.mcp.McpEndpointInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpServerDetailInfo;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class NacosServiceImplEndpointTest {

    private static NacosServiceImpl newService() {
        return new NacosServiceImpl(null, null, null);
    }

    private static McpEndpointInfo endpoint(
            String protocol, String address, int port, String path) {
        McpEndpointInfo info = new McpEndpointInfo();
        info.setProtocol(protocol);
        info.setAddress(address);
        info.setPort(port);
        info.setPath(path);
        return info;
    }

    private static String invokeExtractEndpointUrl(
            NacosServiceImpl service, McpEndpointInfo endpoint) throws Exception {
        Method method =
                NacosServiceImpl.class.getDeclaredMethod(
                        "extractEndpointUrl", McpEndpointInfo.class);
        method.setAccessible(true);
        return (String) method.invoke(service, endpoint);
    }

    private static Object invokeBuildRemoteConnectionConfig(
            NacosServiceImpl service, McpServerDetailInfo detail) throws Exception {
        Method method =
                NacosServiceImpl.class.getDeclaredMethod(
                        "buildRemoteConnectionConfig", McpServerDetailInfo.class);
        method.setAccessible(true);
        return method.invoke(service, detail);
    }

    @Test
    void extractEndpointUrl_nullEndpoint_returnsNull() throws Exception {
        assertNull(invokeExtractEndpointUrl(newService(), null));
    }

    @Test
    void extractEndpointUrl_blankAddress_returnsNull() throws Exception {
        McpEndpointInfo info = endpoint("http", "  ", 8080, "/mcp");
        assertNull(invokeExtractEndpointUrl(newService(), info));
    }

    @Test
    void extractEndpointUrl_fullUrl_fillsMissingPortAndPath() throws Exception {
        McpEndpointInfo info = endpoint("https", "http://example.com", 8443, "/mcp");
        assertEquals("http://example.com:8443/mcp", invokeExtractEndpointUrl(newService(), info));
    }

    @Test
    void extractEndpointUrl_fullUrl_keepsExistingPath_andFillsMissingPort() throws Exception {
        McpEndpointInfo info = endpoint("http", "https://example.com/api", 1234, "/ignored");
        assertEquals("https://example.com:1234/api", invokeExtractEndpointUrl(newService(), info));
    }

    @Test
    void extractEndpointUrl_hostPort_parsesPortWhenEndpointPortMissing() throws Exception {
        McpEndpointInfo info = endpoint(null, "example.com:8080", -1, "x");
        assertEquals("http://example.com:8080/x", invokeExtractEndpointUrl(newService(), info));
    }

    @Test
    void extractEndpointUrl_hostPort_prefersEndpointPortWhenProvided() throws Exception {
        McpEndpointInfo info = endpoint(null, "example.com:8080", 9090, "x");
        assertEquals("http://example.com:9090/x", invokeExtractEndpointUrl(newService(), info));
    }

    @Test
    void extractEndpointUrl_ipv6BracketWithPort_buildsCorrectUrl() throws Exception {
        McpEndpointInfo info = endpoint("http", "[::1]:8848", -1, "nacos");
        assertEquals("http://[::1]:8848/nacos", invokeExtractEndpointUrl(newService(), info));
    }

    @Test
    @SuppressWarnings("unchecked")
    void buildRemoteConnectionConfig_withBackendEndpoint_setsUrlInMcpServers() throws Exception {
        NacosServiceImpl service = newService();

        McpEndpointInfo backend = endpoint("http", "example.com:8080", -1, "/mcp");
        McpServerDetailInfo detail = new McpServerDetailInfo();
        detail.setName("demo");
        detail.setBackendEndpoints(List.of(backend));

        Object config = invokeBuildRemoteConnectionConfig(service, detail);
        assertNotNull(config);
        assertTrue(config instanceof Map);

        Map<String, Object> connectionConfig = (Map<String, Object>) config;
        assertTrue(connectionConfig.containsKey("mcpServers"));

        Map<String, Object> mcpServers = (Map<String, Object>) connectionConfig.get("mcpServers");
        assertTrue(mcpServers.containsKey("demo"));

        Map<String, Object> serverConfig = (Map<String, Object>) mcpServers.get("demo");
        assertEquals("http://example.com:8080/mcp", serverConfig.get("url"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void buildRemoteConnectionConfig_withoutEndpoints_returnsBasicRemoteConfig() throws Exception {
        NacosServiceImpl service = newService();

        McpServerDetailInfo detail = new McpServerDetailInfo();
        detail.setName("demo");
        detail.setBackendEndpoints(List.of());

        Object config = invokeBuildRemoteConnectionConfig(service, detail);
        assertNotNull(config);
        assertTrue(config instanceof Map);

        Map<String, Object> basicConfig = (Map<String, Object>) config;
        assertEquals("remote", basicConfig.get("type"));
        assertEquals("demo", basicConfig.get("name"));
        assertEquals("http", basicConfig.get("protocol"));
    }
}
