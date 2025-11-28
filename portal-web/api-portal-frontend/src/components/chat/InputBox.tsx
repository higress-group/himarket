import { useState } from "react";
import { SendOutlined } from "@ant-design/icons";
import SendButton from "../send-button";
// import { Mcp } from "../icon";

interface InputBoxProps {
  onSendMessage: (content: string) => void;
  isLoading?: boolean;
}

export function InputBox({ onSendMessage, isLoading = false }: InputBoxProps) {
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
      {/* <div className="px-3 py-1 text-sm">MCP 工具执行中...</div> */}
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
        {/* <Mcp className="fill-colorPrimary w-4 h-4" /> */}
        <div></div>
        <SendButton
          className={`w-9 h-9 ${input.trim() && !isLoading
            ? "bg-colorPrimary text-white hover:opacity-90"
            : "bg-colorPrimarySecondary text-colorPrimary cursor-not-allowed"}`}
          isLoading={isLoading}
        >
          <SendOutlined
            className={"text-sm text-white"}
          />
        </SendButton>
      </div>
    </div>
  );
}
