import { useState, useEffect, useRef } from "react";
import { Loader2, CheckCircle2, AlertCircle, ChevronDown, ChevronRight } from "lucide-react";
import {
  useCodingState,
  useActiveCodingSession,
} from "../../context/CodingSessionContext";

interface SandboxStatusCardProps {
  onRetry?: () => void;
}

const COLLAPSE_DELAY = 3000;

export function SandboxStatusCard({ onRetry }: SandboxStatusCardProps) {
  const state = useCodingState();
  const activeSession = useActiveCodingSession();
  const { sandboxStatus } = state;

  const prevStatusRef = useRef(sandboxStatus?.status);
  const mountedWithReady = useRef(
    sandboxStatus?.status === "ready" &&
      (activeSession?.messages.length ?? 0) > 0
  );

  const [collapsed, setCollapsed] = useState(mountedWithReady.current);

  // Auto-collapse 3s after sandbox becomes ready
  useEffect(() => {
    const prev = prevStatusRef.current;
    const curr = sandboxStatus?.status;
    prevStatusRef.current = curr;

    if (curr === "ready" && prev !== "ready" && !mountedWithReady.current) {
      const timer = setTimeout(() => setCollapsed(true), COLLAPSE_DELAY);
      return () => clearTimeout(timer);
    }

    // If status regresses (e.g. reconnect resets to creating), un-collapse
    if (curr !== "ready" && collapsed) {
      setCollapsed(false);
    }
  }, [sandboxStatus?.status, collapsed]);

  // Nothing to show if sandbox hasn't started
  if (!sandboxStatus) return null;

  const { status, message } = sandboxStatus;

  // ── Collapsed ready state ──
  if (status === "ready" && collapsed) {
    return (
      <button
        className="flex items-center gap-1 text-xs text-gray-400 hover:text-gray-500 transition-colors py-0.5"
        onClick={() => setCollapsed(false)}
      >
        <CheckCircle2 size={12} className="text-green-400" />
        <span>沙箱已就绪</span>
        <ChevronRight size={12} />
      </button>
    );
  }

  // ── Error state ──
  if (status === "error") {
    return (
      <div className="flex items-start gap-3 px-3.5 py-3 rounded-lg border border-red-200 bg-red-50/80">
        <AlertCircle size={18} className="text-red-500 flex-shrink-0 mt-0.5" />
        <div className="flex-1 min-w-0">
          <div className="text-sm font-medium text-red-700">
            沙箱环境初始化失败
          </div>
          {message && (
            <div className="text-xs text-red-600/80 mt-0.5 truncate">
              {message}
            </div>
          )}
        </div>
        {onRetry && (
          <button
            onClick={onRetry}
            className="flex-shrink-0 px-3 py-1 text-xs font-medium text-white bg-red-500 hover:bg-red-600 rounded-md transition-colors"
          >
            重新连接
          </button>
        )}
      </div>
    );
  }

  // ── Ready expanded state ──
  if (status === "ready") {
    return (
      <button
        className="flex items-center gap-2 w-full px-3.5 py-2.5 rounded-lg border border-green-200 bg-green-50/60 transition-colors hover:bg-green-50"
        onClick={() => setCollapsed(true)}
      >
        <CheckCircle2 size={16} className="text-green-500 flex-shrink-0" />
        <span className="text-sm text-gray-600">沙箱环境已就绪</span>
        <div className="flex-1" />
        <ChevronDown size={14} className="text-gray-400" />
      </button>
    );
  }

  // ── Creating / initializing state ──
  return (
    <div className="flex items-center gap-2 px-3.5 py-2.5 rounded-lg border border-blue-200 bg-blue-50/60">
      <Loader2 size={16} className="animate-spin text-blue-500 flex-shrink-0" />
      <span className="text-sm text-gray-600">正在准备沙箱环境...</span>
    </div>
  );
}
