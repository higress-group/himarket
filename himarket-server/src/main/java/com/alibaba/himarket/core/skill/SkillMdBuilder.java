package com.alibaba.himarket.core.skill;

import com.alibaba.nacos.api.ai.model.skills.Skill;

/**
 * 从 Nacos Skill 对象拼装 SKILL.md 内容。
 * 格式：YAML frontmatter（name、description）+ instruction 正文。
 */
public final class SkillMdBuilder {

    private SkillMdBuilder() {}

    /**
     * 从 Skill 对象生成 SKILL.md 内容。
     */
    public static String build(Skill skill) {
        StringBuilder sb = new StringBuilder();
        sb.append("---\n");
        sb.append("name: ").append(skill.getName() != null ? skill.getName() : "").append("\n");
        sb.append("description: ")
                .append(skill.getDescription() != null ? skill.getDescription() : "")
                .append("\n");
        sb.append("---\n\n");
        if (skill.getInstruction() != null) {
            sb.append(skill.getInstruction());
        }
        return sb.toString();
    }
}
