import { useState } from "react";
import { ChevronRight, ChevronDown } from "lucide-react";
import { useActiveQuest } from "../../context/CodingSessionContext";
import { DiffViewer } from "./DiffViewer";
import { TerminalOutput } from "./TerminalOutput";
import type { ChatItemToolCall } from "../../types/acp";

interface ToolPanelProps {
  collapsed: boolean;
  onToggleCollapse: () => void;
}

export function ToolPanel({ collapsed, onToggleCollapse }: ToolPanelProps) {
  const quest = useActiveQuest();
  const [jsonExpanded, setJsonExpanded] = useState(false);

  const selectedTool = quest?.selectedToolCallId
    ? (quest.messages.find(
        m => m.type === "tool_call" && m.toolCallId === quest.selectedToolCallId
      ) as ChatItemToolCall | undefined)
    : null;

  if (!selectedTool) return null;
  if (collapsed) return null;

  const diffs = selectedTool.content?.filter(c => c.type === "diff") ?? [];
  const textContent =
    selectedTool.content?.filter(c => c.type === "content") ?? [];

  return (
    <div className="w-[420px] flex-shrink-0 border-l border-gray-200/60 bg-white/30 backdrop-blur-sm flex flex-col">
      <div className="flex items-center justify-between px-4 py-2 border-b border-gray-200/60">
        <span className="text-sm font-medium text-gray-700 truncate">
          {selectedTool.title} — {selectedTool.status.replace("_", " ")}
        </span>
        <button
          className="w-6 h-6 flex items-center justify-center rounded text-gray-400
                     hover:text-gray-600 hover:bg-gray-100 transition-colors"
          onClick={onToggleCollapse}
          title="收起面板"
        >
          <ChevronRight size={16} />
        </button>
      </div>
      <div className="flex-1 overflow-y-auto p-4 space-y-3">
        {diffs.map((d, i) => (
          <DiffViewer
            key={i}
            path={d.path}
            oldText={d.oldText}
            newText={d.newText}
          />
        ))}
        {textContent.map((c, i) => (
          <TerminalOutput key={i} text={c.content?.text ?? ""} />
        ))}
        {selectedTool.rawInput && (
          <div className="rounded-lg border border-gray-200/60 overflow-hidden">
            <button
              className="flex items-center gap-1 w-full px-3 py-2 text-xs text-gray-500
                         hover:bg-gray-50 transition-colors"
              onClick={() => setJsonExpanded(!jsonExpanded)}
            >
              {jsonExpanded ? (
                <ChevronDown size={12} />
              ) : (
                <ChevronRight size={12} />
              )}
              Raw Input
            </button>
            {jsonExpanded && (
              <div className="px-3 pb-2 border-t border-gray-100">
                <pre className="text-xs font-mono text-gray-500 whitespace-pre-wrap overflow-x-auto">
                  {JSON.stringify(selectedTool.rawInput, null, 2)}
                </pre>
              </div>
            )}
          </div>
        )}
      </div>
    </div>
  );
}
