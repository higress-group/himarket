package com.alibaba.himarket.service.acp;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * QwenCodeConfigGenerator.generateSkillConfig 和 toKebabCase 单元测试。
 * 验证 Skill 配置注入到 .qwen/skills/ 目录的正确性。
 */
class QwenCodeConfigGeneratorSkillTest {

    @TempDir Path tempDir;

    private QwenCodeConfigGenerator generator;

    @BeforeEach
    void setUp() {
        generator = new QwenCodeConfigGenerator(new ObjectMapper());
    }

    // ========== generateSkillConfig 测试 ==========

    @Test
    void generateSkillConfig_nullList_noDirectoryCreated() throws IOException {
        generator.generateSkillConfig(tempDir.toString(), null);

        Path skillsDir = tempDir.resolve(".qwen/skills");
        assertFalse(Files.exists(skillsDir));
    }

    @Test
    void generateSkillConfig_emptyList_noDirectoryCreated() throws IOException {
        generator.generateSkillConfig(tempDir.toString(), List.of());

        Path skillsDir = tempDir.resolve(".qwen/skills");
        assertFalse(Files.exists(skillsDir));
    }

    @Test
    void generateSkillConfig_singleSkill_createsSkillMdFile() throws IOException {
        CliSessionConfig.SkillEntry skill = new CliSessionConfig.SkillEntry();
        skill.setName("My Skill");
        skill.setSkillMdContent("# My Skill\nThis is a test skill.");

        generator.generateSkillConfig(tempDir.toString(), List.of(skill));

        Path skillMd = tempDir.resolve(".qwen/skills/my-skill/SKILL.md");
        assertTrue(Files.exists(skillMd));
        assertEquals("# My Skill\nThis is a test skill.", Files.readString(skillMd));
    }

    @Test
    void generateSkillConfig_multipleSkills_createsAllFiles() throws IOException {
        CliSessionConfig.SkillEntry skill1 = new CliSessionConfig.SkillEntry();
        skill1.setName("Skill One");
        skill1.setSkillMdContent("Content one");

        CliSessionConfig.SkillEntry skill2 = new CliSessionConfig.SkillEntry();
        skill2.setName("Skill Two");
        skill2.setSkillMdContent("Content two");

        generator.generateSkillConfig(tempDir.toString(), List.of(skill1, skill2));

        Path md1 = tempDir.resolve(".qwen/skills/skill-one/SKILL.md");
        Path md2 = tempDir.resolve(".qwen/skills/skill-two/SKILL.md");
        assertTrue(Files.exists(md1));
        assertTrue(Files.exists(md2));
        assertEquals("Content one", Files.readString(md1));
        assertEquals("Content two", Files.readString(md2));
    }

    @Test
    void generateSkillConfig_existingFile_overwritesContent() throws IOException {
        // 先创建已有文件
        Path skillDir = tempDir.resolve(".qwen/skills/my-skill");
        Files.createDirectories(skillDir);
        Files.writeString(skillDir.resolve("SKILL.md"), "Old content");

        CliSessionConfig.SkillEntry skill = new CliSessionConfig.SkillEntry();
        skill.setName("My Skill");
        skill.setSkillMdContent("New content");

        generator.generateSkillConfig(tempDir.toString(), List.of(skill));

        assertEquals("New content", Files.readString(skillDir.resolve("SKILL.md")));
    }

    // ========== toKebabCase 测试 ==========

    @Test
    void toKebabCase_simpleSpaces_replacedWithDashes() {
        assertEquals("my-skill-name", QwenCodeConfigGenerator.toKebabCase("My Skill Name"));
    }

    @Test
    void toKebabCase_specialCharacters_removed() {
        assertEquals("my-skill-name", QwenCodeConfigGenerator.toKebabCase("My Skill Name!"));
    }

    @Test
    void toKebabCase_multipleSpaces_singleDash() {
        assertEquals("a-b", QwenCodeConfigGenerator.toKebabCase("a   b"));
    }

    @Test
    void toKebabCase_leadingTrailingSpecialChars_trimmed() {
        assertEquals("hello", QwenCodeConfigGenerator.toKebabCase("--hello--"));
    }

    @Test
    void toKebabCase_mixedCaseAndNumbers_preserved() {
        assertEquals("skill-v2-beta", QwenCodeConfigGenerator.toKebabCase("Skill V2 Beta"));
    }

    @Test
    void toKebabCase_nullInput_returnsEmpty() {
        assertEquals("", QwenCodeConfigGenerator.toKebabCase(null));
    }

    @Test
    void toKebabCase_blankInput_returnsEmpty() {
        assertEquals("", QwenCodeConfigGenerator.toKebabCase("   "));
    }

    @Test
    void toKebabCase_alreadyKebab_unchanged() {
        assertEquals("already-kebab", QwenCodeConfigGenerator.toKebabCase("already-kebab"));
    }

    @Test
    void toKebabCase_consecutiveSpecialChars_removed() {
        assertEquals("ab", QwenCodeConfigGenerator.toKebabCase("a@#$b"));
    }

    @Test
    void toKebabCase_specialCharsWithSpaces_collapsedToSingleDash() {
        assertEquals("a-b", QwenCodeConfigGenerator.toKebabCase("a @ b"));
    }

    @Test
    void toKebabCase_camelCase_lowered() {
        assertEquals("camelcase", QwenCodeConfigGenerator.toKebabCase("CamelCase"));
    }
}
