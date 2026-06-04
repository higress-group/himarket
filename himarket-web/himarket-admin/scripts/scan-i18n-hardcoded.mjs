import { readdirSync, readFileSync, statSync } from 'node:fs';
import { join, relative } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = join(fileURLToPath(new URL('..', import.meta.url)), 'src');
const cjkPattern = /[\u4e00-\u9fff]/;
const sourceExtensions = new Set(['.ts', '.tsx']);
const ignoredFiles = new Set(['src/i18n/locales.ts']);
const ignoredLinePatterns = [
  /^\s*\/\//,
  /^\s*\*/,
  /console\./,
  /toLocaleString\(['"]zh-CN['"]\)/,
  /name:\s*['"]头像\.png['"]/,
];

function walk(dir) {
  return readdirSync(dir).flatMap((entry) => {
    const path = join(dir, entry);
    const stat = statSync(path);
    if (stat.isDirectory()) {
      return walk(path);
    }
    return [path];
  });
}

function extensionOf(path) {
  const match = path.match(/\.[^.]+$/);
  return match?.[0] ?? '';
}

function stripBlockComments(content) {
  return content.replace(/\/\*[\s\S]*?\*\//g, (match) =>
    match
      .split('\n')
      .map(() => '')
      .join('\n'),
  );
}

function stripLineComment(line) {
  let quote = null;
  let escaped = false;

  for (let index = 0; index < line.length - 1; index += 1) {
    const char = line[index];
    const next = line[index + 1];

    if (escaped) {
      escaped = false;
      continue;
    }
    if (char === '\\') {
      escaped = true;
      continue;
    }
    if (quote) {
      if (char === quote) {
        quote = null;
      }
      continue;
    }
    if (char === '"' || char === "'" || char === '`') {
      quote = char;
      continue;
    }
    if (char === '/' && next === '/') {
      return line.slice(0, index);
    }
  }

  return line;
}

const findings = [];

for (const file of walk(root)) {
  if (!sourceExtensions.has(extensionOf(file))) {
    continue;
  }

  const relativePath = relative(join(root, '..'), file);
  if (ignoredFiles.has(relativePath) || relativePath.endsWith('.d.ts')) {
    continue;
  }

  const content = stripBlockComments(readFileSync(file, 'utf8'));
  content.split('\n').forEach((line, index) => {
    const code = stripLineComment(line);
    if (!cjkPattern.test(code)) {
      return;
    }
    if (ignoredLinePatterns.some((pattern) => pattern.test(code))) {
      return;
    }
    findings.push({
      line: index + 1,
      path: relativePath,
      text: code.trim(),
    });
  });
}

if (findings.length === 0) {
  console.log('No hardcoded Chinese candidates found outside src/i18n/locales.ts.');
  process.exit(0);
}

console.log(`Found ${findings.length} hardcoded Chinese candidate(s):`);
for (const finding of findings) {
  console.log(`${finding.path}:${finding.line}: ${finding.text}`);
}

if (process.argv.includes('--fail')) {
  process.exit(1);
}
