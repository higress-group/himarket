import { useState } from "react";
import { SendOutlined } from "@ant-design/icons";
import SendButton from "../send-button";
import { Mcp } from "../icon";
import type { IProductDetail } from "../../lib/apis";

interface InputBoxProps {
  onSendMessage: (content: string) => void;
  isLoading?: boolean;
  mcpEnabled?: boolean;
  onMcpClick?: () => void;
  addedMcps: IProductDetail[];
  isMcpExecuting?: boolean;
}

export function InputBox(props: InputBoxProps) {
  const {
    onSendMessage, isLoading = false, mcpEnabled = false,
    onMcpClick, addedMcps, isMcpExecuting = false
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
        className="w-full h-full pb-14 p-4 bg-white/80 backdrop-blur-sm rounded-3xl shadow-sm "
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
        <ToolButton onClick={onMcpClick} enabled={mcpEnabled}>
          <Mcp className={`w-4 h-4 hover:fill-colorPrimary ${mcpEnabled ? "fill-colorPrimary" : "fill-subTitle"}`} />
          <span className="text-sm text-subTitle">MCP {addedMcps.length ? `(${addedMcps.length})` : ""}</span>
        </ToolButton>
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
      className={`flex gap-2 items-center justify-center px-2 rounded-md cursor-pointer ${enabled ? "bg-colorPrimaryBgHover" : ""} hover:bg-colorPrimaryBgHover transition-all ease-linear duration-200`}
    >
      {children}
    </div>
  )
}
