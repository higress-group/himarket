import {
  FileText,
  Pencil,
  Terminal,
  CheckCircle2,
  XCircle,
} from "lucide-react";
import type { ChatItemToolCall } from "../../types/acp";

interface ToolCallCardProps {
  item: ChatItemToolCall;
  selected: boolean;
  onClick: () => void;
}

const kindIcons = {
  read: FileText,
  edit: Pencil,
  execute: Terminal,
};

function getDetail(item: ChatItemToolCall): string | null {
  if (item.locations && item.locations.length > 0) {
    const first = item.locations[0].path;
    const rest = item.locations.length - 1;
    return rest > 0 ? `${first} +${rest} more` : first;
  }
  if (item.rawInput) {
    if (typeof item.rawInput.command === "string") return item.rawInput.command;
    if (typeof item.rawInput.file_path === "string")
      return item.rawInput.file_path;
    if (typeof item.rawInput.path === "string") return item.rawInput.path;
  }
  return null;
}

export function ToolCallCard({ item, selected, onClick }: ToolCallCardProps) {
  const KindIcon = kindIcons[item.kind] ?? Terminal;
  const isCompleted = item.status === "completed";
  const isFailed = item.status === "failed";
  const inProgress = item.status === "in_progress";
  const detail = getDetail(item);

  const StatusIcon = isFailed ? XCircle : isCompleted ? CheckCircle2 : KindIcon;
  const statusColor = isFailed
    ? "text-red-500"
    : isCompleted
      ? "text-green-500"
      : "text-gray-500";

  return (
    <div
      className={`
        rounded-xl border px-3 py-2.5 cursor-pointer transition-all duration-200
        ${
          selected
            ? "border-blue-300 bg-blue-50/50 shadow-sm"
            : "border-gray-200/60 bg-white/60 hover:bg-white/80 hover:shadow-sm"
        }
        ${inProgress ? "animate-pulse" : ""}
      `}
      onClick={onClick}
    >
      <div className="flex items-center gap-2">
        <StatusIcon size={15} className={statusColor} />
        <span className="text-sm text-gray-700 font-medium flex-1 truncate">
          {item.title}
        </span>
        <span
          className={`text-[10px] px-1.5 py-0.5 rounded-full
          ${
            isFailed
              ? "bg-red-100 text-red-600"
              : isCompleted
                ? "bg-green-100 text-green-600"
                : inProgress
                  ? "bg-blue-100 text-blue-600"
                  : "bg-gray-100 text-gray-500"
          }`}
        >
          {item.status.replace("_", " ")}
        </span>
      </div>
      {detail && (
        <div className="mt-1 text-xs text-gray-400 truncate pl-[23px]">
          {detail}
        </div>
      )}
    </div>
  );
}
