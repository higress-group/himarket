/**
 * SKILL.md 解析工具
 * 将 YAML frontmatter + Markdown body 分离
 */

export interface SkillMdParsed {
  frontmatter: Record<string, string>;
  body: string;
}

/**
 * 解析 SKILL.md 内容，分离 YAML frontmatter 和 Markdown body
 * frontmatter 以 --- 开头和结尾
 */
export function parseSkillMd(content: string): SkillMdParsed {
  if (!content) {
    return { frontmatter: {}, body: "" };
  }

  const trimmed = content.trim();

  // 检查是否以 --- 开头
  if (!trimmed.startsWith("---")) {
    return { frontmatter: {}, body: trimmed };
  }

  // 找到第二个 ---
  const secondDash = trimmed.indexOf("---", 3);
  if (secondDash === -1) {
    return { frontmatter: {}, body: trimmed };
  }

  const yamlBlock = trimmed.substring(3, secondDash).trim();
  const body = trimmed.substring(secondDash + 3).trim();

  // 简单解析 YAML key: value
  const frontmatter: Record<string, string> = {};
  for (const line of yamlBlock.split("\n")) {
    const colonIdx = line.indexOf(":");
    if (colonIdx > 0) {
      const key = line.substring(0, colonIdx).trim();
      let value = line.substring(colonIdx + 1).trim();
      // 去掉引号
      if ((value.startsWith('"') && value.endsWith('"')) ||
          (value.startsWith("'") && value.endsWith("'"))) {
        value = value.slice(1, -1);
      }
      frontmatter[key] = value;
    }
  }

  return { frontmatter, body };
}

/**
 * 从 SKILL.md 内容中提取纯 Markdown body（去掉 frontmatter）
 */
export function getSkillMdBody(content: string): string {
  return parseSkillMd(content).body;
}
