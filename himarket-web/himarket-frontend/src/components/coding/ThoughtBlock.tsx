import { useState, useEffect, useRef, useMemo, useCallback } from "react";
import { ChevronDown, ChevronRight, Brain } from "lucide-react";

interface ThoughtBlockProps {
  text: string;
  streaming?: boolean;
  variant?: "standalone" | "inline";
}

const PREVIEW_MAX_LINES = 3;

function formatDuration(ms: number): string {
  if (ms < 1000) return `${Math.round(ms)}ms`;
  const seconds = ms / 1000;
  if (seconds < 60) return `${seconds.toFixed(1)}s`;
  const mins = Math.floor(seconds / 60);
  const secs = Math.round(seconds % 60);
  return `${mins}m ${secs}s`;
}

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

  // Duration tracking
  const startTimeRef = useRef<number | null>(null);
  const [finalDuration, setFinalDuration] = useState<number | null>(null);
  const [liveDuration, setLiveDuration] = useState<number>(0);
  const timerRef = useRef<ReturnType<typeof setInterval>>();

  // Auto-expand while streaming, auto-collapse when done
  const [expanded, setExpanded] = useState(!!streaming);
  const [userToggled, setUserToggled] = useState(false);
  const prevStreamingRef = useRef(streaming);

  useEffect(() => {
    if (streaming && !prevStreamingRef.current) {
      // Streaming started
      startTimeRef.current = Date.now();
      setFinalDuration(null);
      timerRef.current = setInterval(() => {
        if (startTimeRef.current) {
          setLiveDuration(Date.now() - startTimeRef.current);
        }
      }, 100);
      if (!userToggled) setExpanded(true);
    }
    if (!streaming && prevStreamingRef.current) {
      // Streaming ended
      if (startTimeRef.current) {
        setFinalDuration(Date.now() - startTimeRef.current);
      }
      clearInterval(timerRef.current);
      if (!userToggled) setExpanded(false);
    }
    prevStreamingRef.current = streaming;
  }, [streaming, userToggled]);

  useEffect(() => {
    return () => clearInterval(timerRef.current);
  }, []);

  const durationText = useMemo(() => {
    if (finalDuration != null) return formatDuration(finalDuration);
    if (streaming && liveDuration > 0) return formatDuration(liveDuration);
    return null;
  }, [finalDuration, streaming, liveDuration]);

  const handleToggle = useCallback(() => {
    setExpanded(prev => !prev);
    setUserToggled(true);
  }, []);

  const contentRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (streaming && expanded && contentRef.current) {
      contentRef.current.scrollTop = contentRef.current.scrollHeight;
    }
  }, [text, streaming, expanded]);

  if (isInline) {
    return (
      <div className="py-0.5">
        {/* Header */}
        <button
          className="flex items-center gap-1.5 w-full text-xs hover:bg-gray-50 rounded-md px-1.5 py-1 transition-colors"
          onClick={handleToggle}
        >
          <Brain
            size={12}
            className={
              streaming ? "text-purple-500 animate-pulse" : "text-gray-300"
            }
          />
          <span
            className={
              streaming ? "text-purple-600 font-medium" : "text-gray-400"
            }
          >
            {streaming ? "思考中..." : "深度思考"}
          </span>
          {streaming && (
            <span className="flex gap-0.5 ml-0.5">
              <span className="w-1 h-1 rounded-full bg-purple-400 animate-bounce [animation-delay:0ms]" />
              <span className="w-1 h-1 rounded-full bg-purple-400 animate-bounce [animation-delay:150ms]" />
              <span className="w-1 h-1 rounded-full bg-purple-400 animate-bounce [animation-delay:300ms]" />
            </span>
          )}
          {durationText && (
            <span className="text-[11px] text-gray-300 tabular-nums ml-0.5">{durationText}</span>
          )}
          <span className="flex-1" />
          {expanded ? (
            <ChevronDown size={12} className="text-gray-300" />
          ) : (
            <ChevronRight size={12} className="text-gray-300" />
          )}
        </button>

        {/* Expanded content */}
        {expanded && text && (
          <div
            ref={contentRef}
            className="mt-1 px-1.5 text-[13px] text-gray-400 whitespace-pre-wrap leading-relaxed max-h-48 overflow-y-auto"
          >
            {text}
            {streaming && (
              <span className="inline-block w-1.5 h-3 bg-purple-400 animate-blink align-text-bottom ml-0.5" />
            )}
          </div>
        )}

        {/* Collapsed preview */}
        {!expanded && preview && (
          <div className="mt-0.5 px-1.5 text-[13px] text-gray-300 whitespace-pre-wrap leading-relaxed line-clamp-2">
            {preview}
            {hasMore && <span> ...</span>}
          </div>
        )}
      </div>
    );
  }

  // Standalone variant
  return (
    <div className="py-0.5">
      {/* Header */}
      <button
        className="flex items-center gap-1.5 w-full py-1.5 px-1.5 text-xs hover:bg-gray-50 rounded-md transition-colors"
        onClick={handleToggle}
      >
        <Brain
          size={13}
          className={
            streaming ? "text-purple-500 animate-pulse" : "text-gray-300"
          }
        />
        <span
          className={
            streaming ? "text-purple-600 font-medium" : "text-gray-400"
          }
        >
          {streaming ? "思考中..." : "深度思考"}
        </span>
        {streaming && (
          <span className="flex gap-0.5 ml-1">
            <span className="w-1 h-1 rounded-full bg-purple-400 animate-bounce [animation-delay:0ms]" />
            <span className="w-1 h-1 rounded-full bg-purple-400 animate-bounce [animation-delay:150ms]" />
            <span className="w-1 h-1 rounded-full bg-purple-400 animate-bounce [animation-delay:300ms]" />
          </span>
        )}
        {durationText && (
          <span className="text-[11px] text-gray-300 tabular-nums ml-1">{durationText}</span>
        )}
        <span className="flex-1" />
        {expanded ? (
          <ChevronDown size={13} className="text-gray-300" />
        ) : (
          <ChevronRight size={13} className="text-gray-300" />
        )}
      </button>

      {/* Content area */}
      {expanded && text && (
        <div
          ref={contentRef}
          className="mt-1 px-1.5 text-[13px] text-gray-400 whitespace-pre-wrap leading-relaxed max-h-60 overflow-y-auto"
        >
          {text}
          {streaming && (
            <span className="inline-block w-1.5 h-3 bg-purple-400 animate-blink align-text-bottom ml-0.5" />
          )}
        </div>
      )}

      {/* Collapsed preview */}
      {!expanded && preview && (
        <div className="mt-0.5 px-1.5 text-[13px] text-gray-300 whitespace-pre-wrap leading-relaxed line-clamp-2">
          {preview}
          {hasMore && <span> ...</span>}
        </div>
      )}
    </div>
  );
}
