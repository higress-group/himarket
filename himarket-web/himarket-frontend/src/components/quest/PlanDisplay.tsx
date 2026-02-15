import { useState, useMemo } from "react";
import {
  CheckCircle2,
  Circle,
  Loader2,
  ChevronDown,
  ChevronRight,
  ClipboardList,
} from "lucide-react";

interface PlanEntry {
  content: string;
  status: "pending" | "in_progress" | "completed";
  priority?: "low" | "medium" | "high";
}

interface PlanDisplayProps {
  entries: PlanEntry[];
  variant?: "sidebar" | "inline";
}

export function PlanDisplay({
  entries,
  variant = "sidebar",
}: PlanDisplayProps) {
  const isInline = variant === "inline";
  const [expanded, setExpanded] = useState(!isInline);

  const summary = useMemo(() => {
    const completed = entries.filter(e => e.status === "completed").length;
    const total = entries.length;
    return { completed, total, text: `${completed}/${total}` };
  }, [entries]);

  const hasInProgress = entries.some(e => e.status === "in_progress");

  if (isInline) {
    return (
      <div className="py-0.5">
        <button
          className="flex items-center gap-2 w-full py-1.5 px-2 rounded-lg text-left hover:bg-gray-50 transition-colors"
          onClick={() => setExpanded(prev => !prev)}
        >
          {hasInProgress ? (
            <Loader2 size={14} className="text-blue-500 animate-spin" />
          ) : summary.completed === summary.total ? (
            <CheckCircle2 size={14} className="text-green-500" />
          ) : (
            <ClipboardList size={14} className="text-blue-500" />
          )}
          <span className="text-xs font-semibold text-gray-400 uppercase tracking-wide">
            Todo
          </span>
          <span className="flex-1" />
          {/* Progress bar */}
          <div className="w-16 h-1.5 bg-blue-100 rounded-full overflow-hidden">
            <div
              className="h-full bg-blue-500 rounded-full transition-all duration-500"
              style={{
                width: `${summary.total > 0 ? (summary.completed / summary.total) * 100 : 0}%`,
              }}
            />
          </div>
          <span className="text-[10px] text-blue-500/70 ml-1">
            {summary.text}
          </span>
          {expanded ? (
            <ChevronDown size={14} className="text-blue-400" />
          ) : (
            <ChevronRight size={14} className="text-blue-400" />
          )}
        </button>
        {expanded && (
          <div className="mt-1.5 space-y-1.5 px-2">
            {entries.map((entry, i) => (
              <div key={i} className="flex items-start gap-2">
                <span className="mt-0.5 flex-shrink-0">
                  {entry.status === "completed" && (
                    <CheckCircle2 size={13} className="text-green-500" />
                  )}
                  {entry.status === "in_progress" && (
                    <Loader2 size={13} className="text-blue-500 animate-spin" />
                  )}
                  {entry.status === "pending" && (
                    <Circle size={13} className="text-blue-300" />
                  )}
                </span>
                <span
                  className={`text-[13px] leading-snug ${
                    entry.status === "completed"
                      ? "text-gray-300 line-through"
                      : entry.status === "in_progress"
                        ? "text-blue-700 font-medium"
                        : "text-gray-400"
                  }`}
                >
                  {entry.content}
                </span>
              </div>
            ))}
          </div>
        )}
      </div>
    );
  }

  // Sidebar variant
  return (
    <div className="py-0.5">
      <button
        className="flex items-center gap-2 w-full py-1.5 px-2 rounded-lg text-left hover:bg-gray-50 transition-colors"
        onClick={() => setExpanded(prev => !prev)}
      >
        {hasInProgress ? (
          <Loader2 size={14} className="text-blue-500 animate-spin" />
        ) : (
          <CheckCircle2 size={14} className="text-green-500" />
        )}
        <span className="text-xs font-semibold text-gray-500 uppercase tracking-wide flex-1">
          Todo
        </span>
        <span className="text-[10px] text-gray-400">{summary.text}</span>
        {expanded ? (
          <ChevronDown size={14} className="text-gray-400" />
        ) : (
          <ChevronRight size={14} className="text-gray-400" />
        )}
      </button>
      {expanded && (
        <div className="mt-1.5 space-y-1.5 px-2">
          {entries.map((entry, i) => (
            <div key={i} className="flex items-start gap-2">
              <span className="mt-0.5 flex-shrink-0">
                {entry.status === "completed" && (
                  <CheckCircle2 size={14} className="text-green-500" />
                )}
                {entry.status === "in_progress" && (
                  <Loader2 size={14} className="text-blue-500 animate-spin" />
                )}
                {entry.status === "pending" && (
                  <Circle size={14} className="text-gray-300" />
                )}
              </span>
              <span
                className={`text-[13px] leading-snug ${
                  entry.status === "completed"
                    ? "text-gray-400 line-through"
                    : "text-gray-600"
                }`}
              >
                {entry.content}
              </span>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
