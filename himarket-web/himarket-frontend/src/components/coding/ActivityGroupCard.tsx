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
} from 'lucide-react';
import { useState, useEffect, useRef, useMemo } from 'react';

import { AgentMessage } from './AgentMessage';
import { ThoughtBlock } from './ThoughtBlock';
import { ToolCallCard } from './ToolCallCard';
import { extractFileName } from './ToolCallCard.utils';

import type { ActivityGroup } from '../../lib/utils/groupMessages';
import type {
  ChatItem,
  ChatItemToolCall,
  ChatItemThought,
  ChatItemAgent,
} from '../../types/coding-protocol';

// ===== Props =====

interface ActivityGroupCardProps {
  group: ActivityGroup;
  selectedToolCallId: string | null;
  onSelectToolCall: (toolCallId: string) => void;
  onOpenFile?: (path: string) => void;
}

// ===== Summary Text =====

function getSummaryText(group: ActivityGroup): string {
  const { blocks, toolsSummary: s } = group;
  const parts: string[] = [];

  // Show filenames for edits (up to 3)
  if (s.edits > 0) {
    const editedFiles: string[] = [];
    for (const b of blocks) {
      if (b.type !== 'tool_call') continue;
      const tc = b as ChatItemToolCall;
      if (tc.kind !== 'edit') continue;
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
      const shown = editedFiles.slice(0, 3).join(', ');
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
    const toolCount = blocks.filter((b) => b.type === 'tool_call').length;
    return `${toolCount} 个操作`;
  }
  return parts.join(' · ');
}

// ===== Same-kind operation merging (migrated from WorkUnitCard) =====

const MERGEABLE_KINDS = new Set([
  'read',
  'search',
  'think',
  'fetch',
  'switch_mode',
  'other',
  'edit',
]);

type ToolCallGroup =
  | { type: 'single'; item: ChatItem }
  | { type: 'merged'; kind: string; items: ChatItemToolCall[] };

function groupConsecutiveToolCalls(items: ChatItem[]): ToolCallGroup[] {
  const groups: ToolCallGroup[] = [];
  let i = 0;

  while (i < items.length) {
    const item = items[i];
    if (item && item.type === 'tool_call') {
      const tc = item as ChatItemToolCall;
      if (MERGEABLE_KINDS.has(tc.kind)) {
        const sameKindItems: ChatItemToolCall[] = [tc];
        let j = i + 1;
        while (j < items.length) {
          const next = items[j];
          if (next && next.type === 'tool_call' && (next as ChatItemToolCall).kind === tc.kind) {
            sameKindItems.push(next as ChatItemToolCall);
            j++;
          } else {
            break;
          }
        }
        if (sameKindItems.length >= 2) {
          groups.push({ items: sameKindItems, kind: tc.kind, type: 'merged' });
          i = j;
          continue;
        }
      }
    }
    if (item) {
      groups.push({ item, type: 'single' });
    }
    i++;
  }

  return groups;
}

// ===== Merged Row Icons & Labels =====

const MERGED_ICON_MAP: Record<string, typeof Eye> = {
  edit: Pencil,
  fetch: CloudDownload,
  other: CircleHelp,
  read: Eye,
  search: Search,
  switch_mode: Settings2,
  think: Brain,
};

function getMergedLabel(kind: string, items: ChatItemToolCall[]): string {
  const count = items.length;
  if (kind === 'read') {
    const names = items
      .map((tc) => {
        const p =
          (tc.rawInput?.file_path as string) ??
          (tc.rawInput?.path as string) ??
          tc.locations?.[0]?.path ??
          null;
        return p ? extractFileName(p) : null;
      })
      .filter(Boolean);
    const shown = names.slice(0, 3).join(', ');
    const rest = names.length - 3;
    return `已查看 ${count} 个文件${shown ? ': ' + shown : ''}${rest > 0 ? ` +${rest}` : ''}`;
  }
  if (kind === 'search') return `搜索了 ${count} 次`;
  if (kind === 'think') return `思考了 ${count} 次`;
  if (kind === 'fetch') return `抓取了 ${count} 次`;
  if (kind === 'switch_mode') return `切换模式 ${count} 次`;
  if (kind === 'edit') return `编辑了 ${count} 次`;
  return `其他操作 ${count} 次`;
}

// ===== MergedRow Component =====

function MergedRow({
  items,
  kind,
  onSelectToolCall,
  selectedToolCallId,
}: {
  kind: string;
  items: ChatItemToolCall[];
  selectedToolCallId: string | null;
  onSelectToolCall: (toolCallId: string) => void;
}) {
  const [expanded, setExpanded] = useState(false);
  const Icon = MERGED_ICON_MAP[kind] ?? CircleHelp;
  const label = getMergedLabel(kind, items);
  const allDone = items.every((tc) => tc.status === 'completed');
  const anyFailed = items.some((tc) => tc.status === 'failed');

  return (
    <div>
      <button
        className="flex items-center gap-2 px-2.5 py-1.5 cursor-pointer hover:bg-gray-50/60 rounded-lg transition-colors border-0 bg-transparent w-full text-left"
        onClick={() => setExpanded((prev) => !prev)}
        type="button"
      >
        {expanded ? (
          <ChevronDown className="text-gray-300 flex-shrink-0" size={12} />
        ) : (
          <ChevronRight className="text-gray-300 flex-shrink-0" size={12} />
        )}
        <Icon className="text-gray-400 flex-shrink-0" size={13} />
        <span className="text-[13px] text-gray-400 truncate flex-1 min-w-0">{label}</span>
        {anyFailed ? (
          <XCircle className="text-red-500 flex-shrink-0" size={13} />
        ) : allDone ? (
          <CheckCircle2 className="text-green-500/70 flex-shrink-0" size={13} />
        ) : (
          <Loader2 className="text-blue-500 animate-spin flex-shrink-0" size={13} />
        )}
      </button>
      {expanded && (
        <div className="pl-4 space-y-0.5">
          {items.map((tc) => (
            <ToolCallCard
              item={tc}
              key={tc.id}
              onClick={() => onSelectToolCall(tc.toolCallId)}
              selected={selectedToolCallId === tc.toolCallId}
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
  if (group.isExploring) return '执行中...';
  if (group.isThinkingOnly) return '深度思考';
  if (group.isEditOnly && group.editFilePath) {
    return `编辑 ${extractFileName(group.editFilePath)}`;
  }
  return '已执行';
}

// ===== Main Component =====

export function ActivityGroupCard({
  group,
  onOpenFile,
  onSelectToolCall,
  selectedToolCallId,
}: ActivityGroupCardProps) {
  // Separate blocks by role
  const { reasoningItems, toolCallItems, trailingThoughts } = useMemo(() => {
    const reasoning: ChatItem[] = [];
    const toolCalls: ChatItem[] = [];
    const trailing: ChatItem[] = [];

    let foundToolCall = false;
    let lastToolCallIdx = -1;

    for (let i = group.blocks.length - 1; i >= 0; i--) {
      const block = group.blocks[i];
      if (block && block.type === 'tool_call') {
        lastToolCallIdx = i;
        break;
      }
    }

    for (let i = 0; i < group.blocks.length; i++) {
      const item = group.blocks[i];
      if (!item) continue;
      if (!foundToolCall && (item.type === 'thought' || item.type === 'agent')) {
        reasoning.push(item);
      } else if (item.type === 'tool_call') {
        foundToolCall = true;
        toolCalls.push(item);
      } else if (item.type === 'thought' && foundToolCall) {
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
  const groupedToolCalls = useMemo(() => groupConsecutiveToolCalls(toolCallItems), [toolCallItems]);

  const toolCallCount = toolCallItems.filter((b) => b.type === 'tool_call').length;

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
      (i) => i.type === 'tool_call' && (i as ChatItemToolCall).toolCallId === selectedToolCallId,
    );
    if (containsSelected && !isExpanded) {
      setIsExpanded(true);
      hasManuallyToggledRef.current = false;
    }
  }, [selectedToolCallId, toolCallItems, isExpanded]);

  const handleToggle = () => {
    setIsExpanded((prev) => !prev);
    hasManuallyToggledRef.current = true;
  };

  // Status icon
  const hasFailed = group.hasErrorTool;
  const allCompleted =
    toolCallCount > 0 &&
    toolCallItems.every(
      (b) =>
        b.type !== 'tool_call' ||
        (b as ChatItemToolCall).status === 'completed' ||
        (b as ChatItemToolCall).status === 'failed',
    );

  const StatusIcon = hasFailed
    ? XCircle
    : allCompleted
      ? CheckCircle2
      : group.isExploring
        ? Loader2
        : CheckCircle2;
  const statusColor = hasFailed
    ? 'text-red-500'
    : allCompleted
      ? 'text-green-500'
      : 'text-blue-500';

  const title = getTitle(group);

  return (
    <div className="pl-1 transition-colors duration-200">
      {/* Reasoning Header */}
      {reasoningItems.length > 0 && (
        <div className="pb-1.5">
          {reasoningItems.map((item) => {
            if (item.type === 'thought') {
              const t = item as ChatItemThought;
              return (
                <ThoughtBlock
                  key={item.id}
                  streaming={
                    group.isExploring && item === reasoningItems[reasoningItems.length - 1]
                  }
                  text={t.text}
                  variant="inline"
                />
              );
            }
            if (item.type === 'agent') {
              const a = item as ChatItemAgent;
              return (
                <div className="pl-1" key={item.id}>
                  <AgentMessage
                    onOpenFile={onOpenFile}
                    streaming={
                      group.isExploring && item === reasoningItems[reasoningItems.length - 1]
                    }
                    text={a.text}
                    variant="compact"
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
              className={`${statusColor} flex-shrink-0 ${group.isExploring ? 'animate-spin' : ''}`}
              size={14}
            />
            <span className="text-gray-500 text-[13px] flex-1 text-left truncate">
              {group.isExploring ? title : `${title} · ${getSummaryText(group)}`}
            </span>
            {isExpanded ? (
              <ChevronDown className="text-gray-300 flex-shrink-0" size={14} />
            ) : (
              <ChevronRight className="text-gray-300 flex-shrink-0" size={14} />
            )}
          </button>

          {/* CSS Grid animated expand/collapse */}
          <div
            className="grid transition-[grid-template-rows] duration-200 ease-out"
            style={{ gridTemplateRows: isExpanded ? '1fr' : '0fr' }}
          >
            <div className="min-h-0 overflow-hidden">
              <div className="space-y-0.5">
                {groupedToolCalls.map((g, gi) => {
                  if (g.type === 'merged') {
                    return (
                      <MergedRow
                        items={g.items}
                        key={`merged-${g.kind}-${gi}`}
                        kind={g.kind}
                        onSelectToolCall={onSelectToolCall}
                        selectedToolCallId={selectedToolCallId}
                      />
                    );
                  }
                  const item = g.item;
                  if (item.type === 'tool_call') {
                    const tc = item as ChatItemToolCall;
                    return (
                      <ToolCallCard
                        item={tc}
                        key={item.id}
                        onClick={() => onSelectToolCall(tc.toolCallId)}
                        selected={selectedToolCallId === tc.toolCallId}
                        variant="compact"
                      />
                    );
                  }
                  if (item.type === 'thought') {
                    const t = item as ChatItemThought;
                    return (
                      <div className="px-0 py-1" key={item.id}>
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
          {trailingThoughts.map((item) => {
            const t = item as ChatItemThought;
            return (
              <ThoughtBlock
                key={item.id}
                streaming={
                  group.isExploring && item === trailingThoughts[trailingThoughts.length - 1]
                }
                text={t.text}
                variant="inline"
              />
            );
          })}
        </div>
      )}
    </div>
  );
}
