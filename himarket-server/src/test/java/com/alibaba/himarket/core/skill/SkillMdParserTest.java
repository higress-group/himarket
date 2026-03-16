package com.alibaba.himarket.core.skill;

import static org.junit.jupiter.api.Assertions.*;

import com.alibaba.himarket.core.exception.BusinessException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SkillMdParserTest {

    private SkillMdParser parser;

    @BeforeEach
    void setUp() {
        parser = new SkillMdParser();
    }

    // ========== parse 测试 ==========

    @Test
    void testParseStandardSkillMd() {
        String content =
                """
                ---
                name: my-skill
                description: "技能描述"
                ---
                # 技能标题

                ## 使用说明

                正文内容
                """;

        SkillMdDocument doc = parser.parse(content);

        assertEquals("my-skill", doc.getFrontmatter().get("name"));
        assertEquals("技能描述", doc.getFrontmatter().get("description"));
        assertTrue(doc.getBody().contains("# 技能标题"));
        assertTrue(doc.getBody().contains("正文内容"));
    }

    @Test
    void testParseEmptyBody() {
        String content =
                """
                ---
                name: my-skill
                ---
                """;

        SkillMdDocument doc = parser.parse(content);

        assertEquals("my-skill", doc.getFrontmatter().get("name"));
        assertEquals("", doc.getBody());
    }

    @Test
    void testParseEmptyFrontmatter() {
        String content =
                """
                ---
                ---
                # Body only
                """;

        SkillMdDocument doc = parser.parse(content);

        assertTrue(doc.getFrontmatter().isEmpty());
        assertTrue(doc.getBody().contains("# Body only"));
    }

    @Test
    void testParseNullContentThrows() {
        BusinessException ex = assertThrows(BusinessException.class, () -> parser.parse(null));
        assertTrue(ex.getMessage().contains("内容不能为空"));
    }

    @Test
    void testParseEmptyContentThrows() {
        BusinessException ex = assertThrows(BusinessException.class, () -> parser.parse(""));
        assertTrue(ex.getMessage().contains("内容不能为空"));
    }

    @Test
    void testParseMissingOpeningDelimiterThrows() {
        String content = "name: my-skill\n---\n# Body";
        BusinessException ex = assertThrows(BusinessException.class, () -> parser.parse(content));
        assertTrue(ex.getMessage().contains("缺少 YAML frontmatter 分隔符"));
    }

    @Test
    void testParseMissingClosingDelimiterThrows() {
        String content = "---\nname: my-skill\n# Body without closing delimiter";
        BusinessException ex = assertThrows(BusinessException.class, () -> parser.parse(content));
        assertTrue(ex.getMessage().contains("缺少 YAML frontmatter 结束分隔符"));
    }

    @Test
    void testParseInvalidYamlThrows() {
        String content = "---\n: invalid: yaml: [broken\n---\n# Body";
        BusinessException ex = assertThrows(BusinessException.class, () -> parser.parse(content));
        assertTrue(ex.getMessage().contains("YAML frontmatter 解析失败"));
    }

    @Test
    void testParseYamlNotMapThrows() {
        String content = "---\n- item1\n- item2\n---\n# Body";
        BusinessException ex = assertThrows(BusinessException.class, () -> parser.parse(content));
        assertTrue(ex.getMessage().contains("期望键值对映射"));
    }

    // ========== serialize 测试 ==========

    @Test
    void testSerializeStandardDocument() {
        Map<String, Object> frontmatter = new LinkedHashMap<>();
        frontmatter.put("name", "my-skill");
        frontmatter.put("description", "技能描述");
        SkillMdDocument doc = new SkillMdDocument(frontmatter, "# 技能标题\n\n正文内容\n");

        String result = parser.serialize(doc);

        assertTrue(result.startsWith("---\n"));
        assertTrue(result.contains("name: my-skill"));
        assertTrue(result.contains("description: 技能描述"));
        assertTrue(result.contains("---\n# 技能标题"));
        assertTrue(result.contains("正文内容"));
    }

    @Test
    void testSerializeEmptyFrontmatter() {
        SkillMdDocument doc = new SkillMdDocument(new LinkedHashMap<>(), "# Body\n");

        String result = parser.serialize(doc);

        assertTrue(result.startsWith("---\n---\n"));
        assertTrue(result.contains("# Body"));
    }

    @Test
    void testSerializeEmptyBody() {
        Map<String, Object> frontmatter = new LinkedHashMap<>();
        frontmatter.put("name", "test");
        SkillMdDocument doc = new SkillMdDocument(frontmatter, "");

        String result = parser.serialize(doc);

        assertTrue(result.contains("name: test"));
        assertTrue(result.endsWith("---\n"));
    }

    @Test
    void testSerializeNullDocumentThrows() {
        BusinessException ex = assertThrows(BusinessException.class, () -> parser.serialize(null));
        assertTrue(ex.getMessage().contains("不能为空"));
    }

    // ========== 往返一致性测试 ==========

    @Test
    void testRoundTripConsistency() {
        String original =
                """
                ---
                name: my-skill
                description: "技能描述"
                ---
                # 技能标题

                ## 使用说明

                正文内容
                """;

        SkillMdDocument doc1 = parser.parse(original);
        String serialized = parser.serialize(doc1);
        SkillMdDocument doc2 = parser.parse(serialized);

        assertEquals(doc1.getFrontmatter(), doc2.getFrontmatter());
        assertEquals(doc1.getBody(), doc2.getBody());
    }
}
