import { ARTIFACT_EXTENSIONS, type Artifact, type ArtifactType } from '../../types/artifact';

import type { ChatItemToolCall } from '../../types/coding-protocol';

export function getArtifactType(filePath: string): ArtifactType | null {
  const lastDot = filePath.lastIndexOf('.');
  if (lastDot === -1) return null;
  const ext = filePath.slice(lastDot).toLowerCase();
  return ARTIFACT_EXTENSIONS[ext] ?? null;
}
/**
 * 归一化文件路径，消除格式差异：
 * - 移除 "./" 前缀
 * - 合并连续的 "/"
 * - 移除末尾 "/"
 */
export function normalizePath(filePath: string): string {
  let p = filePath;
  // 移除 "./" 前缀（可能多次出现如 "././"）
  while (p.startsWith('./')) {
    p = p.slice(2);
  }
  // 合并连续的 "/"
  p = p.replace(/\/+/g, '/');
  // 移除末尾 "/"（但保留单独的 "/"）
  if (p.length > 1 && p.endsWith('/')) {
    p = p.slice(0, -1);
  }
  return p;
}

/** Collect all unique file paths from a tool call (rawInput + locations). */
function extractAllPaths(toolCall: ChatItemToolCall): string[] {
  const paths: string[] = [];
  const seen = new Set<string>();

  // Priority 1: explicit file_path / path from rawInput (Write / Edit tools)
  if (toolCall.rawInput) {
    const fp =
      typeof toolCall.rawInput.file_path === 'string'
        ? toolCall.rawInput.file_path
        : typeof toolCall.rawInput.path === 'string'
          ? toolCall.rawInput.path
          : null;
    if (fp) {
      const normalized = normalizePath(fp);
      if (!seen.has(normalized)) {
        paths.push(normalized);
        seen.add(normalized);
      }
    }
  }

  // Priority 2: all entries in locations (Bash / other tools that create files)
  if (toolCall.locations) {
    for (const loc of toolCall.locations) {
      if (loc.path) {
        const normalized = normalizePath(loc.path);
        if (!seen.has(normalized)) {
          paths.push(normalized);
          seen.add(normalized);
        }
      }
    }
  }

  return paths;
}

function getFileName(filePath: string): string {
  const lastSlash = Math.max(filePath.lastIndexOf('/'), filePath.lastIndexOf('\\'));
  return lastSlash >= 0 ? filePath.slice(lastSlash + 1) : filePath;
}

/** File extensions that should NOT be treated as artifacts */
const IGNORED_EXTENSIONS = new Set([
  '.ts',
  '.tsx',
  '.js',
  '.jsx',
  '.json',
  '.css',
  '.scss',
  '.less',
  '.java',
  '.py',
  '.go',
  '.rs',
  '.rb',
  '.c',
  '.cpp',
  '.h',
  '.hpp',
  '.yaml',
  '.yml',
  '.toml',
  '.xml',
  '.lock',
  '.sh',
  '.bash',
  '.zsh',
  '.env',
  '.gitignore',
  '.editorconfig',
  '.eslintrc',
  '.prettierrc',
]);

let _artifactSeq = 0;

function buildArtifacts(paths: string[], toolCallId: string): Artifact[] {
  const now = Date.now();
  const results: Artifact[] = [];
  const seen = new Set<string>();

  for (const path of paths) {
    if (!path || seen.has(path)) continue;
    seen.add(path);

    const lastDot = path.lastIndexOf('.');
    if (lastDot === -1) continue;

    const ext = path.slice(lastDot).toLowerCase();
    if (IGNORED_EXTENSIONS.has(ext)) continue;

    const type = getArtifactType(path);
    if (!type) continue;

    results.push({
      content: null,
      fileName: getFileName(path),
      id: `artifact-${++_artifactSeq}`,
      path,
      toolCallId,
      type,
      updatedAt: now,
    });
  }

  return results;
}

/**
 * Detect artifacts from a raw path list (e.g. workspace scan results).
 */
export function detectArtifactsFromPaths(paths: string[], toolCallId: string): Artifact[] {
  if (!paths.length) return [];
  return buildArtifacts(paths, toolCallId);
}

/**
 * Detect artifacts from a tool call.
 * Iterates over ALL paths (rawInput + locations) so that files produced as
 * side-effects of Bash execution (e.g. .pptx, .pdf) are also captured.
 */
export function detectArtifacts(toolCall: ChatItemToolCall): Artifact[] {
  // Skip kinds that never create files
  const NON_CREATING_KINDS = new Set(['read', 'search', 'think', 'fetch', 'switch_mode']);
  if (NON_CREATING_KINDS.has(toolCall.kind)) return [];

  const paths = extractAllPaths(toolCall);
  if (paths.length === 0) return [];
  return buildArtifacts(paths, toolCall.toolCallId);
}
