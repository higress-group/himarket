import { CheckCircle2, Circle, Loader2 } from "lucide-react";

interface PlanEntry {
  content: string;
  status: "pending" | "in_progress" | "completed";
  priority?: "low" | "medium" | "high";
}

interface PlanDisplayProps {
  entries: PlanEntry[];
}

export function PlanDisplay({ entries }: PlanDisplayProps) {
  return (
    <div className="rounded-xl border border-gray-200/60 bg-white/60 backdrop-blur-sm px-4 py-3">
      <div className="text-xs font-semibold text-gray-500 uppercase tracking-wide mb-2">
        Plan
      </div>
      <div className="space-y-1.5">
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
              className={`text-sm leading-snug ${
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
    </div>
  );
}
