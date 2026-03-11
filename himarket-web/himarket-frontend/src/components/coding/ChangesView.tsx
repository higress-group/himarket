import { useMemo } from "react";
import { useActiveCodingSession } from "../../context/CodingSessionContext";
import { DiffViewer } from "./DiffViewer";
import type { ChatItemToolCall } from "../../types/coding-protocol";

interface DiffEntry {
  path: string;
  oldText: string | null | undefined;
  newText: string;
  toolTitle: string;
}

export function ChangesView() {
  const quest = useActiveCodingSession();
  const messages = quest?.messages;

  const diffs = useMemo<DiffEntry[]>(() => {
    if (!messages) return [];
    const result: DiffEntry[] = [];
    for (const m of messages) {
      if (m.type !== "tool_call") continue;
      const tc = m as ChatItemToolCall;
      for (const c of tc.content ?? []) {
        if (
          c.type === "diff" &&
          (c.oldText !== undefined || c.newText !== undefined)
        ) {
          result.push({
            path: c.path ?? "unknown",
            oldText: c.oldText,
            newText: c.newText ?? "",
            toolTitle: tc.title,
          });
        }
      }
    }
    // Deduplicate: keep latest diff per path
    const byPath = new Map<string, DiffEntry>();
    for (const d of result) {
      byPath.set(d.path, d);
    }
    return Array.from(byPath.values());
  }, [messages]);

  if (diffs.length === 0) {
    return (
      <div className="flex items-center justify-center h-full text-gray-400 text-sm">
        No changes yet
      </div>
    );
  }

  return (
    <div className="p-4 space-y-3">
      <div className="text-xs text-gray-400 mb-2">
        {diffs.length} file{diffs.length !== 1 ? "s" : ""} changed
      </div>
      {diffs.map((d, i) => (
        <DiffViewer
          key={`${d.path}-${i}`}
          path={d.path}
          oldText={d.oldText}
          newText={d.newText}
        />
      ))}
    </div>
  );
}
