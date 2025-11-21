import { useState } from "react";
import { SendOutlined } from "@ant-design/icons";

interface InputBoxProps {
  onSendMessage: (content: string) => void;
}

export function InputBox({ onSendMessage }: InputBoxProps) {
  const [input, setInput] = useState("");

  const handleSend = () => {
    if (input.trim()) {
      onSendMessage(input.trim());
      setInput("");
    }
  };

  const handleKeyDown = (e: React.KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === "Enter" && !e.shiftKey) {
      e.preventDefault();
      handleSend();
    }
  };

  return (
    <div className="relative">
      <textarea
        value={input}
        onChange={(e) => setInput(e.target.value)}
        onKeyDown={handleKeyDown}
        placeholder="输入您的问题..."
        className="w-full px-4 py-3 pr-12 bg-white/80 backdrop-blur-sm rounded-3xl resize-none focus:outline-none focus:ring-2 focus:ring-colorPrimary/50 focus:border-transparent shadow-sm border-4 border-colorPrimary/40"
        rows={3}
      />
      <button
        onClick={handleSend}
        disabled={!input.trim()}
        className={`
          absolute right-5 bottom-5 w-8 h-8 rounded-lg flex items-center justify-center
          transition-all duration-200
          ${
            input.trim()
              ? "bg-primary-500 text-white hover:bg-primary-600"
              : "bg-gray-200 text-gray-400 cursor-not-allowed"
          }
        `}
      >
        <SendOutlined className="text-sm" />
      </button>
    </div>
  );
}
