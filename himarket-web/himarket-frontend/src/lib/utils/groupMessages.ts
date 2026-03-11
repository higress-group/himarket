import type {
  ChatItem,
  ChatItemToolCall,
  ChatItemAgent,
} from "../../types/coding-protocol";

// ===== MCP Detection (inlined to avoid circular deps with ToolCallCard) =====

/** Detect if a tool_call is an MCP tool invocation by title pattern */
function isMcpToolCall(item: ChatItemToolCall): boolean {
  if (item.kind !== "other") return false;
  return /\(.+\s+MCP Server\)\s*:/.test(item.title || "");
}

// ===== Types =====

export interface ToolsSummary {
  files: number;
  searches: number;
  edits: number;
  executes: number;
  fetches: number;
  thinks: number;
  skills: number;
  mcpCalls: number;
  others: number;
}

export interface ActivityGroup {
  id: string;
  blocks: ChatItem[];
  isExploring: boolean;
  isThinkingOnly: boolean;
  isEditOnly: boolean;
  editFilePath?: string;
  toolsSummary: ToolsSummary;
  hasErrorTool: boolean;
}

export type RenderItem =
  | { type: "single"; item: ChatItem }
  | { type: "activity_group"; group: ActivityGroup };

// ===== Helpers =====

function extractFilePath(item: ChatItemToolCall): string | null {
  if (item.rawInput) {
    if (typeof item.rawInput.file_path === "string")
      return item.rawInput.file_path;
    if (typeof item.rawInput.path === "string") return item.rawInput.path;
  }
  if (item.locations && item.locations.length > 0)
    return item.locations[0].path;
  return null;
}

function isShortText(text: string): boolean {
  if (text.length > 150) return false;
  if (text.split("\n").length > 2) return false;
  if (text.includes("```")) return false;
  return true;
}

// ===== shouldFoldBlock =====

/**
 * Determines whether a block should be folded into an activity group.
 *
 * Priority rules:
 * 1. user / plan / error → never fold
 * 2. tool_call:
 *    a. failed → fold (reduce noise from error tools)
 *    b. switch_mode → never fold
 *    c. skill → never fold
 *    d. edit → fold (modify; create/delete already separated by kind=delete/move)
 *    e. execute → never fold
 *    f. read/search/think/fetch → fold (exploration tools)
 *    g. other + MCP → never fold
 *    h. other + non-MCP → fold
 * 3. thought → fold
 * 4. agent:
 *    a. not in group → never fold
 *    b. in group + short text (≤150 chars, ≤2 lines, no code blocks) → fold
 *    c. otherwise → never fold
 */
function shouldFoldBlock(item: ChatItem, isInGroup: boolean): boolean {
  switch (item.type) {
    case "user":
    case "plan":
    case "error":
      return false;

    case "tool_call": {
      const tc = item as ChatItemToolCall;
      // Failed tools → fold into group to reduce visual noise
      if (tc.status === "failed") return true;
      switch (tc.kind) {
        case "switch_mode":
          return false;
        case "skill":
          return false;
        case "edit":
          return true; // modify operations fold
        case "execute":
          return false;
        case "read":
        case "search":
        case "think":
        case "fetch":
          return true; // exploration tools fold
        case "delete":
        case "move":
          return false;
        case "other":
          return !isMcpToolCall(tc); // MCP calls don't fold
        default:
          return false;
      }
    }

    case "thought":
      return true;

    case "agent": {
      if (!isInGroup) return false;
      const a = item as ChatItemAgent;
      return isShortText(a.text);
    }

    default:
      return false;
  }
}

// ===== shouldEndGroup =====

/**
 * Determines whether the current group should be closed before adding nextItem.
 * Handles same-file edit aggregation boundaries.
 */
function shouldEndGroup(currentGroup: ChatItem[], nextItem: ChatItem): boolean {
  const hasEditInGroup = currentGroup.some(
    b => b.type === "tool_call" && (b as ChatItemToolCall).kind === "edit"
  );

  if (nextItem.type === "tool_call") {
    const isNextEdit = (nextItem as ChatItemToolCall).kind === "edit";

    // Current group has no edit + next is edit → end group (edits start own group)
    if (!hasEditInGroup && isNextEdit) return true;

    // Current group has edit + next is not edit → end group
    if (hasEditInGroup && !isNextEdit) return true;

    // Both are edits → check file path
    if (hasEditInGroup && isNextEdit) {
      const groupFilePath = getFirstEditFilePath(currentGroup);
      const nextFilePath = extractFilePath(nextItem as ChatItemToolCall);
      if (groupFilePath && nextFilePath && groupFilePath !== nextFilePath) {
        return true; // Different file → end group
      }
      return false; // Same file → continue
    }
  }

  return false;
}

function getFirstEditFilePath(blocks: ChatItem[]): string | null {
  for (const b of blocks) {
    if (b.type === "tool_call" && (b as ChatItemToolCall).kind === "edit") {
      return extractFilePath(b as ChatItemToolCall);
    }
  }
  return null;
}

// ===== Last Message Protection =====

/**
 * Find the index of the last agent message that should be protected from folding.
 * When the session is complete (!isProcessing), the last agent message in the
 * conversation is protected so the user always sees the final reply.
 * During processing, no protection is applied (the last message is still changing).
 */
function findProtectedIndex(
  messages: ChatItem[],
  isProcessing: boolean
): number {
  if (isProcessing) return -1;
  for (let i = messages.length - 1; i >= 0; i--) {
    if (messages[i].type === "agent") return i;
  }
  return -1;
}

// ===== createActivityGroup =====

function createActivityGroup(blocks: ChatItem[]): ActivityGroup {
  const summary: ToolsSummary = {
    files: 0,
    searches: 0,
    edits: 0,
    executes: 0,
    fetches: 0,
    thinks: 0,
    skills: 0,
    mcpCalls: 0,
    others: 0,
  };

  let hasErrorTool = false;
  const readFilePaths = new Set<string>();
  let isThinkingOnly = true;
  let isEditOnly = true;
  let editFilePath: string | undefined;
  let hasAnyToolCall = false;

  for (const block of blocks) {
    if (block.type === "tool_call") {
      const tc = block as ChatItemToolCall;
      hasAnyToolCall = true;

      if (tc.status === "failed") hasErrorTool = true;

      const isSkill = tc.kind === "skill" || /^Skill\s+/i.test(tc.title || "");
      if (isSkill) {
        summary.skills++;
        isThinkingOnly = false;
        isEditOnly = false;
      } else {
        switch (tc.kind) {
          case "read": {
            const fp = extractFilePath(tc);
            if (fp) readFilePaths.add(fp);
            isThinkingOnly = false;
            isEditOnly = false;
            break;
          }
          case "search":
            summary.searches++;
            isThinkingOnly = false;
            isEditOnly = false;
            break;
          case "edit": {
            summary.edits++;
            isThinkingOnly = false;
            const fp = extractFilePath(tc);
            if (editFilePath === undefined) {
              editFilePath = fp ?? undefined;
            } else if (fp && fp !== editFilePath) {
              isEditOnly = false; // Different files
            }
            break;
          }
          case "execute":
            summary.executes++;
            isThinkingOnly = false;
            isEditOnly = false;
            break;
          case "think":
            summary.thinks++;
            isEditOnly = false;
            break;
          case "fetch":
            summary.fetches++;
            isThinkingOnly = false;
            isEditOnly = false;
            break;
          case "delete":
          case "move":
            isThinkingOnly = false;
            isEditOnly = false;
            summary.others++;
            break;
          case "switch_mode":
            isThinkingOnly = false;
            isEditOnly = false;
            summary.others++;
            break;
          case "other":
            isThinkingOnly = false;
            isEditOnly = false;
            if (isMcpToolCall(tc)) {
              summary.mcpCalls++;
            } else {
              summary.others++;
            }
            break;
          default:
            isThinkingOnly = false;
            isEditOnly = false;
            summary.others++;
            break;
        }
      }
    } else if (block.type === "thought") {
      // thought is compatible with both isThinkingOnly and isEditOnly
      isEditOnly = false; // thoughts don't contribute to edit-only
    } else if (block.type === "agent") {
      // Short agent text in a group is OK for thinking-only
      isEditOnly = false;
    }
  }

  summary.files = readFilePaths.size;

  // If there are no tool calls, it's a thinking-only group
  if (!hasAnyToolCall) {
    isEditOnly = false;
  }

  // If edits count is 0, not edit-only
  if (summary.edits === 0) {
    isEditOnly = false;
  }

  // isEditOnly requires only edit tool calls (+ thoughts/agents are OK)
  if (isEditOnly) {
    for (const block of blocks) {
      if (block.type === "tool_call") {
        const tc = block as ChatItemToolCall;
        if (tc.kind !== "edit") {
          isEditOnly = false;
          break;
        }
      }
    }
  }

  return {
    id: `ag-${blocks[0].id}`,
    blocks,
    isExploring: false, // Set later by setIsExploringForLastGroup
    isThinkingOnly,
    isEditOnly,
    editFilePath: isEditOnly ? editFilePath : undefined,
    toolsSummary: summary,
    hasErrorTool,
  };
}

// ===== isExploring =====

function setIsExploringForLastGroup(
  result: RenderItem[],
  isProcessing: boolean
): void {
  if (!isProcessing) return;

  // Find the last activity_group
  let lastGroupIdx = -1;
  for (let i = result.length - 1; i >= 0; i--) {
    if (result[i].type === "activity_group") {
      lastGroupIdx = i;
      break;
    }
  }
  if (lastGroupIdx === -1) return;

  // Check no single blocks after it
  for (let i = lastGroupIdx + 1; i < result.length; i++) {
    if (result[i].type === "single") return;
  }

  // Check at least one tool is still running
  const group = (
    result[lastGroupIdx] as { type: "activity_group"; group: ActivityGroup }
  ).group;
  const hasRunningTool = group.blocks.some(
    b =>
      b.type === "tool_call" &&
      ((b as ChatItemToolCall).status === "pending" ||
        (b as ChatItemToolCall).status === "in_progress")
  );

  if (hasRunningTool) {
    group.isExploring = true;
  }
}

// ===== Flush helper =====

function flushGroup(result: RenderItem[], currentGroup: ChatItem[]): void {
  if (currentGroup.length === 0) return;

  if (currentGroup.length === 1) {
    // Single block: re-check if it should be folded when standalone
    const item = currentGroup[0];
    const shouldFold = shouldFoldBlock(item, false);
    if (!shouldFold) {
      result.push({ type: "single", item });
      return;
    }
  }

  const group = createActivityGroup(currentGroup);
  result.push({ type: "activity_group", group });
}

// ===== Main Grouping Algorithm =====

/**
 * Groups messages into semantic activity groups using per-block fold decisions.
 *
 * Key improvements over the previous work_unit approach:
 * - shouldFoldBlock: per-block decision based on tool kind and status
 * - Last message protection: agent's final reply is never folded
 * - isExploring: precise 3-condition detection for the last active group
 * - Error tool folding: failed tools fold into groups to reduce noise
 * - Same-file edit aggregation: consecutive modify edits on same file group together
 * - Short text folding: agent short text (≤150 chars) folds when in a group
 */
export function groupMessages(
  messages: ChatItem[],
  isProcessing: boolean = false
): RenderItem[] {
  const result: RenderItem[] = [];
  const len = messages.length;
  const protectedIndex = findProtectedIndex(messages, isProcessing);
  let currentGroup: ChatItem[] = [];

  for (let i = 0; i < len; i++) {
    const item = messages[i];

    // Protected last message: always render as single
    if (i === protectedIndex) {
      flushGroup(result, currentGroup);
      currentGroup = [];
      result.push({ type: "single", item });
      continue;
    }

    const isInGroup = currentGroup.length > 0;
    const fold = shouldFoldBlock(item, isInGroup);

    if (fold) {
      // Check group boundary before adding
      if (isInGroup && shouldEndGroup(currentGroup, item)) {
        flushGroup(result, currentGroup);
        currentGroup = [];
      }
      currentGroup.push(item);
    } else {
      // Not foldable: flush current group, emit as single
      flushGroup(result, currentGroup);
      currentGroup = [];
      result.push({ type: "single", item });
    }
  }

  // Flush remaining group
  flushGroup(result, currentGroup);

  // Compute isExploring for the last group
  setIsExploringForLastGroup(result, isProcessing);

  return result;
}
