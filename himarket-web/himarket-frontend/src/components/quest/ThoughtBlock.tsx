import { useState, useEffect, useRef, useMemo } from "react";
import { ChevronDown, ChevronRight, Brain } from "lucide-react";

interface ThoughtBlockProps {
  text: string;
  streaming?: boolean;
  variant?: "standalone" | "inline";
}

const PREVIEW_MAX_LINES = 3;

export function ThoughtBlock({
  text,
  streaming,
  variant = "standalone",
}: ThoughtBlockProps) {
  const isInline = variant === "inline";

  const { preview, hasMore } = useMemo(() => {
    const lines = text.split("\n").filter(l => l.trim() !== "");
    if (lines.length <= PREVIEW_MAX_LINES) {
      return { preview: lines.join("\n"), hasMore: false };
    }
    return {
      preview: lines.slice(0, PREVIEW_MAX_LINES).join("\n"),
      hasMore: true,
    };
  }, [text]);

  // Auto-expand while streaming, auto-collapse when done
  const [expanded, setExpanded] = useState(!!streaming);
  const [userToggled, setUserToggled] = useState(false);
  const prevStreamingRef = useRef(streaming);

  useEffect(() => {
    if (streaming && !prevStreamingRef.current && !userToggled) {
      setExpanded(true);
    }
    if (!streaming && prevStreamingRef.current && !userToggled) {
      setExpanded(false);
    }
    prevStreamingRef.current = streaming;
  }, [streaming, userToggled]);

  const handleToggle = () => {
    setExpanded(prev => !prev);
    setUserToggled(true);
  };

  const contentRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (streaming && expanded && contentRef.current) {
      contentRef.current.scrollTop = contentRef.current.scrollHeight;
    }
  }, [text, streaming, expanded]);

  if (isInline) {
    return (
      <div className="border-l-2 border-purple-300 pl-3 py-1">
        {/* Header */}
        <button
          className="flex items-center gap-1.5 w-full text-xs hover:opacity-80 transition-opacity"
          onClick={handleToggle}
        >
          <Brain
            size={12}
            className={
              streaming ? "text-purple-500 animate-pulse" : "text-purple-400"
            }
          />
          <span
            className={
              streaming ? "text-purple-600 font-medium" : "text-purple-500/80"
            }
          >
            {streaming ? "思考中..." : "思考"}
          </span>
          {streaming && (
            <span className="flex gap-0.5 ml-0.5">
              <span className="w-1 h-1 rounded-full bg-purple-400 animate-bounce [animation-delay:0ms]" />
              <span className="w-1 h-1 rounded-full bg-purple-400 animate-bounce [animation-delay:150ms]" />
              <span className="w-1 h-1 rounded-full bg-purple-400 animate-bounce [animation-delay:300ms]" />
            </span>
          )}
          <span className="flex-1" />
          {expanded ? (
            <ChevronDown size={12} className="text-purple-300" />
          ) : (
            <ChevronRight size={12} className="text-purple-300" />
          )}
        </button>

        {/* Expanded content */}
        {expanded && text && (
          <div
            ref={contentRef}
            className="mt-1 text-xs text-gray-500 whitespace-pre-wrap leading-relaxed max-h-48 overflow-y-auto"
          >
            {text}
            {streaming && (
              <span className="inline-block w-1.5 h-3 bg-purple-400 animate-blink align-text-bottom ml-0.5" />
            )}
          </div>
        )}

        {/* Collapsed preview */}
        {!expanded && preview && (
          <div className="mt-0.5 text-xs text-gray-400 whitespace-pre-wrap leading-relaxed line-clamp-2">
            {preview}
            {hasMore && <span> ...</span>}
          </div>
        )}
      </div>
    );
  }

  // Standalone variant
  return (
    <div
      className={`border-l-[3px] pl-3 transition-colors duration-300 ${
        streaming ? "border-purple-400" : "border-purple-300"
      }`}
    >
      {/* Header */}
      <button
        className="flex items-center gap-1.5 w-full py-1.5 text-xs hover:opacity-80 transition-opacity"
        onClick={handleToggle}
      >
        <Brain
          size={13}
          className={
            streaming ? "text-purple-500 animate-pulse" : "text-gray-400"
          }
        />
        <span
          className={
            streaming ? "text-purple-600 font-medium" : "text-gray-500"
          }
        >
          {streaming ? "思考中..." : "思考"}
        </span>
        {streaming && (
          <span className="flex gap-0.5 ml-1">
            <span className="w-1 h-1 rounded-full bg-purple-400 animate-bounce [animation-delay:0ms]" />
            <span className="w-1 h-1 rounded-full bg-purple-400 animate-bounce [animation-delay:150ms]" />
            <span className="w-1 h-1 rounded-full bg-purple-400 animate-bounce [animation-delay:300ms]" />
          </span>
        )}
        <span className="flex-1" />
        {expanded ? (
          <ChevronDown size={13} className="text-gray-400" />
        ) : (
          <ChevronRight size={13} className="text-gray-400" />
        )}
      </button>

      {/* Content area */}
      {expanded && text && (
        <div
          ref={contentRef}
          className="mt-1 text-xs text-gray-500 whitespace-pre-wrap leading-relaxed max-h-60 overflow-y-auto"
        >
          {text}
          {streaming && (
            <span className="inline-block w-1.5 h-3 bg-purple-400 animate-blink align-text-bottom ml-0.5" />
          )}
        </div>
      )}

      {/* Collapsed preview */}
      {!expanded && preview && (
        <div className="mt-0.5 text-xs text-gray-400 whitespace-pre-wrap leading-relaxed line-clamp-2">
          {preview}
          {hasMore && <span> ...</span>}
        </div>
      )}
    </div>
  );
}
