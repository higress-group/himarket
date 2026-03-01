package com.alibaba.himarket.service.acp;

import static org.junit.jupiter.api.Assertions.*;

import com.alibaba.himarket.config.AcpProperties;
import com.alibaba.himarket.config.AcpProperties.CliProviderConfig;
import com.alibaba.himarket.service.acp.runtime.SandboxType;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
        qoder.setRuntimeCategory("native");
        qoder.setCompatibleRuntimes(List.of(SandboxType.LOCAL, SandboxType.K8S));
        qoder.setContainerImage("himarket/sandbox:latest");

        CliProviderConfig kiro = new CliProviderConfig();
        kiro.setDisplayName("Kiro CLI");
        kiro.setCommand("kiro-cli");
        kiro.setArgs("acp");
        kiro.setRuntimeCategory("native");
        kiro.setCompatibleRuntimes(List.of(SandboxType.LOCAL));

        CliProviderConfig claude = new CliProviderConfig();
        claude.setDisplayName("Claude Code");
        claude.setCommand("npx");
        claude.setArgs("claude-code-acp");
        claude.setEnv(Map.of("ANTHROPIC_API_KEY", "test-key"));
        claude.setRuntimeCategory("nodejs");
        claude.setCompatibleRuntimes(List.of(SandboxType.LOCAL));

        CliProviderConfig codex = new CliProviderConfig();
        codex.setDisplayName("Codex CLI");
        codex.setCommand("codex");
        codex.setArgs("--acp");
        codex.setRuntimeCategory("nodejs");
        codex.setCompatibleRuntimes(List.of(SandboxType.LOCAL));

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

    @Test
    void testDefaultRuntimeDefaultValue() {
        AcpProperties fresh = new AcpProperties();
        assertEquals("local", fresh.getDefaultRuntime());
    }

    @Test
    void testSetDefaultRuntime() {
        properties.setDefaultRuntime("k8s");
        assertEquals("k8s", properties.getDefaultRuntime());
    }

    @Test
    void testCompatibleRuntimesForNativeCli() {
        CliProviderConfig qoder = properties.getProvider("qodercli");
        assertNotNull(qoder.getCompatibleRuntimes());
        assertEquals(2, qoder.getCompatibleRuntimes().size());
        assertTrue(qoder.getCompatibleRuntimes().contains(SandboxType.LOCAL));
        assertTrue(qoder.getCompatibleRuntimes().contains(SandboxType.K8S));
    }

    @Test
    void testCompatibleRuntimesForNodejsCli() {
        CliProviderConfig claude = properties.getProvider("claude-code");
        assertNotNull(claude.getCompatibleRuntimes());
        assertEquals(1, claude.getCompatibleRuntimes().size());
        assertTrue(claude.getCompatibleRuntimes().contains(SandboxType.LOCAL));
    }

    @Test
    void testContainerImage() {
        CliProviderConfig qoder = properties.getProvider("qodercli");
        assertEquals("himarket/sandbox:latest", qoder.getContainerImage());
    }

    @Test
    void testRuntimeCategory() {
        assertEquals("native", properties.getProvider("qodercli").getRuntimeCategory());
        assertEquals("nodejs", properties.getProvider("claude-code").getRuntimeCategory());
    }
}
