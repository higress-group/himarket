import {
  Eye,
  Terminal,
  CheckCircle2,
  XCircle,
  Loader2,
  Sparkles,
} from "lucide-react";
import type { ChatItemToolCall, ToolCallContentItem } from "../../types/acp";

interface ToolCallCardProps {
  item: ChatItemToolCall;
  selected: boolean;
  onClick: () => void;
  variant?: "default" | "compact";
}

// ===== Operation type detection =====

type FileOp = "A" | "M" | "D";

const opStyles: Record<FileOp, string> = {
  A: "bg-green-50 text-green-600 border border-green-200",
  M: "bg-amber-50 text-amber-600 border border-amber-200",
  D: "bg-red-50 text-red-500 border border-red-200",
};

function getFileOp(item: ChatItemToolCall): FileOp {
  if (item.content) {
    const diffs = item.content.filter(c => c.type === "diff");
    if (diffs.length > 0) {
      const hasOld = diffs.some(d => d.oldText != null);
      const hasNew = diffs.some(d => d.newText != null);
      if (!hasOld && hasNew) return "A";
      if (hasOld && !hasNew) return "D";
      if (hasOld && hasNew) return "M";
    }
  }
  // Write tool (rawInput.content exists) → create
  if (item.rawInput && typeof item.rawInput.content === "string") return "A";
  return "M";
}

// ===== Path helpers =====

function getFilePath(item: ChatItemToolCall): string | null {
  if (item.rawInput) {
    if (typeof item.rawInput.file_path === "string")
      return item.rawInput.file_path;
    if (typeof item.rawInput.path === "string") return item.rawInput.path;
  }
  if (item.locations && item.locations.length > 0) return item.locations[0].path;
  return null;
}

/** Get just the basename from a full file path */
export function extractFileName(path: string): string {
  const parts = path.split(/[/\\]/);
  return parts[parts.length - 1] || path;
}

/** Get the parent directory (last 2 segments) from a full path, for context */
function extractDirHint(path: string, fileName: string): string | null {
  const withoutFile = path.slice(0, path.length - fileName.length - 1);
  if (!withoutFile) return null;
  const parts = withoutFile.split(/[/\\]/);
  // Show last 2 segments for context
  return parts.slice(-2).join("/");
}

// ===== Diff stats =====

export function getDiffStats(
  content?: ToolCallContentItem[]
): { added: number; removed: number } | null {
  if (!content) return null;
  const diffs = content.filter(c => c.type === "diff");
  if (diffs.length === 0) return null;

  let added = 0;
  let removed = 0;
  for (const d of diffs) {
    if (d.newText) added += d.newText.split("\n").length;
    if (d.oldText) removed += d.oldText.split("\n").length;
  }

  return added > 0 || removed > 0 ? { added, removed } : null;
}

// ===== Command helpers =====

function getCommand(item: ChatItemToolCall): string | null {
  if (item.rawInput && typeof item.rawInput.command === "string") {
    return item.rawInput.command;
  }
  return null;
}

/** Extract skill name from title like "Skill qoder-ppt" → "qoder-ppt" */
function getSkillName(item: ChatItemToolCall): string {
  const title = item.title || "";
  const match = title.match(/^Skill\s+(.+)$/i);
  return match ? match[1] : title;
}

/** Detect skill tool_call by kind or title pattern */
function isSkillItem(item: ChatItemToolCall): boolean {
  if (item.kind === "skill") return true;
  return /^Skill\s+/i.test(item.title || "");
}

function getOutputPreview(content?: ToolCallContentItem[]): string | null {
  if (!content) return null;
  const textItems = content.filter(
    c => c.type === "content" && c.content?.text
  );
  if (textItems.length === 0) return null;
  const text = textItems[0].content?.text ?? "";
  const firstLine = text.split("\n").filter(l => l.trim())[0] ?? "";
  if (firstLine.length > 80) return firstLine.slice(0, 80) + "...";
  return firstLine || null;
}

// ===== Sub-components =====

function StatusBadge({ item }: { item: ChatItemToolCall }) {
  const isCompleted = item.status === "completed";
  const isFailed = item.status === "failed";
  const inProgress = item.status === "in_progress" || item.status === "pending";

  if (inProgress)
    return (
      <Loader2
        size={13}
        className="text-blue-500 animate-spin flex-shrink-0"
      />
    );
  if (isFailed)
    return <XCircle size={13} className="text-red-500 flex-shrink-0" />;
  if (isCompleted)
    return (
      <CheckCircle2 size={13} className="text-green-500/70 flex-shrink-0" />
    );
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
      {stats.added > 0 && (
        <span className="text-green-600">+{stats.added}</span>
      )}
      {stats.removed > 0 && (
        <span className="text-red-500">-{stats.removed}</span>
      )}
    </span>
  );
}

// ===== Main component =====

export function ToolCallCard({
  item,
  selected,
  onClick,
  variant = "default",
}: ToolCallCardProps) {
  const isSkill = isSkillItem(item);
  const filePath = item.kind !== "execute" && !isSkill ? getFilePath(item) : null;
  const fileName = filePath ? extractFileName(filePath) : null;
  const fileOp = item.kind === "edit" ? getFileOp(item) : null;
  const command = item.kind === "execute" && !isSkill ? getCommand(item) : null;
  const extraFileCount =
    item.locations && item.locations.length > 1
      ? item.locations.length - 1
      : 0;

  // ===== Compact variant (inside WorkUnitCard) =====
  if (variant === "compact") {
    return (
      <div
        className={`
          flex items-center gap-2 px-2.5 py-1.5 rounded-md cursor-pointer transition-colors
          ${selected ? "bg-blue-50 ring-1 ring-blue-200/80" : "hover:bg-gray-50/60"}
        `}
        onClick={onClick}
      >
        {/* Skill: sparkle icon + skill name (checked first, before execute) */}
        {isSkill && (
          <>
            <span className="flex items-center justify-center w-[18px] h-[18px] rounded bg-violet-50 border border-violet-200 flex-shrink-0">
              <Sparkles size={11} className="text-violet-500" />
            </span>
            <span className="text-xs text-violet-700 font-medium truncate flex-1 min-w-0">
              {getSkillName(item)}
            </span>
          </>
        )}

        {/* Edit: op badge + filename + stats */}
        {!isSkill && item.kind === "edit" && (
          <>
            {fileOp && <OpBadge op={fileOp} />}
            <span className="text-xs text-gray-700 font-medium truncate flex-1 min-w-0">
              {fileName || item.title}
              {extraFileCount > 0 && (
                <span className="text-gray-400 font-normal ml-1">
                  +{extraFileCount}
                </span>
              )}
            </span>
            <DiffStatsDisplay content={item.content} />
          </>
        )}

        {/* Read: eye icon + filename */}
        {!isSkill && item.kind === "read" && (
          <>
            <Eye size={14} className="text-blue-400/70 flex-shrink-0" />
            <span className="text-xs text-gray-500 truncate flex-1 min-w-0">
              {fileName || item.title}
            </span>
          </>
        )}

        {/* Execute: terminal icon + command */}
        {!isSkill && item.kind === "execute" && (
          <>
            <Terminal size={14} className="text-emerald-500 flex-shrink-0" />
            <code className="text-xs text-gray-600 truncate flex-1 min-w-0 font-mono">
              {command || item.title}
            </code>
          </>
        )}

        {/* Fallback for unknown kinds */}
        {!isSkill && item.kind !== "edit" && item.kind !== "read" && item.kind !== "execute" && (
          <span className="text-xs text-gray-500 truncate flex-1 min-w-0">
            {item.title}
          </span>
        )}

        <StatusBadge item={item} />
      </div>
    );
  }

  // ===== Default variant (standalone in ChatStream) =====
  const isFailed = item.status === "failed";

  // Skill kind (checked first, before execute fallback)
  if (isSkill) {
    const skillName = getSkillName(item);
    const inProgress = item.status === "in_progress" || item.status === "pending";
    return (
      <div
        className={`
          rounded-lg border cursor-pointer transition-all duration-200 overflow-hidden
          ${
            selected
              ? "border-violet-300 bg-violet-50/40 shadow-sm"
              : isFailed
                ? "border-red-200 bg-red-50/30"
                : "border-violet-200/60 bg-gradient-to-r from-violet-50/50 to-white hover:border-violet-300 hover:shadow-sm"
          }
        `}
        onClick={onClick}
      >
        <div className="px-3 py-2.5">
          <div className="flex items-center gap-2.5">
            <span
              className={`
                flex items-center justify-center w-[22px] h-[22px] rounded-md flex-shrink-0
                ${isFailed ? "bg-red-50 border border-red-200" : "bg-violet-100/80 border border-violet-200"}
              `}
            >
              <Sparkles size={13} className={isFailed ? "text-red-400" : "text-violet-500"} />
            </span>
            <div className="flex flex-col flex-1 min-w-0">
              <span className="text-sm text-gray-800 font-medium truncate">
                {skillName}
              </span>
              <span className={`text-[11px] ${isFailed ? "text-red-400" : inProgress ? "text-violet-400" : "text-gray-400"}`}>
                {isFailed ? "技能执行失败" : inProgress ? "技能执行中..." : "技能已完成"}
              </span>
            </div>
            <StatusBadge item={item} />
          </div>
        </div>
      </div>
    );
  }

  // Edit kind
  if (item.kind === "edit") {
    const dirHint =
      filePath && fileName ? extractDirHint(filePath, fileName) : null;
    return (
      <div
        className={`
          rounded-lg border cursor-pointer transition-all duration-200 overflow-hidden
          ${
            selected
              ? "border-blue-300 bg-blue-50/40 shadow-sm"
              : isFailed
                ? "border-red-200 bg-red-50/30"
                : "border-gray-200 bg-white hover:border-gray-300 hover:shadow-sm"
          }
        `}
        onClick={onClick}
      >
        <div className="px-3 py-2.5">
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
            <StatusBadge item={item} />
          </div>
          {dirHint && (
            <div className="mt-1 text-[11px] text-gray-400 truncate pl-[26px]">
              {dirHint}
            </div>
          )}
        </div>
      </div>
    );
  }

  // Read kind
  if (item.kind === "read") {
    return (
      <div
        className={`
          rounded-lg border cursor-pointer transition-all duration-200
          ${
            selected
              ? "border-blue-300 bg-blue-50/40 shadow-sm"
              : "border-gray-200 bg-white hover:border-gray-300 hover:shadow-sm"
          }
        `}
        onClick={onClick}
      >
        <div className="px-3 py-2 flex items-center gap-2">
          <Eye size={15} className="text-blue-400/70 flex-shrink-0" />
          <span className="text-sm text-gray-600 truncate flex-1 min-w-0">
            {fileName || item.title}
          </span>
          <StatusBadge item={item} />
        </div>
      </div>
    );
  }

  // Execute kind (fallback)
  return (
    <div
      className={`
        rounded-lg border cursor-pointer transition-all duration-200 overflow-hidden
        ${
          selected
            ? "border-blue-300 bg-blue-50/40 shadow-sm"
            : isFailed
              ? "border-red-200 bg-red-50/30"
              : "border-gray-200 bg-white hover:border-gray-300 hover:shadow-sm"
        }
      `}
      onClick={onClick}
    >
      <div className="px-3 py-2.5">
        <div className="flex items-center gap-2">
          <Terminal size={15} className="text-emerald-500 flex-shrink-0" />
          <span className="text-sm text-gray-700 font-medium truncate flex-1 min-w-0">
            {item.title}
          </span>
          <StatusBadge item={item} />
        </div>
        {command && (
          <div className="mt-1.5 bg-gray-900 rounded-md px-2.5 py-1.5 font-mono text-[11px] text-gray-300 truncate">
            <span className="text-emerald-400 mr-1.5 select-none">$</span>
            {command}
          </div>
        )}
        {item.status === "completed" && getOutputPreview(item.content) && (
          <div className="mt-1 text-[11px] text-gray-400 truncate pl-[23px]">
            {getOutputPreview(item.content)}
          </div>
        )}
      </div>
    </div>
  );
}
