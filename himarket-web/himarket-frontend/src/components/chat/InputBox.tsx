import { useState } from "react";
import { SendOutlined } from "@ant-design/icons";
import SendButton from "../send-button";
import { Global, Mcp } from "../icon";
import type { IProductDetail } from "../../lib/apis";

interface InputBoxProps {
  isLoading?: boolean;
  mcpEnabled?: boolean;
  addedMcps: IProductDetail[];
  isMcpExecuting?: boolean;
  showWebSearch: boolean;
  webSearchEnabled: boolean;
  onWebSearchEnable: (enabled: boolean) => void;
  onMcpClick?: () => void;
  onSendMessage: (content: string) => void;
}

export function InputBox(props: InputBoxProps) {
  const {
    onSendMessage, isLoading = false, mcpEnabled = false,
    onMcpClick, addedMcps, isMcpExecuting = false, showWebSearch,
    webSearchEnabled, onWebSearchEnable,
  } = props;
  const [input, setInput] = useState("");

  const handleSend = () => {
    if (input.trim() && !isLoading) {
      onSendMessage(input.trim());
      setInput("");
    }
  };

  const handleKeyDown = (e: React.KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === "Enter" && !e.shiftKey && !isLoading) {
      e.preventDefault();
      handleSend();
    }
  };

  return (
    <div className="relative p-1.5 rounded-3xl flex flex-col justify-center"
      style={{
        background: "linear-gradient(256deg, rgba(234, 228, 248, 1) 36%, rgba(215, 229, 243, 1) 100%)",
      }}
    >
      {isMcpExecuting && (
        <div className="px-3 py-1 text-sm">MCP 工具执行中...</div>
      )}
      <div
        className="w-full h-full pb-14 p-4 bg-white/80 backdrop-blur-sm rounded-3xl"
      >
        <textarea
          value={input}
          onChange={(e) => setInput(e.target.value)}
          onKeyDown={handleKeyDown}
          className="w-full resize-none focus:outline-none bg-transparent"
          placeholder="输入您的问题..."
          rows={2}
        />
      </div>
      <div
        className="absolute bottom-5 flex justify-between w-full px-6 left-0"
      >
        <div className="inline-flex gap-2">
          {
            showWebSearch && (
              <ToolButton onClick={() => onWebSearchEnable(!webSearchEnabled)} enabled={webSearchEnabled}>
                <Global className={`w-4 h-4 ${webSearchEnabled ? "fill-colorPrimary" : "fill-subTitle"}`} />
                <span className="text-sm text-subTitle">联网</span>
              </ToolButton>
            )
          }
          <ToolButton onClick={onMcpClick} enabled={mcpEnabled}>
            <Mcp className={`w-4 h-4 ${mcpEnabled ? "fill-colorPrimary" : "fill-subTitle"}`} />
            <span className="text-sm text-subTitle">MCP {addedMcps.length ? `(${addedMcps.length})` : ""}</span>
          </ToolButton>
        </div>
        <SendButton
          className={`w-9 h-9 ${input.trim() && !isLoading
            ? "bg-colorPrimary text-white hover:opacity-90"
            : "bg-colorPrimarySecondary text-colorPrimary cursor-not-allowed"}`}
          isLoading={isLoading}
          onClick={handleSend}
        >
          <SendOutlined
            className={"text-sm text-white"}
          />
        </SendButton>
      </div>
    </div>
  );
}

function ToolButton({ enabled, children, onClick }: { enabled: boolean; children: React.ReactNode, onClick?: () => void }) {
  return (
    <div
      onClick={onClick}
      className={`flex h-full gap-2 items-center justify-center px-2 rounded-lg cursor-pointer ${enabled ? "bg-colorPrimaryBgHover" : ""}  transition-all ease-linear duration-400`}
    >
      {children}
    </div>
  )
}
