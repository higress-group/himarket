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
    return { body: '', frontmatter: {} };
  }

  const trimmed = content.trim();

  // 检查是否以 --- 开头
  if (!trimmed.startsWith('---')) {
    return { body: trimmed, frontmatter: {} };
  }

  // 找到第二个 ---
  const secondDash = trimmed.indexOf('---', 3);
  if (secondDash === -1) {
    return { body: trimmed, frontmatter: {} };
  }

  const yamlBlock = trimmed.substring(3, secondDash).trim();
  const body = trimmed.substring(secondDash + 3).trim();

  // 解析 YAML key: value，支持多行值（| 和 > 语法）
  const frontmatter: Record<string, string> = {};
  const lines = yamlBlock.split('\n');
  let currentKey = '';
  let currentValue = '';
  let multilineIndent = -1;

  for (const line of lines) {
    // 如果正在收集多行值，检查缩进
    if (multilineIndent >= 0) {
      const stripped = line.replace(/^\s*/, '');
      const indent = line.length - stripped.length;
      if (indent > multilineIndent || stripped === '') {
        currentValue += (currentValue ? '\n' : '') + stripped;
        continue;
      }
      // 缩进结束，保存之前的多行值
      frontmatter[currentKey] = currentValue.trim();
      multilineIndent = -1;
    }

    const colonIdx = line.indexOf(':');
    if (
      colonIdx > 0 &&
      line.substring(0, colonIdx).trim() === line.substring(0, colonIdx).trimStart()
    ) {
      const key = line.substring(0, colonIdx).trim();
      let value = line.substring(colonIdx + 1).trim();

      if (value === '|' || value === '>' || value === '|−' || value === '>-') {
        // 多行值开始
        currentKey = key;
        currentValue = '';
        multilineIndent = line.length - line.trimStart().length;
        continue;
      }

      // 去掉引号
      if (
        (value.startsWith('"') && value.endsWith('"')) ||
        (value.startsWith("'") && value.endsWith("'"))
      ) {
        value = value.slice(1, -1);
      }
      frontmatter[key] = value;
    }
  }
  // 处理最后一个多行值
  if (multilineIndent >= 0 && currentKey) {
    frontmatter[currentKey] = currentValue.trim();
  }

  return { body, frontmatter };
}

/**
 * 从 SKILL.md 内容中提取纯 Markdown body（去掉 frontmatter）
 */
export function getSkillMdBody(content: string): string {
  return parseSkillMd(content).body;
}
