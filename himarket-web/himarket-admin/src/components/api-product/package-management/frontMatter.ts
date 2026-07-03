export function parseFrontMatter(content: string): {
  frontmatter: Record<string, string>;
  body: string;
} {
  const trimmed = content.trim();
  if (!trimmed.startsWith('---')) return { body: trimmed, frontmatter: {} };
  const secondDash = trimmed.indexOf('---', 3);
  if (secondDash === -1) return { body: trimmed, frontmatter: {} };
  const yamlBlock = trimmed.substring(3, secondDash).trim();
  const body = trimmed.substring(secondDash + 3).trim();
  const frontmatter: Record<string, string> = {};
  const lines = yamlBlock.split('\n');
  let currentKey = '';
  let currentValue = '';
  let multilineIndent = -1;

  for (let i = 0; i < lines.length; i++) {
    const line = lines[i];
    if (line === undefined) continue;
    if (multilineIndent >= 0) {
      const stripped = line.replace(/^\s*/, '');
      const indent = line.length - stripped.length;
      if (indent > multilineIndent || stripped === '') {
        currentValue += (currentValue ? '\n' : '') + stripped;
        continue;
      }
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
      if (value === '|' || value === '>' || value === '|-' || value === '>-') {
        currentKey = key;
        currentValue = '';
        multilineIndent = line.length - line.trimStart().length;
        continue;
      }
      if (
        (value.startsWith('"') && value.endsWith('"')) ||
        (value.startsWith("'") && value.endsWith("'"))
      ) {
        value = value.slice(1, -1);
      }
      frontmatter[key] = value;
    }
  }
  if (multilineIndent >= 0 && currentKey) {
    frontmatter[currentKey] = currentValue.trim();
  }
  return { body, frontmatter };
}
