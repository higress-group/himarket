import { useState, useEffect, useRef, useMemo } from "react";
import {
  ChevronDown,
  ChevronRight,
  CheckCircle2,
  XCircle,
  Loader2,
  Layers,
  Eye,
  Search,
  Brain,
  CloudDownload,
  Settings2,
  CircleHelp,
} from "lucide-react";
import type {
  ChatItem,
  ChatItemToolCall,
  ChatItemThought,
  ChatItemAgent,
} from "../../types/acp";
import type { WorkUnitMeta } from "../../lib/utils/groupMessages";
import { ThoughtBlock } from "./ThoughtBlock";
import { AgentMessage } from "./AgentMessage";
import { ToolCallCard, extractFileName, isMcpItem } from "./ToolCallCard";

interface WorkUnitCardProps {
  items: ChatItem[];
  meta: WorkUnitMeta;
  selectedToolCallId: string | null;
  onSelectToolCall: (toolCallId: string) => void;
  onOpenFile?: (path: string) => void;
  isLastUnit?: boolean;
  isProcessing?: boolean;
}

/** Build a concise summary: show filenames for edits, counts for others */
function getSummaryText(meta: WorkUnitMeta, toolCallItems: ChatItem[]): string {
  // Collect edited filenames
  const editedFiles: string[] = [];
  for (const item of toolCallItems) {
    if (item.type !== "tool_call") continue;
    const tc = item as ChatItemToolCall;
    if (tc.kind !== "edit") continue;
    const path =
      (tc.rawInput?.file_path as string) ??
      (tc.rawInput?.path as string) ??
      tc.locations?.[0]?.path ??
      null;
    if (path) {
      const name = extractFileName(path);
      if (!editedFiles.includes(name)) editedFiles.push(name);
    }
  }

  const parts: string[] = [];

  // Show filenames for edits (up to 3), otherwise fall back to count
  if (editedFiles.length > 0) {
    const shown = editedFiles.slice(0, 3).join(", ");
    const rest = editedFiles.length - 3;
    parts.push(rest > 0 ? `${shown} +${rest}` : shown);
  } else if (meta.editCount > 0) {
    parts.push(`${meta.editCount}次编辑`);
  }

  if (meta.readCount > 0) parts.push(`${meta.readCount}次阅读`);
  if (meta.deleteCount > 0) parts.push(`${meta.deleteCount}次删除`);
  if (meta.moveCount > 0) parts.push(`${meta.moveCount}次移动`);
  if (meta.searchCount > 0) parts.push(`${meta.searchCount}次搜索`);
  if (meta.executeCount > 0) parts.push(`${meta.executeCount}次执行`);
  if (meta.fetchCount > 0) parts.push(`${meta.fetchCount}次抓取`);
  if (meta.thinkCount > 0) parts.push(`${meta.thinkCount}次思考`);
  if (meta.switchModeCount > 0) parts.push(`${meta.switchModeCount}次模式切换`);
  if (meta.skillCount > 0) parts.push(`${meta.skillCount}个技能`);
  if (meta.otherCount > 0) {
    // Count how many "other" items are actually MCP tool calls
    let mcpCount = 0;
    for (const item of toolCallItems) {
      if (item.type !== "tool_call") continue;
      const tc = item as ChatItemToolCall;
      if (tc.kind === "other" && isMcpItem(tc)) mcpCount++;
    }
    const nonMcpCount = meta.otherCount - mcpCount;
    if (mcpCount > 0) parts.push(`${mcpCount}次MCP调用`);
    if (nonMcpCount > 0) parts.push(`${nonMcpCount}次其他操作`);
  }
  if (parts.length === 0) return `${meta.toolCallCount} 个操作`;
  return parts.join(" · ");
}

// ===== Same-kind operation merging =====

const MERGEABLE_KINDS = new Set([
  "read",
  "search",
  "think",
  "fetch",
  "switch_mode",
  "other",
]);

type ToolCallGroup =
  | { type: "single"; item: ChatItem }
  | { type: "merged"; kind: string; items: ChatItemToolCall[] };

function groupConsecutiveToolCalls(items: ChatItem[]): ToolCallGroup[] {
  const groups: ToolCallGroup[] = [];
  let i = 0;

  while (i < items.length) {
    const item = items[i];
    if (item.type === "tool_call") {
      const tc = item as ChatItemToolCall;
      if (MERGEABLE_KINDS.has(tc.kind)) {
        const sameKindItems: ChatItemToolCall[] = [tc];
        let j = i + 1;
        while (j < items.length) {
          const next = items[j];
          if (
            next.type === "tool_call" &&
            (next as ChatItemToolCall).kind === tc.kind
          ) {
            sameKindItems.push(next as ChatItemToolCall);
            j++;
          } else {
            break;
          }
        }
        if (sameKindItems.length >= 2) {
          groups.push({ type: "merged", kind: tc.kind, items: sameKindItems });
          i = j;
          continue;
        }
      }
    }
    groups.push({ type: "single", item });
    i++;
  }

  return groups;
}

const MERGED_ICON_MAP: Record<string, typeof Eye> = {
  read: Eye,
  search: Search,
  think: Brain,
  fetch: CloudDownload,
  switch_mode: Settings2,
  other: CircleHelp,
};

function getMergedLabel(kind: string, items: ChatItemToolCall[]): string {
  const count = items.length;
  if (kind === "read") {
    const names = items
      .map(tc => {
        const p =
          (tc.rawInput?.file_path as string) ??
          (tc.rawInput?.path as string) ??
          tc.locations?.[0]?.path ??
          null;
        return p ? extractFileName(p) : null;
      })
      .filter(Boolean);
    const shown = names.slice(0, 3).join(", ");
    const rest = names.length - 3;
    return `已查看 ${count} 个文件${shown ? ": " + shown : ""}${rest > 0 ? ` +${rest}` : ""}`;
  }
  if (kind === "search") return `搜索了 ${count} 次`;
  if (kind === "think") return `思考了 ${count} 次`;
  if (kind === "fetch") return `抓取了 ${count} 次`;
  if (kind === "switch_mode") return `切换模式 ${count} 次`;
  return `其他操作 ${count} 次`;
}

function MergedRow({
  kind,
  items,
  selectedToolCallId,
  onSelectToolCall,
}: {
  kind: string;
  items: ChatItemToolCall[];
  selectedToolCallId: string | null;
  onSelectToolCall: (toolCallId: string) => void;
}) {
  const [expanded, setExpanded] = useState(false);
  const Icon = MERGED_ICON_MAP[kind] ?? CircleHelp;
  const label = getMergedLabel(kind, items);
  const allDone = items.every(tc => tc.status === "completed");
  const anyFailed = items.some(tc => tc.status === "failed");

  return (
    <div>
      <div
        className="flex items-center gap-2 px-2.5 py-1.5 cursor-pointer hover:bg-gray-50/60 rounded-lg transition-colors"
        onClick={() => setExpanded(prev => !prev)}
      >
        {expanded ? (
          <ChevronDown size={12} className="text-gray-300 flex-shrink-0" />
        ) : (
          <ChevronRight size={12} className="text-gray-300 flex-shrink-0" />
        )}
        <Icon size={13} className="text-gray-400 flex-shrink-0" />
        <span className="text-[13px] text-gray-400 truncate flex-1 min-w-0">
          {label}
        </span>
        {anyFailed ? (
          <XCircle size={13} className="text-red-500 flex-shrink-0" />
        ) : allDone ? (
          <CheckCircle2 size={13} className="text-green-500/70 flex-shrink-0" />
        ) : (
          <Loader2
            size={13}
            className="text-blue-500 animate-spin flex-shrink-0"
          />
        )}
      </div>
      {expanded && (
        <div className="pl-4 space-y-0.5">
          {items.map(tc => (
            <ToolCallCard
              key={tc.id}
              item={tc}
              selected={selectedToolCallId === tc.toolCallId}
              onClick={() => onSelectToolCall(tc.toolCallId)}
              variant="compact"
            />
          ))}
        </div>
      )}
    </div>
  );
}

export function WorkUnitCard({
  items,
  meta,
  selectedToolCallId,
  onSelectToolCall,
  onOpenFile,
  isLastUnit,
  isProcessing,
}: WorkUnitCardProps) {
  // Separate items by role
  const { reasoningItems, toolCallItems, trailingThoughts } = useMemo(() => {
    const reasoning: ChatItem[] = [];
    const toolCalls: ChatItem[] = [];
    const trailing: ChatItem[] = [];

    let foundToolCall = false;
    let lastToolCallIdx = -1;

    // Find last tool_call index
    for (let i = items.length - 1; i >= 0; i--) {
      if (items[i].type === "tool_call") {
        lastToolCallIdx = i;
        break;
      }
    }

    for (let i = 0; i < items.length; i++) {
      const item = items[i];
      if (
        !foundToolCall &&
        (item.type === "thought" || item.type === "agent")
      ) {
        reasoning.push(item);
      } else if (item.type === "tool_call") {
        foundToolCall = true;
        toolCalls.push(item);
      } else if (item.type === "thought" && foundToolCall) {
        if (i > lastToolCallIdx) {
          trailing.push(item);
        } else {
          // interleaved thought between tool calls - include with tool calls
          toolCalls.push(item);
        }
      } else {
        toolCalls.push(item);
      }
    }

    return {
      reasoningItems: reasoning,
      toolCallItems: toolCalls,
      trailingThoughts: trailing,
    };
  }, [items]);

  // Group consecutive same-kind auxiliary tool calls for merged rendering
  const groupedToolCalls = useMemo(
    () => groupConsecutiveToolCalls(toolCallItems),
    [toolCallItems]
  );

  // Streaming detection: is this the last unit and still processing?
  const isStreaming = !!(isLastUnit && isProcessing);

  // Actions expand/collapse state
  const [actionsExpanded, setActionsExpanded] = useState(true);
  const userToggledActionsRef = useRef(false);
  const prevHasInProgressRef = useRef(meta.hasInProgress);

  // Auto-expand when an item goes in_progress (only if user hasn't manually toggled)
  useEffect(() => {
    if (meta.hasInProgress && !prevHasInProgressRef.current && !userToggledActionsRef.current) {
      setActionsExpanded(true);
    }
    prevHasInProgressRef.current = meta.hasInProgress;
  }, [meta.hasInProgress]);

  // Auto-expand when selected tool is inside this unit
  useEffect(() => {
    if (!selectedToolCallId) return;
    const containsSelected = toolCallItems.some(
      i =>
        i.type === "tool_call" &&
        (i as ChatItemToolCall).toolCallId === selectedToolCallId
    );
    if (containsSelected && !actionsExpanded) {
      setActionsExpanded(true);
      userToggledActionsRef.current = false;
    }
  }, [selectedToolCallId, toolCallItems, actionsExpanded]);

  const handleToggleActions = () => {
    setActionsExpanded(prev => !prev);
    userToggledActionsRef.current = true;
  };

  // Header status icon
  const StatusIcon = meta.hasFailed
    ? XCircle
    : meta.allCompleted
      ? CheckCircle2
      : meta.hasInProgress
        ? Loader2
        : Layers;
  const statusColor = meta.hasFailed
    ? "text-red-500"
    : meta.allCompleted
      ? "text-green-500"
      : "text-blue-500";

  return (
    <div
      className="pl-1 transition-colors duration-200"
    >
      {/* Reasoning Header */}
      {reasoningItems.length > 0 && (
        <div className="pb-1.5">
          {reasoningItems.map(item => {
            if (item.type === "thought") {
              const t = item as ChatItemThought;
              return (
                <ThoughtBlock
                  key={item.id}
                  text={t.text}
                  variant="inline"
                  streaming={
                    isStreaming &&
                    item === reasoningItems[reasoningItems.length - 1]
                  }
                />
              );
            }
            if (item.type === "agent") {
              const a = item as ChatItemAgent;
              return (
                <div
                  key={item.id}
                  className="pl-1"
                >
                  <AgentMessage
                    text={a.text}
                    variant="compact"
                    streaming={
                      isStreaming &&
                      item === reasoningItems[reasoningItems.length - 1]
                    }
                    onOpenFile={onOpenFile}
                  />
                </div>
              );
            }
            return null;
          })}
        </div>
      )}

      {/* Actions Section */}
      {meta.toolCallCount > 0 && (
        <>
          {/* Actions Summary Bar */}
          <button
            className="flex items-center gap-2 w-full py-1.5 px-2 rounded-lg text-[13px] hover:bg-gray-50 transition-colors"
            onClick={handleToggleActions}
          >
            <StatusIcon
              size={14}
              className={`${statusColor} flex-shrink-0 ${meta.hasInProgress ? "animate-spin" : ""}`}
            />
            <span className="text-gray-500 text-[13px] flex-1 text-left truncate">
              {getSummaryText(meta, toolCallItems)}
            </span>
            {actionsExpanded ? (
              <ChevronDown size={14} className="text-gray-300 flex-shrink-0" />
            ) : (
              <ChevronRight size={14} className="text-gray-300 flex-shrink-0" />
            )}
          </button>

          {/* Expanded Actions List */}
          {actionsExpanded && (
            <div className="space-y-0.5">
              {groupedToolCalls.map((group, gi) => {
                if (group.type === "merged") {
                  return (
                    <MergedRow
                      key={`merged-${group.kind}-${gi}`}
                      kind={group.kind}
                      items={group.items}
                      selectedToolCallId={selectedToolCallId}
                      onSelectToolCall={onSelectToolCall}
                    />
                  );
                }
                const item = group.item;
                if (item.type === "tool_call") {
                  const tc = item as ChatItemToolCall;
                  return (
                    <ToolCallCard
                      key={item.id}
                      item={tc}
                      selected={selectedToolCallId === tc.toolCallId}
                      onClick={() => onSelectToolCall(tc.toolCallId)}
                      variant="compact"
                    />
                  );
                }
                // Interleaved thought within actions
                if (item.type === "thought") {
                  const t = item as ChatItemThought;
                  return (
                    <div key={item.id} className="px-0 py-1">
                      <ThoughtBlock text={t.text} variant="inline" />
                    </div>
                  );
                }
                return null;
              })}
            </div>
          )}
        </>
      )}

      {/* Trailing thoughts */}
      {trailingThoughts.length > 0 && (
        <div className="mt-1.5">
          {trailingThoughts.map(item => {
            const t = item as ChatItemThought;
            return (
              <ThoughtBlock
                key={item.id}
                text={t.text}
                variant="inline"
                streaming={
                  isStreaming &&
                  item === trailingThoughts[trailingThoughts.length - 1]
                }
              />
            );
          })}
        </div>
      )}
    </div>
  );
}
