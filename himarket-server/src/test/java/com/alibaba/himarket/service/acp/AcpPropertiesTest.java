package com.alibaba.himarket.service.acp;

import static org.junit.jupiter.api.Assertions.*;

import com.alibaba.himarket.config.AcpProperties;
import com.alibaba.himarket.config.AcpProperties.CliProviderConfig;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 单元测试：验证 AcpProperties 多 provider 配置的解析和查询逻辑。
 */
class AcpPropertiesTest {

    private AcpProperties properties;

    @BeforeEach
    void setUp() {
        properties = new AcpProperties();
        properties.setDefaultProvider("qodercli");
        properties.setWorkspaceRoot("/tmp/workspaces");

        CliProviderConfig qoder = new CliProviderConfig();
        qoder.setDisplayName("Qoder CLI");
        qoder.setCommand("qodercli");
        qoder.setArgs("--acp");

        CliProviderConfig kiro = new CliProviderConfig();
        kiro.setDisplayName("Kiro CLI");
        kiro.setCommand("kiro-cli");
        kiro.setArgs("acp");

        CliProviderConfig claude = new CliProviderConfig();
        claude.setDisplayName("Claude Code");
        claude.setCommand("npx");
        claude.setArgs("claude-code-acp");
        claude.setEnv(Map.of("ANTHROPIC_API_KEY", "test-key"));

        CliProviderConfig codex = new CliProviderConfig();
        codex.setDisplayName("Codex CLI");
        codex.setCommand("codex");
        codex.setArgs("--acp");

        properties.setProviders(
                Map.of(
                        "qodercli", qoder,
                        "kiro-cli", kiro,
                        "claude-code", claude,
                        "codex", codex));
    }

    @Test
    void testGetDefaultProvider() {
        assertEquals("qodercli", properties.getDefaultProvider());
    }

    @Test
    void testGetDefaultProviderConfig() {
        CliProviderConfig config = properties.getDefaultProviderConfig();
        assertNotNull(config);
        assertEquals("qodercli", config.getCommand());
        assertEquals("--acp", config.getArgs());
        assertEquals("Qoder CLI", config.getDisplayName());
    }

    @Test
    void testGetProviderByKey() {
        CliProviderConfig kiro = properties.getProvider("kiro-cli");
        assertNotNull(kiro);
        assertEquals("kiro-cli", kiro.getCommand());
        assertEquals("acp", kiro.getArgs());
    }

    @Test
    void testGetProviderWithEnv() {
        CliProviderConfig claude = properties.getProvider("claude-code");
        assertNotNull(claude);
        assertEquals("npx", claude.getCommand());
        assertEquals("claude-code-acp", claude.getArgs());
        assertEquals("test-key", claude.getEnv().get("ANTHROPIC_API_KEY"));
    }

    @Test
    void testGetUnknownProviderReturnsNull() {
        assertNull(properties.getProvider("unknown-cli"));
    }

    @Test
    void testProviderCount() {
        assertEquals(4, properties.getProviders().size());
    }

    @Test
    void testChangeDefaultProvider() {
        properties.setDefaultProvider("kiro-cli");
        CliProviderConfig config = properties.getDefaultProviderConfig();
        assertNotNull(config);
        assertEquals("kiro-cli", config.getCommand());
    }
}
