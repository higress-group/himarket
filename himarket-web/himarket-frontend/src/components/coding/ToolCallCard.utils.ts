import type { ChatItemToolCall, ToolCallContentItem } from '../../types/coding-protocol';

/** Get just the basename from a full file path */
export function extractFileName(path: string): string {
  const parts = path.split(/[/\\]/);
  return parts[parts.length - 1] || path;
}

export function getDiffStats(
  content?: ToolCallContentItem[],
): { added: number; removed: number } | null {
  if (!content) return null;
  const diffs = content.filter((c) => c.type === 'diff');
  if (diffs.length === 0) return null;

  let added = 0;
  let removed = 0;
  for (const d of diffs) {
    if (d.newText) added += d.newText.split('\n').length;
    if (d.oldText) removed += d.oldText.split('\n').length;
  }

  return added > 0 || removed > 0 ? { added, removed } : null;
}

/** Detect if a tool_call is an MCP tool invocation */
export function isMcpItem(item: ChatItemToolCall): boolean {
  if (item.kind !== 'other') return false;
  return /\(.+\s+MCP Server\)\s*:/.test(item.title || '');
}
