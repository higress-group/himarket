package com.alibaba.himarket.core.skill;

import com.alibaba.nacos.api.ai.model.skills.Skill;

/**
 * 从 Nacos Skill 对象拼装 SKILL.md 内容。
 * 格式：YAML frontmatter（name、description）+ instruction 正文。
 */
public final class SkillMdBuilder {

    private SkillMdBuilder() {}

    private static final String FRONTMATTER_DELIMITER = "---";

    /**
     * 从 Skill 对象生成 SKILL.md 内容。
     *
     * <p>如果 Skill 的 skillMd 已包含 YAML frontmatter（以 --- 开头），
     * 则直接返回原始内容，避免重复拼接 name/description。
     */
    public static String build(Skill skill) {
        String skillMd = skill.getSkillMd();
        if (skillMd != null && skillMd.stripLeading().startsWith(FRONTMATTER_DELIMITER)) {
            return skillMd;
        }

        StringBuilder sb = new StringBuilder();
        sb.append(FRONTMATTER_DELIMITER).append("\n");
        sb.append("name: ").append(skill.getName() != null ? skill.getName() : "").append("\n");
        sb.append("description: ")
                .append(skill.getDescription() != null ? skill.getDescription() : "")
                .append("\n");
        sb.append(FRONTMATTER_DELIMITER).append("\n\n");
        if (skillMd != null) {
            sb.append(skillMd);
        }
        return sb.toString();
    }
}
