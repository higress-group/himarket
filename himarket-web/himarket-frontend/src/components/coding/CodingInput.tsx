import { useState, useRef, useCallback, type KeyboardEvent } from "react";
import { Send, Square } from "lucide-react";
import { useCodingState } from "../../context/CodingSessionContext";
import { SlashMenu } from "./SlashMenu";

interface CodingInputProps {
  onSend: (text: string) => void;
  onCancel: () => void;
  isProcessing: boolean;
  disabled: boolean;
}

export function CodingInput({
  onSend,
  onCancel,
  isProcessing,
  disabled,
}: CodingInputProps) {
  const [text, setText] = useState("");
  const [showSlash, setShowSlash] = useState(false);
  const inputRef = useRef<HTMLTextAreaElement>(null);
  const state = useCodingState();

  const handleSend = useCallback(() => {
    const trimmed = text.trim();
    if (!trimmed) return;
    onSend(trimmed);
    setText("");
    setShowSlash(false);
  }, [text, onSend]);

  const handleKeyDown = (e: KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === "Enter" && !e.shiftKey) {
      e.preventDefault();
      handleSend();
    }
  };

  const handleChange = (value: string) => {
    setText(value);
    setShowSlash(
      value === "/" || (value.startsWith("/") && !value.includes(" "))
    );
  };

  const handleCommandSelect = (name: string) => {
    setText("/" + name + " ");
    setShowSlash(false);
    inputRef.current?.focus();
  };

  return (
    <div className="relative px-4 py-3 border-t border-gray-200/60 bg-white/30 backdrop-blur-sm">
      {isProcessing && (
        <div className="absolute top-0 left-0 right-0 h-0.5 bg-blue-500/30 overflow-hidden">
          <div className="h-full w-1/3 bg-blue-500 animate-[slide_1.5s_ease-in-out_infinite]" />
        </div>
      )}
      {showSlash && state.commands.length > 0 && (
        <SlashMenu
          commands={state.commands}
          filter={text.slice(1)}
          onSelect={handleCommandSelect}
        />
      )}
      <div className="flex items-end gap-2">
        <textarea
          ref={inputRef}
          className="flex-1 resize-none rounded-xl border border-gray-200/80 bg-white/80 px-4 py-2.5
                     text-sm text-gray-700 placeholder-gray-400
                     outline-none focus:border-gray-300 focus:shadow-sm transition-all
                     min-h-[40px] max-h-[160px]"
          value={text}
          onChange={e => handleChange(e.target.value)}
          onKeyDown={handleKeyDown}
          placeholder={
            disabled
              ? "正在连接..."
              : "输入消息… (Enter 发送, Shift+Enter 换行)"
          }
          disabled={disabled}
          rows={1}
        />
        {isProcessing ? (
          <button
            className="flex items-center gap-1.5 px-4 py-2.5 rounded-xl text-sm font-medium
                       bg-red-50 text-red-600 border border-red-200
                       hover:bg-red-100 transition-colors"
            onClick={onCancel}
          >
            <Square size={14} />
            停止
          </button>
        ) : (
          <button
            className="flex items-center gap-1.5 px-4 py-2.5 rounded-xl text-sm font-medium
                       bg-gray-800 text-white
                       hover:bg-gray-700 transition-colors
                       disabled:opacity-40 disabled:cursor-not-allowed"
            onClick={handleSend}
            disabled={disabled || !text.trim()}
          >
            <Send size={14} />
            发送
          </button>
        )}
      </div>
    </div>
  );
}
