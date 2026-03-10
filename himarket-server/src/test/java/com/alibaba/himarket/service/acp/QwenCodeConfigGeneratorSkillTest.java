package com.alibaba.himarket.service.acp;

import static org.junit.jupiter.api.Assertions.*;

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
 * QwenCodeConfigGenerator.generateSkillConfig 单元测试。
 * 验证 Skill 坐标+凭证写入 .qwen/settings.json 的正确性。
 */
class QwenCodeConfigGeneratorSkillTest {

    @TempDir Path tempDir;

    private QwenCodeConfigGenerator generator;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        generator = new QwenCodeConfigGenerator(objectMapper);
    }

    @Test
    void generateSkillConfig_nullList_noFileCreated() throws IOException {
        generator.generateSkillConfig(tempDir.toString(), null);
        Path configPath = tempDir.resolve(".qwen/settings.json");
        assertFalse(Files.exists(configPath));
    }

    @Test
    void generateSkillConfig_emptyList_noFileCreated() throws IOException {
        generator.generateSkillConfig(tempDir.toString(), List.of());
        Path configPath = tempDir.resolve(".qwen/settings.json");
        assertFalse(Files.exists(configPath));
    }

    @Test
    @SuppressWarnings("unchecked")
    void generateSkillConfig_singleSkill_writesCoordinatesAndCredentials() throws IOException {
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

        Path configPath = tempDir.resolve(".qwen/settings.json");
        assertTrue(Files.exists(configPath));

        Map<String, Object> root =
                objectMapper.readValue(
                        Files.readString(configPath),
                        new TypeReference<LinkedHashMap<String, Object>>() {});
        List<Map<String, Object>> skills = (List<Map<String, Object>>) root.get("skills");
        assertNotNull(skills);
        assertEquals(1, skills.size());

        Map<String, Object> entry = skills.get(0);
        assertEquals("java-standards", entry.get("name"));
        assertEquals("nacos-001", entry.get("nacosId"));
        assertEquals("public", entry.get("namespace"));
        assertEquals("java-coding-standards", entry.get("skillName"));
        assertEquals("http://nacos:8848", entry.get("serverAddr"));
        assertEquals("nacos", entry.get("username"));
        assertEquals("nacos", entry.get("password"));
        assertNull(entry.get("accessKey"));
        assertNull(entry.get("secretKey"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void generateSkillConfig_withAccessKey_includesAccessKeyAndSecretKey() throws IOException {
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

        Path configPath = tempDir.resolve(".qwen/settings.json");
        Map<String, Object> root =
                objectMapper.readValue(
                        Files.readString(configPath),
                        new TypeReference<LinkedHashMap<String, Object>>() {});
        List<Map<String, Object>> skills = (List<Map<String, Object>>) root.get("skills");
        Map<String, Object> entry = skills.get(0);
        assertEquals("ak123", entry.get("accessKey"));
        assertEquals("sk456", entry.get("secretKey"));
    }
}
