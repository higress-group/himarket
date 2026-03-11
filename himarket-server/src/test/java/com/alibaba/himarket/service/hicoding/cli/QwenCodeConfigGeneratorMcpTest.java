package com.alibaba.himarket.service.hicoding.cli;

import static org.junit.jupiter.api.Assertions.*;

import com.alibaba.himarket.service.hicoding.session.ResolvedSessionConfig;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * QwenCodeConfigGenerator.generateMcpConfig 单元测试。
 * 验证 MCP Server 配置注入到 .qwen/settings.json 的正确性。
 */
class QwenCodeConfigGeneratorMcpTest {

    @TempDir Path tempDir;

    private QwenCodeConfigGenerator generator;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        generator = new QwenCodeConfigGenerator(objectMapper);
    }

    @Test
    void generateMcpConfig_nullList_noFileCreated() throws IOException {
        generator.generateMcpConfig(tempDir.toString(), null);

        Path configPath = tempDir.resolve(".qwen/settings.json");
        assertFalse(Files.exists(configPath));
    }

    @Test
    void generateMcpConfig_emptyList_noFileCreated() throws IOException {
        generator.generateMcpConfig(tempDir.toString(), List.of());

        Path configPath = tempDir.resolve(".qwen/settings.json");
        assertFalse(Files.exists(configPath));
    }

    @Test
    void generateMcpConfig_singleServer_correctFormat() throws IOException {
        ResolvedSessionConfig.ResolvedMcpEntry entry = new ResolvedSessionConfig.ResolvedMcpEntry();
        entry.setName("my-mcp");
        entry.setUrl("http://example.com/mcp/sse");
        entry.setTransportType("sse");

        generator.generateMcpConfig(tempDir.toString(), List.of(entry));

        Map<String, Object> root = readConfig();
        assertNotNull(root.get("mcpServers"));

        @SuppressWarnings("unchecked")
        Map<String, Object> mcpServers = (Map<String, Object>) root.get("mcpServers");
        assertEquals(1, mcpServers.size());
        assertTrue(mcpServers.containsKey("my-mcp"));

        @SuppressWarnings("unchecked")
        Map<String, Object> serverConfig = (Map<String, Object>) mcpServers.get("my-mcp");
        assertEquals("http://example.com/mcp/sse", serverConfig.get("url"));
        assertEquals("sse", serverConfig.get("type"));
        assertNull(serverConfig.get("headers"));
    }

    @Test
    void generateMcpConfig_withHeaders_headersIncluded() throws IOException {
        ResolvedSessionConfig.ResolvedMcpEntry entry = new ResolvedSessionConfig.ResolvedMcpEntry();
        entry.setName("auth-mcp");
        entry.setUrl("http://example.com/mcp");
        entry.setTransportType("streamable-http");
        entry.setHeaders(Map.of("Authorization", "Bearer token123"));

        generator.generateMcpConfig(tempDir.toString(), List.of(entry));

        Map<String, Object> root = readConfig();
        @SuppressWarnings("unchecked")
        Map<String, Object> mcpServers = (Map<String, Object>) root.get("mcpServers");
        @SuppressWarnings("unchecked")
        Map<String, Object> serverConfig = (Map<String, Object>) mcpServers.get("auth-mcp");

        assertEquals("http://example.com/mcp", serverConfig.get("url"));
        assertEquals("streamable-http", serverConfig.get("type"));
        assertNotNull(serverConfig.get("headers"));

        @SuppressWarnings("unchecked")
        Map<String, String> headers = (Map<String, String>) serverConfig.get("headers");
        assertEquals("Bearer token123", headers.get("Authorization"));
    }

    @Test
    void generateMcpConfig_multipleServers_allPresent() throws IOException {
        ResolvedSessionConfig.ResolvedMcpEntry entry1 =
                new ResolvedSessionConfig.ResolvedMcpEntry();
        entry1.setName("server-a");
        entry1.setUrl("http://a.com/mcp/sse");
        entry1.setTransportType("sse");

        ResolvedSessionConfig.ResolvedMcpEntry entry2 =
                new ResolvedSessionConfig.ResolvedMcpEntry();
        entry2.setName("server-b");
        entry2.setUrl("http://b.com/mcp");
        entry2.setTransportType("streamable-http");
        entry2.setHeaders(Map.of("X-Key", "abc"));

        generator.generateMcpConfig(tempDir.toString(), List.of(entry1, entry2));

        Map<String, Object> root = readConfig();
        @SuppressWarnings("unchecked")
        Map<String, Object> mcpServers = (Map<String, Object>) root.get("mcpServers");
        assertEquals(2, mcpServers.size());
        assertTrue(mcpServers.containsKey("server-a"));
        assertTrue(mcpServers.containsKey("server-b"));
    }

    @Test
    void generateMcpConfig_mergeWithExisting_preservesOldEntries() throws IOException {
        // 先写入一个已有的 settings.json，包含一个 MCP Server
        Path qwenDir = tempDir.resolve(".qwen");
        Files.createDirectories(qwenDir);
        Map<String, Object> existing = new LinkedHashMap<>();
        Map<String, Object> existingMcpServers = new LinkedHashMap<>();
        existingMcpServers.put(
                "old-server", Map.of("url", "http://old.com/mcp/sse", "type", "sse"));
        existing.put("mcpServers", existingMcpServers);
        existing.put("someOtherKey", "preserved");
        Files.writeString(
                qwenDir.resolve("settings.json"), objectMapper.writeValueAsString(existing));

        // 注入新的 MCP Server
        ResolvedSessionConfig.ResolvedMcpEntry newEntry =
                new ResolvedSessionConfig.ResolvedMcpEntry();
        newEntry.setName("new-server");
        newEntry.setUrl("http://new.com/mcp");
        newEntry.setTransportType("streamable-http");

        generator.generateMcpConfig(tempDir.toString(), List.of(newEntry));

        Map<String, Object> root = readConfig();
        // 验证其他配置项被保留
        assertEquals("preserved", root.get("someOtherKey"));

        @SuppressWarnings("unchecked")
        Map<String, Object> mcpServers = (Map<String, Object>) root.get("mcpServers");
        assertEquals(2, mcpServers.size());
        assertTrue(mcpServers.containsKey("old-server"));
        assertTrue(mcpServers.containsKey("new-server"));
    }

    @Test
    void generateMcpConfig_duplicateName_newOverridesOld() throws IOException {
        // 先写入已有配置
        Path qwenDir = tempDir.resolve(".qwen");
        Files.createDirectories(qwenDir);
        Map<String, Object> existing = new LinkedHashMap<>();
        Map<String, Object> existingMcpServers = new LinkedHashMap<>();
        existingMcpServers.put("my-mcp", Map.of("url", "http://old.com/mcp/sse", "type", "sse"));
        existing.put("mcpServers", existingMcpServers);
        Files.writeString(
                qwenDir.resolve("settings.json"), objectMapper.writeValueAsString(existing));

        // 注入同名的新 MCP Server
        ResolvedSessionConfig.ResolvedMcpEntry newEntry =
                new ResolvedSessionConfig.ResolvedMcpEntry();
        newEntry.setName("my-mcp");
        newEntry.setUrl("http://new.com/mcp");
        newEntry.setTransportType("streamable-http");

        generator.generateMcpConfig(tempDir.toString(), List.of(newEntry));

        Map<String, Object> root = readConfig();
        @SuppressWarnings("unchecked")
        Map<String, Object> mcpServers = (Map<String, Object>) root.get("mcpServers");
        assertEquals(1, mcpServers.size());

        @SuppressWarnings("unchecked")
        Map<String, Object> serverConfig = (Map<String, Object>) mcpServers.get("my-mcp");
        assertEquals("http://new.com/mcp", serverConfig.get("url"));
        assertEquals("streamable-http", serverConfig.get("type"));
    }

    @Test
    void generateMcpConfig_emptyHeaders_headersNotIncluded() throws IOException {
        ResolvedSessionConfig.ResolvedMcpEntry entry = new ResolvedSessionConfig.ResolvedMcpEntry();
        entry.setName("no-headers");
        entry.setUrl("http://example.com/mcp/sse");
        entry.setTransportType("sse");
        entry.setHeaders(Map.of());

        generator.generateMcpConfig(tempDir.toString(), List.of(entry));

        Map<String, Object> root = readConfig();
        @SuppressWarnings("unchecked")
        Map<String, Object> mcpServers = (Map<String, Object>) root.get("mcpServers");
        @SuppressWarnings("unchecked")
        Map<String, Object> serverConfig = (Map<String, Object>) mcpServers.get("no-headers");
        assertNull(serverConfig.get("headers"));
    }

    private Map<String, Object> readConfig() throws IOException {
        Path configPath = tempDir.resolve(".qwen/settings.json");
        assertTrue(Files.exists(configPath), "settings.json should exist");
        String content = Files.readString(configPath);
        return objectMapper.readValue(
                content, new TypeReference<LinkedHashMap<String, Object>>() {});
    }
}
