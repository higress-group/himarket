import type { ChatItemToolCall } from "../../types/acp";
import {
  ARTIFACT_EXTENSIONS,
  type Artifact,
  type ArtifactType,
} from "../../types/artifact";

export function getArtifactType(filePath: string): ArtifactType | null {
  const lastDot = filePath.lastIndexOf(".");
  if (lastDot === -1) return null;
  const ext = filePath.slice(lastDot).toLowerCase();
  return ARTIFACT_EXTENSIONS[ext] ?? "file";
}

/** Collect all unique file paths from a tool call (rawInput + locations). */
function extractAllPaths(toolCall: ChatItemToolCall): string[] {
  const paths: string[] = [];
  const seen = new Set<string>();

  // Priority 1: explicit file_path / path from rawInput (Write / Edit tools)
  if (toolCall.rawInput) {
    const fp =
      typeof toolCall.rawInput.file_path === "string"
        ? toolCall.rawInput.file_path
        : typeof toolCall.rawInput.path === "string"
          ? toolCall.rawInput.path
          : null;
    if (fp && !seen.has(fp)) {
      paths.push(fp);
      seen.add(fp);
    }
  }

  // Priority 2: all entries in locations (Bash / other tools that create files)
  if (toolCall.locations) {
    for (const loc of toolCall.locations) {
      if (loc.path && !seen.has(loc.path)) {
        paths.push(loc.path);
        seen.add(loc.path);
      }
    }
  }

  return paths;
}

function getFileName(filePath: string): string {
  const lastSlash = Math.max(
    filePath.lastIndexOf("/"),
    filePath.lastIndexOf("\\")
  );
  return lastSlash >= 0 ? filePath.slice(lastSlash + 1) : filePath;
}

/** File extensions that should NOT be treated as artifacts */
const IGNORED_EXTENSIONS = new Set([
  ".ts",
  ".tsx",
  ".js",
  ".jsx",
  ".json",
  ".css",
  ".scss",
  ".less",
  ".java",
  ".py",
  ".go",
  ".rs",
  ".rb",
  ".c",
  ".cpp",
  ".h",
  ".hpp",
  ".yaml",
  ".yml",
  ".toml",
  ".xml",
  ".lock",
  ".sh",
  ".bash",
  ".zsh",
  ".env",
  ".gitignore",
  ".editorconfig",
  ".eslintrc",
  ".prettierrc",
]);

let _artifactSeq = 0;

function buildArtifacts(paths: string[], toolCallId: string): Artifact[] {
  const now = Date.now();
  const results: Artifact[] = [];
  const seen = new Set<string>();

  for (const path of paths) {
    if (!path || seen.has(path)) continue;
    seen.add(path);

    const lastDot = path.lastIndexOf(".");
    if (lastDot === -1) continue;

    const ext = path.slice(lastDot).toLowerCase();
    if (IGNORED_EXTENSIONS.has(ext)) continue;

    const type = getArtifactType(path);
    if (!type) continue;

    results.push({
      id: `artifact-${++_artifactSeq}`,
      toolCallId,
      type,
      path,
      fileName: getFileName(path),
      content: null,
      updatedAt: now,
    });
  }

  return results;
}

/**
 * Detect artifacts from a raw path list (e.g. workspace scan results).
 */
export function detectArtifactsFromPaths(
  paths: string[],
  toolCallId: string
): Artifact[] {
  if (!paths.length) return [];
  return buildArtifacts(paths, toolCallId);
}

/**
 * Detect artifacts from a tool call.
 * Iterates over ALL paths (rawInput + locations) so that files produced as
 * side-effects of Bash execution (e.g. .pptx, .pdf) are also captured.
 */
export function detectArtifacts(toolCall: ChatItemToolCall): Artifact[] {
  // Only detect for edit/execute kind tool calls
  if (toolCall.kind === "read") return [];

  const paths = extractAllPaths(toolCall);
  if (paths.length === 0) return [];
  return buildArtifacts(paths, toolCall.toolCallId);
}
