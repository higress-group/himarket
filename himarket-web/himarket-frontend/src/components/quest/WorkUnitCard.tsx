import { useState, useEffect, useRef, useMemo } from "react";
import {
  ChevronDown,
  ChevronRight,
  CheckCircle2,
  XCircle,
  Loader2,
  Layers,
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
import { ToolCallCard, extractFileName } from "./ToolCallCard";

interface WorkUnitCardProps {
  items: ChatItem[];
  meta: WorkUnitMeta;
  selectedToolCallId: string | null;
  onSelectToolCall: (toolCallId: string) => void;
  isLastUnit?: boolean;
  isProcessing?: boolean;
}

/** Build a concise summary: show filenames for edits, counts for others */
function getSummaryText(
  meta: WorkUnitMeta,
  toolCallItems: ChatItem[]
): string {
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
  if (meta.otherCount > 0) parts.push(`${meta.otherCount}次其他操作`);
  if (parts.length === 0) return `${meta.toolCallCount} 个操作`;
  return parts.join(" · ");
}

export function WorkUnitCard({
  items,
  meta,
  selectedToolCallId,
  onSelectToolCall,
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

  // Streaming detection: is this the last unit and still processing?
  const isStreaming = !!(isLastUnit && isProcessing);

  // Actions expand/collapse state
  const [actionsExpanded, setActionsExpanded] = useState(!meta.allCompleted);
  const [userToggledActions, setUserToggledActions] = useState(false);
  const prevHasInProgressRef = useRef(meta.hasInProgress);

  // Auto-expand when an item goes in_progress
  useEffect(() => {
    if (meta.hasInProgress && !prevHasInProgressRef.current) {
      setActionsExpanded(true);
      setUserToggledActions(false);
    }
    prevHasInProgressRef.current = meta.hasInProgress;
  }, [meta.hasInProgress]);

  // Auto-collapse when all completed (only if user hasn't manually toggled)
  useEffect(() => {
    if (meta.allCompleted && !userToggledActions) {
      setActionsExpanded(false);
    }
  }, [meta.allCompleted, userToggledActions]);

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
    }
  }, [selectedToolCallId, toolCallItems, actionsExpanded]);

  const handleToggleActions = () => {
    setActionsExpanded(prev => !prev);
    setUserToggledActions(true);
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

  const borderColor = meta.hasFailed
    ? "border-red-400"
    : meta.hasInProgress
      ? "border-blue-400"
      : meta.allCompleted
        ? "border-green-400"
        : "border-gray-300";

  return (
    <div
      className={`border-l-[3px] pl-4 transition-colors duration-200 ${borderColor}`}
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
                <div key={item.id} className="border-l-2 border-gray-200/60 pl-3">
                  <AgentMessage
                    text={a.text}
                    variant="compact"
                    streaming={
                      isStreaming &&
                      item === reasoningItems[reasoningItems.length - 1]
                    }
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
            className="flex items-center gap-2 w-full py-1.5 text-sm hover:opacity-80 transition-opacity"
            onClick={handleToggleActions}
          >
            <StatusIcon
              size={14}
              className={`${statusColor} ${meta.hasInProgress ? "animate-spin" : ""}`}
            />
            <span className="text-gray-600 text-xs font-medium flex-1 text-left">
              {getSummaryText(meta, toolCallItems)}
            </span>
            {actionsExpanded ? (
              <ChevronDown size={14} className="text-gray-400" />
            ) : (
              <ChevronRight size={14} className="text-gray-400" />
            )}
          </button>

          {/* Expanded Actions List */}
          {actionsExpanded && (
            <div className="space-y-0.5">
              {toolCallItems.map(item => {
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
