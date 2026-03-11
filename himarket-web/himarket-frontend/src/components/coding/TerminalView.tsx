import type { ChatItemToolCall } from "../../types/coding-protocol";
import { TerminalOutput } from "./TerminalOutput";

interface TerminalViewProps {
  terminalId: string;
  toolCall: ChatItemToolCall | null;
}

export function TerminalView({ terminalId, toolCall }: TerminalViewProps) {
  const outputs = (toolCall?.content ?? [])
    .filter(
      c =>
        c.type === "content" &&
        c.content?.type === "text" &&
        typeof c.content.text === "string" &&
        c.content.text.length > 0
    )
    .map(c => (c.type === "content" && c.content?.type === "text" ? c.content.text : ""))
    .filter(Boolean);

  return (
    <div className="p-4 space-y-3">
      <div className="rounded-lg border border-gray-200/70 bg-gray-50/70 px-3 py-2">
        <div className="text-xs text-gray-400">Terminal ID</div>
        <div className="text-sm font-mono text-gray-700 mt-0.5">{terminalId}</div>
      </div>
      {outputs.length > 0 ? (
        outputs.map((output, idx) => <TerminalOutput key={idx} text={output} />)
      ) : (
        <div className="rounded-lg border border-amber-200 bg-amber-50/70 px-3 py-2.5">
          <div className="text-xs text-amber-700 font-medium">终端输出暂不可用</div>
          <div className="text-xs text-amber-600 mt-1 leading-relaxed">
            当前前端仅展示 tool_call 中已上报的文本内容。尚未接入 terminal/output 的实时拉流能力。
          </div>
        </div>
      )}
    </div>
  );
}
