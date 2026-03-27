package com.alibaba.himarket.service.hicoding.cli;

import static org.junit.jupiter.api.Assertions.*;

import com.alibaba.himarket.service.hicoding.session.ResolvedSessionConfig;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * QwenCodeConfigGenerator.generateSkillConfig 单元测试。
 * 验证 Skill 配置改为生成 nacos-env.yaml 后的正确性。
 */
class QwenCodeConfigGeneratorSkillTest {

    @TempDir Path tempDir;

    private QwenCodeConfigGenerator generator;

    @BeforeEach
    void setUp() {
        generator = new QwenCodeConfigGenerator(new com.fasterxml.jackson.databind.ObjectMapper());
    }

    @Test
    void generateSkillConfig_nullList_noFileCreated() throws IOException {
        generator.generateSkillConfig(tempDir.toString(), null);
        Path nacosDir = tempDir.resolve(".nacos");
        assertFalse(Files.exists(nacosDir));
    }

    @Test
    void generateSkillConfig_emptyList_noFileCreated() throws IOException {
        generator.generateSkillConfig(tempDir.toString(), List.of());
        Path nacosDir = tempDir.resolve(".nacos");
        assertFalse(Files.exists(nacosDir));
    }

    @Test
    void generateSkillConfig_singleSkill_generatesNacosEnvYaml() throws IOException {
        ResolvedSessionConfig.ResolvedSkillEntry skill =
                new ResolvedSessionConfig.ResolvedSkillEntry();
        skill.setName("java-standards");
        skill.setNacosId("nacos-001");
        skill.setNamespace("public");
        skill.setSkillName("java-coding-standards");
        skill.setServerAddr("http://nacos:8848");
        skill.setUsername("nacos");
        skill.setPassword("nacos");

        generator.generateSkillConfig(tempDir.toString(), List.of(skill));

        Path nacosEnvPath = tempDir.resolve(".nacos/nacos-env-nacos-001.yaml");
        assertTrue(Files.exists(nacosEnvPath));

        String content = Files.readString(nacosEnvPath);
        assertTrue(content.contains("host: nacos"));
        assertTrue(content.contains("port: 8848"));
        assertTrue(content.contains("authType: nacos"));
        assertTrue(content.contains("username: nacos"));
        assertTrue(content.contains("password: nacos"));
        assertTrue(content.contains("namespace: public"));
        assertFalse(content.contains("accessKey:"));
    }

    @Test
    void generateSkillConfig_withAccessKey_generatesAliyunAuthType() throws IOException {
        ResolvedSessionConfig.ResolvedSkillEntry skill =
                new ResolvedSessionConfig.ResolvedSkillEntry();
        skill.setName("my-skill");
        skill.setNacosId("nacos-002");
        skill.setNamespace("dev");
        skill.setSkillName("my-skill-name");
        skill.setServerAddr("http://nacos:8848");
        skill.setUsername("user");
        skill.setPassword("pass");
        skill.setAccessKey("ak123");
        skill.setSecretKey("sk456");

        generator.generateSkillConfig(tempDir.toString(), List.of(skill));

        Path nacosEnvPath = tempDir.resolve(".nacos/nacos-env-nacos-002.yaml");
        assertTrue(Files.exists(nacosEnvPath));

        String content = Files.readString(nacosEnvPath);
        assertTrue(content.contains("authType: aliyun"));
        assertTrue(content.contains("accessKey: ak123"));
        assertTrue(content.contains("secretKey: sk456"));
    }

    @Test
    void generateSkillConfig_doesNotWriteSkillsToSettingsJson() throws IOException {
        ResolvedSessionConfig.ResolvedSkillEntry skill =
                new ResolvedSessionConfig.ResolvedSkillEntry();
        skill.setName("test-skill");
        skill.setNacosId("nacos-001");
        skill.setNamespace("public");
        skill.setSkillName("test");
        skill.setServerAddr("http://nacos:8848");
        skill.setUsername("nacos");
        skill.setPassword("nacos");

        generator.generateSkillConfig(tempDir.toString(), List.of(skill));

        // settings.json 不应该被创建（generateSkillConfig 不再写入 JSON）
        Path settingsPath = tempDir.resolve(".qwen/settings.json");
        assertFalse(Files.exists(settingsPath));
    }

    @Test
    void skillsDirectory_returnsQwenSkillsPath() {
        assertEquals(".qwen/skills/", generator.skillsDirectory());
    }
}
