import { useState, useEffect, useRef, useMemo } from "react";
import {
  ChevronDown,
  ChevronRight,
  CheckCircle2,
  XCircle,
  Loader2,
  Eye,
  Search,
  Brain,
  CloudDownload,
  Settings2,
  CircleHelp,
  Pencil,
} from "lucide-react";
import type {
  ChatItem,
  ChatItemToolCall,
  ChatItemThought,
  ChatItemAgent,
} from "../../types/coding-protocol";
import type { ActivityGroup } from "../../lib/utils/groupMessages";
import { ThoughtBlock } from "./ThoughtBlock";
import { AgentMessage } from "./AgentMessage";
import { ToolCallCard, extractFileName } from "./ToolCallCard";

// ===== Props =====

interface ActivityGroupCardProps {
  group: ActivityGroup;
  selectedToolCallId: string | null;
  onSelectToolCall: (toolCallId: string) => void;
  onOpenFile?: (path: string) => void;
}

// ===== Summary Text =====

function getSummaryText(group: ActivityGroup): string {
  const { toolsSummary: s, blocks } = group;
  const parts: string[] = [];

  // Show filenames for edits (up to 3)
  if (s.edits > 0) {
    const editedFiles: string[] = [];
    for (const b of blocks) {
      if (b.type !== "tool_call") continue;
      const tc = b as ChatItemToolCall;
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
    if (editedFiles.length > 0) {
      const shown = editedFiles.slice(0, 3).join(", ");
      const rest = editedFiles.length - 3;
      parts.push(rest > 0 ? `${shown} +${rest}` : shown);
    } else {
      parts.push(`${s.edits}次编辑`);
    }
  }

  if (s.files > 0) parts.push(`${s.files}次阅读`);
  if (s.searches > 0) parts.push(`${s.searches}次搜索`);
  if (s.executes > 0) parts.push(`${s.executes}次执行`);
  if (s.fetches > 0) parts.push(`${s.fetches}次抓取`);
  if (s.thinks > 0) parts.push(`${s.thinks}次思考`);
  if (s.skills > 0) parts.push(`${s.skills}个技能`);
  if (s.mcpCalls > 0) parts.push(`${s.mcpCalls}次MCP调用`);
  if (s.others > 0) parts.push(`${s.others}次其他操作`);

  if (parts.length === 0) {
    const toolCount = blocks.filter(b => b.type === "tool_call").length;
    return `${toolCount} 个操作`;
  }
  return parts.join(" · ");
}

// ===== Same-kind operation merging (migrated from WorkUnitCard) =====

const MERGEABLE_KINDS = new Set([
  "read",
  "search",
  "think",
  "fetch",
  "switch_mode",
  "other",
  "edit",
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

// ===== Merged Row Icons & Labels =====

const MERGED_ICON_MAP: Record<string, typeof Eye> = {
  read: Eye,
  search: Search,
  think: Brain,
  fetch: CloudDownload,
  switch_mode: Settings2,
  edit: Pencil,
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
  if (kind === "edit") return `编辑了 ${count} 次`;
  return `其他操作 ${count} 次`;
}

// ===== MergedRow Component =====

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

// ===== Title helpers =====

function getTitle(group: ActivityGroup): string {
  if (group.isExploring) return "执行中...";
  if (group.isThinkingOnly) return "深度思考";
  if (group.isEditOnly && group.editFilePath) {
    return `编辑 ${extractFileName(group.editFilePath)}`;
  }
  return "已执行";
}

// ===== Main Component =====

export function ActivityGroupCard({
  group,
  selectedToolCallId,
  onSelectToolCall,
  onOpenFile,
}: ActivityGroupCardProps) {
  // Separate blocks by role
  const { reasoningItems, toolCallItems, trailingThoughts } = useMemo(() => {
    const reasoning: ChatItem[] = [];
    const toolCalls: ChatItem[] = [];
    const trailing: ChatItem[] = [];

    let foundToolCall = false;
    let lastToolCallIdx = -1;

    for (let i = group.blocks.length - 1; i >= 0; i--) {
      if (group.blocks[i].type === "tool_call") {
        lastToolCallIdx = i;
        break;
      }
    }

    for (let i = 0; i < group.blocks.length; i++) {
      const item = group.blocks[i];
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
  }, [group.blocks]);

  // Group consecutive same-kind tool calls for merged rendering
  const groupedToolCalls = useMemo(
    () => groupConsecutiveToolCalls(toolCallItems),
    [toolCallItems]
  );

  const toolCallCount = toolCallItems.filter(
    b => b.type === "tool_call"
  ).length;

  // ===== Auto expand/collapse based on isExploring =====
  const [isExpanded, setIsExpanded] = useState(group.isExploring);
  const hasManuallyToggledRef = useRef(false);
  const prevIsExploringRef = useRef(group.isExploring);

  useEffect(() => {
    if (hasManuallyToggledRef.current) return;

    const prev = prevIsExploringRef.current;
    const curr = group.isExploring;

    if (!prev && curr) {
      // Started exploring → auto expand
      setIsExpanded(true);
    } else if (prev && !curr) {
      // Finished exploring → auto collapse
      setIsExpanded(false);
    }

    prevIsExploringRef.current = curr;
  }, [group.isExploring]);

  // Auto-expand when selected tool is inside this group
  useEffect(() => {
    if (!selectedToolCallId) return;
    const containsSelected = toolCallItems.some(
      i =>
        i.type === "tool_call" &&
        (i as ChatItemToolCall).toolCallId === selectedToolCallId
    );
    if (containsSelected && !isExpanded) {
      setIsExpanded(true);
      hasManuallyToggledRef.current = false;
    }
  }, [selectedToolCallId, toolCallItems, isExpanded]);

  const handleToggle = () => {
    setIsExpanded(prev => !prev);
    hasManuallyToggledRef.current = true;
  };

  // Status icon
  const hasFailed = group.hasErrorTool;
  const allCompleted =
    toolCallCount > 0 &&
    toolCallItems.every(
      b =>
        b.type !== "tool_call" ||
        (b as ChatItemToolCall).status === "completed" ||
        (b as ChatItemToolCall).status === "failed"
    );

  const StatusIcon = hasFailed
    ? XCircle
    : allCompleted
      ? CheckCircle2
      : group.isExploring
        ? Loader2
        : CheckCircle2;
  const statusColor = hasFailed
    ? "text-red-500"
    : allCompleted
      ? "text-green-500"
      : "text-blue-500";

  const title = getTitle(group);

  return (
    <div className="pl-1 transition-colors duration-200">
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
                    group.isExploring &&
                    item === reasoningItems[reasoningItems.length - 1]
                  }
                />
              );
            }
            if (item.type === "agent") {
              const a = item as ChatItemAgent;
              return (
                <div key={item.id} className="pl-1">
                  <AgentMessage
                    text={a.text}
                    variant="compact"
                    streaming={
                      group.isExploring &&
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
      {toolCallCount > 0 && (
        <>
          {/* Summary Bar */}
          <button
            className="flex items-center gap-2 w-full py-1.5 px-2 rounded-lg text-[13px] hover:bg-gray-50 transition-colors"
            onClick={handleToggle}
          >
            <StatusIcon
              size={14}
              className={`${statusColor} flex-shrink-0 ${group.isExploring ? "animate-spin" : ""}`}
            />
            <span className="text-gray-500 text-[13px] flex-1 text-left truncate">
              {group.isExploring
                ? title
                : `${title} · ${getSummaryText(group)}`}
            </span>
            {isExpanded ? (
              <ChevronDown size={14} className="text-gray-300 flex-shrink-0" />
            ) : (
              <ChevronRight size={14} className="text-gray-300 flex-shrink-0" />
            )}
          </button>

          {/* CSS Grid animated expand/collapse */}
          <div
            className="grid transition-[grid-template-rows] duration-200 ease-out"
            style={{ gridTemplateRows: isExpanded ? "1fr" : "0fr" }}
          >
            <div className="min-h-0 overflow-hidden">
              <div className="space-y-0.5">
                {groupedToolCalls.map((g, gi) => {
                  if (g.type === "merged") {
                    return (
                      <MergedRow
                        key={`merged-${g.kind}-${gi}`}
                        kind={g.kind}
                        items={g.items}
                        selectedToolCallId={selectedToolCallId}
                        onSelectToolCall={onSelectToolCall}
                      />
                    );
                  }
                  const item = g.item;
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
            </div>
          </div>
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
                  group.isExploring &&
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
