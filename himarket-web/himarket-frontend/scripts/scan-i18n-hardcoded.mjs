import { existsSync, readdirSync, readFileSync, statSync, writeFileSync } from 'node:fs';
import { join, relative } from 'node:path';
import { fileURLToPath } from 'node:url';

const appRoot = fileURLToPath(new URL('..', import.meta.url));
const sourceRoot = join(appRoot, 'src');
const baselinePath = join(appRoot, 'scripts/i18n-hardcoded-baseline.json');
const cjkPattern = /[\u4e00-\u9fff]/;
const sourceExtensions = new Set(['.ts', '.tsx']);
const shouldFail = process.argv.includes('--fail');
const shouldUpdateBaseline = process.argv.includes('--update-baseline');
const ignoredPathPatterns = [
  /^src\/__tests__\//,
  /^src\/locales\//,
  /^src\/test\//,
  /\.test\.[tj]sx?$/,
];
const ignoredLinePatterns = [
  /^\s*\/\//,
  /^\s*\*/,
  /console\./,
  /toLocaleString\(['"]zh-CN['"]\)/,
  /toLocaleDateString\(['"]zh-CN['"]\)/,
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

function normalizeText(text) {
  return text.replace(/\s+/g, ' ').trim();
}

function findingId(finding) {
  return `${finding.path}\u0000${normalizeText(finding.text)}`;
}

function readBaseline() {
  if (!existsSync(baselinePath)) {
    return new Set();
  }

  const baseline = JSON.parse(readFileSync(baselinePath, 'utf8'));
  const allowed = Array.isArray(baseline) ? baseline : baseline.allowed;

  return new Set(
    (allowed ?? []).map((item) => {
      if (typeof item === 'string') {
        return item;
      }
      return `${item.path}\u0000${normalizeText(item.text)}`;
    }),
  );
}

function writeBaseline(findingsToWrite) {
  const allowed = findingsToWrite
    .map((finding) => ({
      path: finding.path,
      text: normalizeText(finding.text),
    }))
    .sort((left, right) => {
      if (left.path === right.path) {
        return left.text.localeCompare(right.text);
      }
      return left.path.localeCompare(right.path);
    });

  writeFileSync(
    baselinePath,
    JSON.stringify(
      {
        allowed,
        description:
          'Known hardcoded Chinese candidates in developer portal source. The i18n scan fails only on candidates not listed here.',
      },
      null,
      2,
    ) + '\n',
  );
}

const findings = [];

for (const file of walk(sourceRoot)) {
  if (!sourceExtensions.has(extensionOf(file))) {
    continue;
  }

  const relativePath = relative(appRoot, file);
  if (ignoredPathPatterns.some((pattern) => pattern.test(relativePath))) {
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

if (shouldUpdateBaseline) {
  writeBaseline(findings);
  process.stdout.write(`Updated i18n hardcoded baseline with ${findings.length} candidate(s).\n`);
  process.exit(0);
}

const baseline = readBaseline();
const newFindings = findings.filter((finding) => !baseline.has(findingId(finding)));
const baselineCount = findings.length - newFindings.length;

if (newFindings.length === 0) {
  process.stdout.write('No new hardcoded Chinese candidates found outside src/locales.\n');
  if (baselineCount > 0) {
    process.stdout.write(`${baselineCount} existing candidate(s) are covered by baseline.\n`);
  }
  process.exit(0);
}

process.stdout.write(`Found ${newFindings.length} new hardcoded Chinese candidate(s):\n`);
for (const finding of newFindings) {
  process.stdout.write(`${finding.path}:${finding.line}: ${finding.text}\n`);
}

if (shouldFail) {
  process.exit(1);
}
