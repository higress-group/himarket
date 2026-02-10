import type { ChatItem, ChatItemToolCall } from "../../types/acp";

// ===== Render Item Types =====

export interface WorkUnitMeta {
  reasoningText?: string;
  hasReasoningHeader: boolean;
  readCount: number;
  editCount: number;
  executeCount: number;
  skillCount: number;
  toolCallCount: number;
  allCompleted: boolean;
  hasInProgress: boolean;
  hasFailed: boolean;
}

export type RenderItem =
  | { type: "single"; item: ChatItem }
  | { type: "work_unit"; id: string; items: ChatItem[]; meta: WorkUnitMeta };

function buildWorkUnitId(items: ChatItem[]): string {
  // Keep key stable while the unit grows during streaming updates.
  return `wu-${items[0].id}`;
}

// ===== Meta Computation =====

export function computeWorkUnitMeta(items: ChatItem[]): WorkUnitMeta {
  let readCount = 0;
  let editCount = 0;
  let executeCount = 0;
  let skillCount = 0;
  let toolCallCount = 0;
  let allCompleted = true;
  let hasInProgress = false;
  let hasFailed = false;
  let reasoningText: string | undefined;
  let hasReasoningHeader = false;

  for (const item of items) {
    if (item.type === "thought" || item.type === "agent") {
      if (!hasReasoningHeader) {
        hasReasoningHeader = true;
        reasoningText = item.text;
      }
      continue;
    }
    if (item.type !== "tool_call") continue;
    const tc = item as ChatItemToolCall;
    toolCallCount++;
    // Detect skill by kind or title pattern
    const isSkill = tc.kind === "skill" || /^Skill\s+/i.test(tc.title || "");
    if (isSkill) {
      skillCount++;
    } else {
      switch (tc.kind) {
        case "read":
          readCount++;
          break;
        case "edit":
          editCount++;
          break;
        case "execute":
          executeCount++;
          break;
      }
    }
    if (tc.status !== "completed" && tc.status !== "failed") {
      allCompleted = false;
    }
    if (tc.status === "in_progress" || tc.status === "pending") {
      hasInProgress = true;
    }
    if (tc.status === "failed") {
      hasFailed = true;
    }
  }

  if (toolCallCount === 0) {
    allCompleted = false;
  }

  return {
    reasoningText,
    hasReasoningHeader,
    readCount,
    editCount,
    executeCount,
    skillCount,
    toolCallCount,
    allCompleted,
    hasInProgress,
    hasFailed,
  };
}

// ===== Semantic Grouping Algorithm =====

/**
 * Groups messages into semantic work units.
 *
 * A work_unit binds a reasoning header (thought/agent) with subsequent tool_calls:
 *   [thought|agent] → [tool_call, tool_call, ...] → work_unit
 *
 * Rules:
 * - user and plan are always rendered as single items.
 * - thought/agent followed by ≥1 tool_call → work_unit (with reasoning header).
 * - Consecutive tool_calls (≥2) without preceding reasoning → work_unit (no header).
 * - A single orphan tool_call → single item.
 * - Standalone thought/agent (not followed by tool_calls) → single item.
 * - Interleaved thoughts within tool_call sequences are included in the work_unit.
 */
export function groupMessages(messages: ChatItem[]): RenderItem[] {
  const result: RenderItem[] = [];
  const len = messages.length;
  let i = 0;

  while (i < len) {
    const msg = messages[i];

    // user and plan are always standalone
    if (msg.type === "user" || msg.type === "plan") {
      result.push({ type: "single", item: msg });
      i++;
      continue;
    }

    // thought or agent: try to bind with following tool_calls
    if (msg.type === "thought" || msg.type === "agent") {
      const collected: ChatItem[] = [msg];
      let j = i + 1;
      let toolCount = 0;

      // Collect following tool_calls and interleaved thoughts
      while (j < len) {
        const next = messages[j];
        if (next.type === "tool_call") {
          collected.push(next);
          toolCount++;
          j++;
        } else if (next.type === "thought") {
          // Only include interleaved thought if there are tool_calls around it
          // Look ahead to see if more tool_calls follow
          if (j + 1 < len && messages[j + 1].type === "tool_call") {
            collected.push(next);
            j++;
          } else if (toolCount > 0) {
            // Already have tool_calls, include trailing thought
            collected.push(next);
            j++;
          } else {
            break;
          }
        } else {
          break;
        }
      }

      if (toolCount > 0) {
        // Form a work_unit with reasoning header
        const meta = computeWorkUnitMeta(collected);
        result.push({
          type: "work_unit",
          id: buildWorkUnitId(collected),
          items: collected,
          meta,
        });
        i = j;
      } else {
        // No tool_calls follow, render as single
        result.push({ type: "single", item: msg });
        i++;
      }
      continue;
    }

    // tool_call: collect consecutive tool_calls (and interleaved thoughts)
    if (msg.type === "tool_call") {
      const collected: ChatItem[] = [msg];
      let j = i + 1;
      let toolCount = 1;

      while (j < len) {
        const next = messages[j];
        if (next.type === "tool_call") {
          collected.push(next);
          toolCount++;
          j++;
        } else if (next.type === "thought") {
          // Include interleaved thought if more tool_calls likely follow
          if (j + 1 < len && messages[j + 1].type === "tool_call") {
            collected.push(next);
            j++;
          } else if (toolCount >= 1) {
            collected.push(next);
            j++;
          } else {
            break;
          }
        } else {
          break;
        }
      }

      if (toolCount >= 2) {
        // Form a work_unit without reasoning header
        const meta = computeWorkUnitMeta(collected);
        result.push({
          type: "work_unit",
          id: buildWorkUnitId(collected),
          items: collected,
          meta,
        });
      } else {
        // Single tool_call (or single + trailing thought) - render individually
        for (const item of collected) {
          result.push({ type: "single", item });
        }
      }
      i = j;
      continue;
    }

    // Fallback: render as single
    result.push({ type: "single", item: msg });
    i++;
  }

  return result;
}
