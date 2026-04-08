import {
  Eye,
  Terminal,
  Trash2,
  ArrowRightLeft,
  Search,
  Brain,
  CloudDownload,
  Settings2,
  CircleHelp,
  CheckCircle2,
  XCircle,
  Loader2,
  Sparkles,
  ChevronDown,
  ChevronRight,
} from 'lucide-react';
import { useState } from 'react';

import { Mcp } from '../icon';
import { DiffViewer } from './DiffViewer';
import { extractFileName, getDiffStats, isMcpItem } from './ToolCallCard.utils';

import type { ChatItemToolCall, ToolCallContentItem } from '../../types/coding-protocol';

interface ToolCallCardProps {
  item: ChatItemToolCall;
  selected: boolean;
  onClick: () => void;
  variant?: 'default' | 'compact';
}

// ===== Operation type detection =====

type FileOp = 'A' | 'M' | 'D';

const opStyles: Record<FileOp, string> = {
  A: 'bg-green-50 text-green-600 border border-green-200',
  D: 'bg-red-50 text-red-500 border border-red-200',
  M: 'bg-amber-50 text-amber-600 border border-amber-200',
};

function getFileOp(item: ChatItemToolCall): FileOp {
  if (item.content) {
    const diffs = item.content.filter((c) => c.type === 'diff');
    if (diffs.length > 0) {
      const hasOld = diffs.some((d) => d.oldText !== undefined && d.oldText !== null);
      const hasNew = diffs.some((d) => d.newText !== undefined && d.newText !== null);
      if (!hasOld && hasNew) return 'A';
      if (hasOld && !hasNew) return 'D';
      if (hasOld && hasNew) return 'M';
    }
  }
  // Write tool (rawInput.content exists) → create
  if (item.rawInput && typeof item.rawInput.content === 'string') return 'A';
  return 'M';
}

// ===== Path helpers =====

function getFilePath(item: ChatItemToolCall): string | null {
  if (item.rawInput) {
    if (typeof item.rawInput.file_path === 'string') return item.rawInput.file_path;
    if (typeof item.rawInput.path === 'string') return item.rawInput.path;
  }
  if (item.locations && item.locations.length > 0) {
    const first = item.locations[0];
    if (first) return first.path;
  }
  return null;
}

/** Get the parent directory (last 2 segments) from a full path, for context */
function extractDirHint(path: string, fileName: string): string | null {
  const withoutFile = path.slice(0, path.length - fileName.length - 1);
  if (!withoutFile) return null;
  const parts = withoutFile.split(/[/\\]/);
  // Show last 2 segments for context
  return parts.slice(-2).join('/');
}

// ===== Command helpers =====

function getCommand(item: ChatItemToolCall): string | null {
  if (item.rawInput && typeof item.rawInput.command === 'string') {
    return item.rawInput.command;
  }
  return null;
}

/** Extract skill name from title like "Skill qoder-ppt" → "qoder-ppt" */
function getSkillName(item: ChatItemToolCall): string {
  const title = item.title || '';
  const match = title.match(/^Skill\s+(.+)$/i);
  return match && match[1] ? match[1] : title;
}

/** Detect skill tool_call by kind or title pattern */
function isSkillItem(item: ChatItemToolCall): boolean {
  if (item.kind === 'skill') return true;
  return /^Skill\s+/i.test(item.title || '');
}

// ===== MCP tool_call detection & parsing =====

interface McpTitleInfo {
  toolName: string;
  serverName: string;
  paramsSummary: string;
}

/**
 * Parse MCP tool_call title like:
 *   "ip-address-query (ip-query MCP Server): {\"ip\":\"\"}"
 * into structured parts.
 */
function parseMcpTitle(title: string): McpTitleInfo | null {
  // Pattern: toolName (serverName MCP Server): jsonParams
  const match = title.match(/^(.+?)\s+\((.+?)\s+MCP Server\)\s*:\s*(.*)$/);
  if (!match) return null;
  const toolName = match[1] ?? '';
  const serverName = match[2] ?? '';
  const rawParams = match[3] ?? '';
  // Build a human-readable params summary from the JSON
  let paramsSummary = '';
  try {
    const parsed = JSON.parse(rawParams);
    if (typeof parsed === 'object' && parsed !== null) {
      const entries = Object.entries(parsed)
        .filter(([, v]) => v !== '' && v !== null && v !== undefined)
        .map(([k, v]) => `${k}: ${typeof v === 'string' ? v : JSON.stringify(v)}`);
      paramsSummary = entries.length > 0 ? entries.join(', ') : '';
    }
  } catch {
    // If JSON parse fails, use raw string but trim it
    paramsSummary = rawParams.length > 60 ? rawParams.slice(0, 60) + '...' : rawParams;
  }
  return { paramsSummary, serverName, toolName };
}

/** Get parsed MCP info, returns null if not an MCP tool_call */
function getMcpInfo(item: ChatItemToolCall): McpTitleInfo | null {
  if (!isMcpItem(item)) return null;
  return parseMcpTitle(item.title || '');
}

function getOutputPreview(content?: ToolCallContentItem[]): string | null {
  if (!content) return null;
  const textItem = content.find((c) => c.type === 'content' && c.content?.type === 'text');
  if (!textItem || textItem.type !== 'content' || textItem.content?.type !== 'text') {
    return null;
  }
  const text = textItem.content.text;
  const firstLine = text.split('\n').filter((l: string) => l.trim())[0] ?? '';
  if (firstLine.length > 80) return firstLine.slice(0, 80) + '...';
  return firstLine || null;
}

function getTerminalId(content?: ToolCallContentItem[]): string | null {
  if (!content) return null;
  const terminal = content.find((c) => c.type === 'terminal');
  return terminal && terminal.type === 'terminal' ? terminal.terminalId : null;
}

// ===== Sub-components =====

function StatusBadge({ item }: { item: ChatItemToolCall }) {
  const isCompleted = item.status === 'completed';
  const isFailed = item.status === 'failed';
  const inProgress = item.status === 'in_progress' || item.status === 'pending';

  if (inProgress) return <Loader2 className="text-blue-500 animate-spin flex-shrink-0" size={13} />;
  if (isFailed) return <XCircle className="text-red-500 flex-shrink-0" size={13} />;
  if (isCompleted) return <CheckCircle2 className="text-green-500/70 flex-shrink-0" size={13} />;
  return null;
}

function OpBadge({ op }: { op: FileOp }) {
  return (
    <span
      className={`
        text-[10px] font-semibold leading-none
        w-[18px] h-[18px] rounded
        flex items-center justify-center flex-shrink-0
        ${opStyles[op]}
      `}
    >
      {op}
    </span>
  );
}

function DiffStatsDisplay({ content }: { content?: ToolCallContentItem[] }) {
  const stats = getDiffStats(content);
  if (!stats) return null;
  return (
    <span className="font-mono text-[11px] flex items-center gap-1 flex-shrink-0 tabular-nums">
      {stats.added > 0 && <span className="text-green-600">+{stats.added}</span>}
      {stats.removed > 0 && <span className="text-red-500">-{stats.removed}</span>}
    </span>
  );
}

// ===== Main component =====

export function ToolCallCard({ item, onClick, selected, variant = 'default' }: ToolCallCardProps) {
  const isSkill = isSkillItem(item);
  const filePath = item.kind !== 'execute' && !isSkill ? getFilePath(item) : null;
  const fileName = filePath ? extractFileName(filePath) : null;
  const fileOp = item.kind === 'edit' ? getFileOp(item) : null;
  const command = item.kind === 'execute' && !isSkill ? getCommand(item) : null;
  const terminalId = item.kind === 'execute' ? getTerminalId(item.content) : null;
  const extraFileCount =
    item.locations && item.locations.length > 1 ? item.locations.length - 1 : 0;

  // ===== Compact variant (inside WorkUnitCard) =====
  if (variant === 'compact') {
    return (
      <button
        className={`
          flex items-center gap-2 px-2.5 py-1.5 rounded-md cursor-pointer transition-colors w-full text-left border-0 bg-transparent
          ${selected ? 'bg-blue-50 ring-1 ring-blue-200/80' : 'hover:bg-gray-50/60'}
        `}
        onClick={onClick}
        type="button"
      >
        {/* Skill: sparkle icon + skill name (checked first, before execute) */}
        {isSkill && (
          <>
            <span className="flex items-center justify-center w-[18px] h-[18px] rounded bg-violet-50 border border-violet-200 flex-shrink-0">
              <Sparkles className="text-violet-500" size={11} />
            </span>
            <span className="text-xs text-violet-700 font-medium truncate flex-1 min-w-0">
              {getSkillName(item)}
            </span>
          </>
        )}

        {/* Edit: op badge + filename + stats */}
        {!isSkill && item.kind === 'edit' && (
          <>
            {fileOp && <OpBadge op={fileOp} />}
            <span className="text-xs text-gray-700 font-medium truncate flex-1 min-w-0">
              {fileName || item.title}
              {extraFileCount > 0 && (
                <span className="text-gray-400 font-normal ml-1">+{extraFileCount}</span>
              )}
            </span>
            <DiffStatsDisplay content={item.content} />
          </>
        )}

        {/* Read: eye icon + filename */}
        {!isSkill && item.kind === 'read' && (
          <>
            <Eye className="text-blue-400/70 flex-shrink-0" size={14} />
            <span className="text-xs text-gray-500 truncate flex-1 min-w-0">
              {fileName || item.title}
            </span>
          </>
        )}

        {!isSkill && item.kind === 'delete' && (
          <>
            <Trash2 className="text-red-400 flex-shrink-0" size={14} />
            <span className="text-xs text-gray-600 truncate flex-1 min-w-0">
              {fileName || item.title}
            </span>
          </>
        )}

        {!isSkill && item.kind === 'move' && (
          <>
            <ArrowRightLeft className="text-indigo-400 flex-shrink-0" size={14} />
            <span className="text-xs text-gray-600 truncate flex-1 min-w-0">
              {fileName || item.title}
            </span>
          </>
        )}

        {!isSkill && item.kind === 'search' && (
          <>
            <Search className="text-cyan-500 flex-shrink-0" size={14} />
            <span className="text-xs text-gray-600 truncate flex-1 min-w-0">{item.title}</span>
          </>
        )}

        {/* Execute: terminal icon + command */}
        {!isSkill && item.kind === 'execute' && (
          <>
            <Terminal className="text-emerald-500 flex-shrink-0" size={14} />
            <code className="text-xs text-gray-600 truncate flex-1 min-w-0 font-mono">
              {command || item.title}
            </code>
          </>
        )}

        {!isSkill && item.kind === 'think' && (
          <>
            <Brain className="text-purple-500 flex-shrink-0" size={14} />
            <span className="text-xs text-gray-600 truncate flex-1 min-w-0">{item.title}</span>
          </>
        )}

        {!isSkill && item.kind === 'fetch' && (
          <>
            <CloudDownload className="text-sky-500 flex-shrink-0" size={14} />
            <span className="text-xs text-gray-600 truncate flex-1 min-w-0">{item.title}</span>
          </>
        )}

        {!isSkill && item.kind === 'switch_mode' && (
          <>
            <Settings2 className="text-amber-500 flex-shrink-0" size={14} />
            <span className="text-xs text-gray-600 truncate flex-1 min-w-0">{item.title}</span>
          </>
        )}

        {!isSkill &&
          item.kind === 'other' &&
          (() => {
            const mcpInfo = getMcpInfo(item);
            if (mcpInfo) {
              return (
                <>
                  <span className="flex items-center justify-center w-[18px] h-[18px] rounded bg-blue-50 border border-blue-200 flex-shrink-0">
                    <Mcp className="w-2.5 h-2.5 fill-blue-500" />
                  </span>
                  <span className="text-xs text-blue-700 font-medium truncate min-w-0">
                    {mcpInfo.toolName}
                  </span>
                  <span className="text-[10px] text-gray-400 truncate min-w-0 flex-1">
                    {mcpInfo.serverName}
                  </span>
                </>
              );
            }
            return (
              <>
                <CircleHelp className="text-gray-400 flex-shrink-0" size={14} />
                <span className="text-xs text-gray-500 truncate flex-1 min-w-0">{item.title}</span>
              </>
            );
          })()}

        <StatusBadge item={item} />
      </button>
    );
  }

  // ===== Default variant (standalone in ChatStream) =====
  const isFailed = item.status === 'failed';

  // Skill kind (checked first, before execute fallback)
  if (isSkill) {
    const skillName = getSkillName(item);
    const inProgress = item.status === 'in_progress' || item.status === 'pending';
    return (
      <div
        className={`
          rounded-lg border cursor-pointer transition-all duration-200 overflow-hidden
          ${
            selected
              ? 'border-violet-300 bg-violet-50/40 shadow-sm'
              : isFailed
                ? 'border-red-200 bg-red-50/30'
                : 'border-violet-200/60 bg-gradient-to-r from-violet-50/50 to-white hover:border-violet-300 hover:shadow-sm'
          }
        `}
        onClick={onClick}
        onKeyDown={(e) => {
          if (e.key === 'Enter' || e.key === ' ') {
            e.preventDefault();
            onClick();
          }
        }}
        role="button"
        tabIndex={0}
      >
        <div className="px-3 py-2.5">
          <div className="flex items-center gap-2.5">
            <span
              className={`
                flex items-center justify-center w-[22px] h-[22px] rounded-md flex-shrink-0
                ${isFailed ? 'bg-red-50 border border-red-200' : 'bg-violet-100/80 border border-violet-200'}
              `}
            >
              <Sparkles className={isFailed ? 'text-red-400' : 'text-violet-500'} size={13} />
            </span>
            <div className="flex flex-col flex-1 min-w-0">
              <span className="text-sm text-gray-800 font-medium truncate">{skillName}</span>
              <span
                className={`text-[11px] ${isFailed ? 'text-red-400' : inProgress ? 'text-violet-400' : 'text-gray-400'}`}
              >
                {isFailed ? '技能执行失败' : inProgress ? '技能执行中...' : '技能已完成'}
              </span>
            </div>
            <StatusBadge item={item} />
          </div>
        </div>
      </div>
    );
  }

  // Edit kind
  if (item.kind === 'edit') {
    const dirHint = filePath && fileName ? extractDirHint(filePath, fileName) : null;
    const diffs = (item.content ?? []).filter((c) => c.type === 'diff');
    const hasDiff = diffs.length > 0;
    return (
      <EditCard
        diffs={diffs}
        dirHint={dirHint}
        extraFileCount={extraFileCount}
        fileName={fileName}
        fileOp={fileOp}
        hasDiff={hasDiff}
        isFailed={isFailed}
        item={item}
        onClick={onClick}
        selected={selected}
      />
    );
  }

  // Read kind — lightweight inline style
  if (item.kind === 'read') {
    return (
      <div
        className="flex items-center gap-2 px-1 py-1 cursor-pointer hover:opacity-70 transition-opacity"
        onClick={onClick}
        onKeyDown={(e) => {
          if (e.key === 'Enter' || e.key === ' ') {
            e.preventDefault();
            onClick();
          }
        }}
        role="button"
        tabIndex={0}
      >
        <Eye className="text-gray-300 flex-shrink-0" size={13} />
        <span className="text-xs text-gray-400 truncate flex-1 min-w-0">
          已查看 {fileName || item.title}
        </span>
        <StatusBadge item={item} />
      </div>
    );
  }

  if (item.kind === 'delete' || item.kind === 'move') {
    const Icon = item.kind === 'delete' ? Trash2 : ArrowRightLeft;
    const iconCls = item.kind === 'delete' ? 'text-red-500' : 'text-indigo-500';
    return (
      <div
        className={`
          rounded-lg border cursor-pointer transition-all duration-200
          ${
            selected
              ? 'border-blue-300 bg-blue-50/40 shadow-sm'
              : isFailed
                ? 'border-red-200 bg-red-50/30'
                : 'border-gray-200 bg-white hover:border-gray-300 hover:shadow-sm'
          }
        `}
        onClick={onClick}
        onKeyDown={(e) => {
          if (e.key === 'Enter' || e.key === ' ') {
            e.preventDefault();
            onClick();
          }
        }}
        role="button"
        tabIndex={0}
      >
        <div className="px-3 py-2 flex items-center gap-2">
          <Icon className={`${iconCls} flex-shrink-0`} size={15} />
          <span className="text-sm text-gray-600 truncate flex-1 min-w-0">
            {fileName || item.title}
          </span>
          <StatusBadge item={item} />
        </div>
      </div>
    );
  }

  if (
    item.kind === 'search' ||
    item.kind === 'think' ||
    item.kind === 'fetch' ||
    item.kind === 'switch_mode' ||
    item.kind === 'other'
  ) {
    // MCP tool_call: render as a styled card
    const mcpInfo = item.kind === 'other' ? getMcpInfo(item) : null;
    if (mcpInfo) {
      const inProgress = item.status === 'in_progress' || item.status === 'pending';
      return (
        <div
          className={`
            rounded-lg border cursor-pointer transition-all duration-200 overflow-hidden
            ${
              selected
                ? 'border-blue-300 bg-blue-50/40 shadow-sm'
                : isFailed
                  ? 'border-red-200 bg-red-50/30'
                  : 'border-blue-200/60 bg-gradient-to-r from-blue-50/40 to-white hover:border-blue-300 hover:shadow-sm'
            }
          `}
          onClick={onClick}
          onKeyDown={(e) => {
            if (e.key === 'Enter' || e.key === ' ') {
              e.preventDefault();
              onClick();
            }
          }}
          role="button"
          tabIndex={0}
        >
          <div className="px-3 py-2.5">
            <div className="flex items-center gap-2.5">
              <span
                className={`
                  flex items-center justify-center w-[22px] h-[22px] rounded-md flex-shrink-0
                  ${isFailed ? 'bg-red-50 border border-red-200' : 'bg-blue-100/80 border border-blue-200'}
                `}
              >
                <Mcp className={`w-3 h-3 ${isFailed ? 'fill-red-400' : 'fill-blue-500'}`} />
              </span>
              <div className="flex flex-col flex-1 min-w-0">
                <div className="flex items-center gap-1.5">
                  <span className="text-sm text-gray-800 font-medium truncate">
                    {mcpInfo.toolName}
                  </span>
                  <span className="text-[11px] text-gray-400 truncate flex-shrink-0">
                    {mcpInfo.serverName}
                  </span>
                </div>
                {mcpInfo.paramsSummary ? (
                  <span className="text-[11px] text-gray-400 truncate">
                    {mcpInfo.paramsSummary}
                  </span>
                ) : (
                  <span
                    className={`text-[11px] ${isFailed ? 'text-red-400' : inProgress ? 'text-blue-400' : 'text-gray-400'}`}
                  >
                    {isFailed ? 'MCP 调用失败' : inProgress ? 'MCP 调用中...' : 'MCP 调用完成'}
                  </span>
                )}
              </div>
              <StatusBadge item={item} />
            </div>
          </div>
        </div>
      );
    }

    // Non-MCP "other" and other lightweight kinds
    const Icon =
      item.kind === 'search'
        ? Search
        : item.kind === 'think'
          ? Brain
          : item.kind === 'fetch'
            ? CloudDownload
            : item.kind === 'switch_mode'
              ? Settings2
              : CircleHelp;
    const label =
      item.kind === 'search'
        ? '搜索'
        : item.kind === 'think'
          ? '思考'
          : item.kind === 'fetch'
            ? '抓取'
            : item.kind === 'switch_mode'
              ? '切换模式'
              : '操作';
    return (
      <div
        className="flex items-center gap-2 px-1 py-1 cursor-pointer hover:opacity-70 transition-opacity"
        onClick={onClick}
        onKeyDown={(e) => {
          if (e.key === 'Enter' || e.key === ' ') {
            e.preventDefault();
            onClick();
          }
        }}
        role="button"
        tabIndex={0}
      >
        <Icon className="text-gray-300 flex-shrink-0" size={13} />
        <span className="text-xs text-gray-400 truncate flex-1 min-w-0">
          {label} {item.title}
        </span>
        <StatusBadge item={item} />
      </div>
    );
  }

  // Execute kind
  return (
    <ExecuteCard
      command={command}
      isFailed={isFailed}
      item={item}
      onClick={onClick}
      selected={selected}
      terminalId={terminalId}
    />
  );
}

// ===== EditCard with expandable diff =====

function EditCard({
  diffs,
  dirHint,
  extraFileCount,
  fileName,
  fileOp,
  hasDiff,
  isFailed,
  item,
  onClick,
  selected,
}: {
  item: ChatItemToolCall;
  selected: boolean;
  isFailed: boolean;
  fileOp: FileOp | null;
  fileName: string | null;
  dirHint: string | null;
  extraFileCount: number;
  diffs: ToolCallContentItem[];
  hasDiff: boolean;
  onClick: () => void;
}) {
  const [diffExpanded, setDiffExpanded] = useState(false);

  return (
    <div
      className={`
        rounded-lg border cursor-pointer transition-all duration-200 overflow-hidden
        ${
          selected
            ? 'border-blue-300 bg-blue-50/40 shadow-sm'
            : isFailed
              ? 'border-red-200 bg-red-50/30'
              : 'border-gray-200 bg-white hover:border-gray-300 hover:shadow-sm'
        }
      `}
    >
      <div
        className="px-3 py-2.5"
        onClick={onClick}
        onKeyDown={(e) => {
          if (e.key === 'Enter' || e.key === ' ') {
            e.preventDefault();
            onClick();
          }
        }}
        role="button"
        tabIndex={0}
      >
        <div className="flex items-center gap-2">
          {fileOp && <OpBadge op={fileOp} />}
          <span className="text-sm text-gray-800 font-medium truncate flex-1 min-w-0">
            {fileName || item.title}
            {extraFileCount > 0 && (
              <span className="text-gray-400 font-normal text-xs ml-1.5">
                +{extraFileCount} files
              </span>
            )}
          </span>
          <DiffStatsDisplay content={item.content} />
          {hasDiff && (
            <button
              className="p-0.5 rounded hover:bg-gray-100 transition-colors"
              onClick={(e) => {
                e.stopPropagation();
                setDiffExpanded((prev) => !prev);
              }}
            >
              {diffExpanded ? (
                <ChevronDown className="text-gray-400" size={13} />
              ) : (
                <ChevronRight className="text-gray-400" size={13} />
              )}
            </button>
          )}
          <StatusBadge item={item} />
        </div>
        {dirHint && (
          <div className="mt-1 text-[11px] text-gray-400 truncate pl-[26px]">{dirHint}</div>
        )}
      </div>
      {diffExpanded && hasDiff && (
        <div className="border-t border-gray-100 max-h-[300px] overflow-y-auto">
          {diffs.map((d, i) => (
            <DiffViewer
              key={i}
              newText={d.type === 'diff' ? d.newText : undefined}
              oldText={d.type === 'diff' ? d.oldText : undefined}
              path={d.type === 'diff' ? (d.path ? extractFileName(d.path) : undefined) : undefined}
            />
          ))}
        </div>
      )}
    </div>
  );
}

// ===== ExecuteCard with expandable command =====

function ExecuteCard({
  command,
  isFailed,
  item,
  onClick,
  selected,
  terminalId,
}: {
  item: ChatItemToolCall;
  selected: boolean;
  isFailed: boolean;
  command: string | null;
  terminalId: string | null;
  onClick: () => void;
}) {
  const [cmdExpanded, setCmdExpanded] = useState(false);

  return (
    <div
      className={`
        rounded-lg border cursor-pointer transition-all duration-200 overflow-hidden
        ${
          selected
            ? 'border-blue-300 bg-blue-50/40 shadow-sm'
            : isFailed
              ? 'border-red-200 bg-red-50/30'
              : 'border-gray-200 bg-white hover:border-gray-300 hover:shadow-sm'
        }
      `}
      onClick={onClick}
      onKeyDown={(e) => {
        if (e.key === 'Enter' || e.key === ' ') {
          e.preventDefault();
          onClick();
        }
      }}
      role="button"
      tabIndex={0}
    >
      <div className="px-3 py-2.5">
        <div className="flex items-center gap-2">
          <Terminal className="text-emerald-500 flex-shrink-0" size={15} />
          <span className="text-sm text-gray-700 font-medium truncate flex-1 min-w-0">
            {item.title}
          </span>
          <StatusBadge item={item} />
        </div>
        {command && (
          <div
            className={`mt-1.5 bg-gray-900 rounded-md px-2.5 py-1.5 font-mono text-[11px] text-gray-300 ${
              cmdExpanded ? 'whitespace-pre-wrap break-all' : 'truncate'
            }`}
            onClick={(e) => {
              e.stopPropagation();
              setCmdExpanded((prev) => !prev);
            }}
            onKeyDown={(e) => {
              if (e.key === 'Enter' || e.key === ' ') {
                e.preventDefault();
                e.stopPropagation();
                setCmdExpanded((prev) => !prev);
              }
            }}
            role="button"
            tabIndex={0}
          >
            <span className="text-emerald-400 mr-1.5 select-none">$</span>
            {command}
          </div>
        )}
        {terminalId && (
          <div className="mt-1 text-[11px] text-gray-400 truncate pl-[23px]">
            terminal: {terminalId}
          </div>
        )}
        {item.status === 'completed' && getOutputPreview(item.content) && (
          <div className="mt-1 text-[11px] text-gray-400 truncate pl-[23px]">
            {getOutputPreview(item.content)}
          </div>
        )}
      </div>
    </div>
  );
}
