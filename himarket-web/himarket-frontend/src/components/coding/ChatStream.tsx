import { useRef, useEffect, useMemo } from "react";
import { MessageCircle } from "lucide-react";
import { useActiveQuest } from "../../context/CodingSessionContext";
import { UserMessage } from "./UserMessage";
import { AgentMessage } from "./AgentMessage";
import { ThoughtBlock } from "./ThoughtBlock";
import { ToolCallCard } from "./ToolCallCard";
import { PlanDisplay } from "./PlanDisplay";

interface ChatStreamProps {
  onSelectToolCall: (toolCallId: string) => void;
}

export function ChatStream({ onSelectToolCall }: ChatStreamProps) {
  const quest = useActiveQuest();
  const bottomRef = useRef<HTMLDivElement>(null);

  const messages = useMemo(() => quest?.messages ?? [], [quest?.messages]);
  const selectedToolCallId = quest?.selectedToolCallId ?? null;
  const isProcessing = quest?.isProcessing ?? false;

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [messages]);

  if (messages.length === 0) {
    return (
      <div className="flex-1 flex items-center justify-center">
        <div className="text-center text-gray-400">
          <MessageCircle size={32} className="mx-auto mb-2 opacity-40" />
          <span className="text-sm">在下方输入消息开始对话</span>
        </div>
      </div>
    );
  }

  return (
    <div className="flex-1 overflow-y-auto">
      <div className="max-w-3xl mx-auto px-4 py-4 space-y-3">
        {messages.map((item, idx) => {
          switch (item.type) {
            case "user":
              return <UserMessage key={item.id} text={item.text} />;
            case "agent": {
              const isLast = idx === messages.length - 1;
              return (
                <AgentMessage
                  key={item.id}
                  text={item.text}
                  streaming={isLast && isProcessing && !item.complete}
                />
              );
            }
            case "thought":
              return <ThoughtBlock key={item.id} text={item.text} />;
            case "tool_call":
              return (
                <ToolCallCard
                  key={item.id}
                  item={item}
                  selected={selectedToolCallId === item.toolCallId}
                  onClick={() => onSelectToolCall(item.toolCallId)}
                />
              );
            case "plan":
              return <PlanDisplay key={item.id} entries={item.entries} />;
            default:
              return null;
          }
        })}
        <div ref={bottomRef} />
      </div>
    </div>
  );
}
