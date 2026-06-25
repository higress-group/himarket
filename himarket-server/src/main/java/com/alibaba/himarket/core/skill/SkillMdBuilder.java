package com.alibaba.himarket.core.skill;

import com.alibaba.nacos.api.ai.model.skills.Skill;

/**
 * Builds SKILL.md content from a Nacos Skill object.
 * Format: YAML frontmatter (name, description) plus instruction body.
 */
public final class SkillMdBuilder {

    private static final String FRONTMATTER_DELIMITER = "---";

    /**
     * Builds SKILL.md content from a Skill object.
     *
     * <p>If skillMd already contains YAML frontmatter (starts with ---), returns it as-is to avoid
     * duplicating name and description.
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
