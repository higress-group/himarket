import { useRef, useEffect, useState, useCallback, forwardRef, useImperativeHandle } from "react";
import { Terminal } from "@xterm/xterm";
import { FitAddon } from "@xterm/addon-fit";
import { ChevronDown, ChevronUp, Terminal as TerminalIcon } from "lucide-react";
import {
  useTerminalWebSocket,
  type TerminalWsStatus,
} from "../../hooks/useTerminalWebSocket";
import "@xterm/xterm/css/xterm.css";

function buildTerminalWsUrl(runtime?: string): string {
  const protocol =
    window.location.protocol === "https:" ? "wss:" : "ws:";
  const base = `${protocol}//${window.location.host}/ws/terminal`;
  const params = new URLSearchParams();
  const token = localStorage.getItem("access_token");
  if (token) params.set("token", token);
  if (runtime) params.set("runtime", runtime);
  const qs = params.toString();
  return qs ? `${base}?${qs}` : base;
}

interface TerminalPanelProps {
  height: number;
  collapsed: boolean;
  onToggleCollapse: () => void;
  runtime?: string;
}

export interface TerminalPanelHandle {
  reconnect: () => void;
}

export const TerminalPanel = forwardRef<TerminalPanelHandle, TerminalPanelProps>(function TerminalPanel({
  height,
  collapsed,
  onToggleCollapse,
  runtime,
}, ref) {
  const containerRef = useRef<HTMLDivElement>(null);
  const terminalRef = useRef<Terminal | null>(null);
  const fitAddonRef = useRef<FitAddon | null>(null);
  const resizeTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const [wsUrl, setWsUrl] = useState(() => buildTerminalWsUrl(runtime));

  // Reconnect when runtime changes
  useEffect(() => {
    setWsUrl(buildTerminalWsUrl(runtime));
  }, [runtime]);

  // Terminal output handler (stable ref to avoid re-creating WebSocket)
  const onOutputRef = useRef<(data: Uint8Array) => void>(() => {});
  const onExitRef = useRef<(code: number) => void>(() => {});

  const { status, sendInput, sendResize, reconnect: terminalReconnect } = useTerminalWebSocket({
    url: wsUrl,
    onOutput: (data: Uint8Array) => onOutputRef.current(data),
    onExit: (code: number) => onExitRef.current(code),
  });

  useImperativeHandle(ref, () => ({
    reconnect: terminalReconnect,
  }), [terminalReconnect]);

  // Initialize xterm
  useEffect(() => {
    if (!containerRef.current) return;

    const terminal = new Terminal({
      fontSize: 13,
      fontFamily:
        "'JetBrains Mono', 'Fira Code', 'Cascadia Code', Menlo, monospace",
      theme: {
        background: "#1e1e1e",
        foreground: "#d4d4d4",
        cursor: "#d4d4d4",
        selectionBackground: "#264f78",
      },
      cursorBlink: true,
      disableStdin: false,
      convertEol: false,
      scrollback: 5000,
    });

    const fitAddon = new FitAddon();
    terminal.loadAddon(fitAddon);
    terminal.open(containerRef.current);

    // Delay fit to allow layout to settle
    requestAnimationFrame(() => {
      try {
        fitAddon.fit();
      } catch {
        // container might not be visible yet
      }
    });

    // Wire user input → WebSocket
    terminal.onData(data => {
      sendInput(data);
    });

    // Wire WebSocket output → xterm (raw bytes for proper UTF-8 handling)
    onOutputRef.current = (data: Uint8Array) => {
      terminal.write(data);
    };

    // Wire exit event
    onExitRef.current = (_code: number) => {
      terminal.write("\r\n\x1b[90m[Shell exited]\x1b[0m\r\n");
    };

    terminalRef.current = terminal;
    fitAddonRef.current = fitAddon;

    return () => {
      terminal.dispose();
      terminalRef.current = null;
      fitAddonRef.current = null;
      onOutputRef.current = () => {};
      onExitRef.current = () => {};
    };
    // sendInput is stable (useCallback with no deps)
  }, [sendInput]);

  // Send resize after fit (debounced)
  const doFitAndResize = useCallback(() => {
    const fitAddon = fitAddonRef.current;
    const terminal = terminalRef.current;
    if (!fitAddon || !terminal) return;

    try {
      fitAddon.fit();
    } catch {
      return;
    }

    // Debounce resize messages to backend
    if (resizeTimerRef.current) {
      clearTimeout(resizeTimerRef.current);
    }
    resizeTimerRef.current = setTimeout(() => {
      sendResize(terminal.cols, terminal.rows);
    }, 150);
  }, [sendResize]);

  // Resize on collapse toggle or height change
  useEffect(() => {
    if (!collapsed) {
      requestAnimationFrame(() => doFitAndResize());
    }
  }, [collapsed, height, doFitAndResize]);

  // Resize on window resize
  useEffect(() => {
    const handleResize = () => {
      if (!collapsed) doFitAndResize();
    };
    window.addEventListener("resize", handleResize);
    return () => window.removeEventListener("resize", handleResize);
  }, [collapsed, doFitAndResize]);

  // Initial resize when connected
  useEffect(() => {
    if (status === "connected") {
      requestAnimationFrame(() => doFitAndResize());
    }
  }, [status, doFitAndResize]);

  const toggleCollapse = useCallback(() => {
    onToggleCollapse();
  }, [onToggleCollapse]);

  return (
    <div
      className="flex flex-col border-t border-gray-200/60 bg-[#1e1e1e] flex-shrink-0 overflow-hidden"
      style={{ height: collapsed ? 32 : height }}
    >
      {/* Header */}
      <button
        className="flex items-center gap-1.5 px-3 py-1.5 text-xs text-gray-300 bg-[#252526]
          hover:bg-[#2d2d2d] transition-colors border-b border-gray-700/50 flex-shrink-0"
        onClick={toggleCollapse}
      >
        <TerminalIcon size={12} />
        <span>终端</span>
        <StatusDot status={status} />
        <div className="flex-1" />
        {collapsed ? <ChevronUp size={12} /> : <ChevronDown size={12} />}
      </button>

      {/* Terminal container */}
      <div
        ref={containerRef}
        className="flex-1 min-h-0 overflow-hidden"
        style={{ display: collapsed ? "none" : "block" }}
      />
    </div>
  );
});

function StatusDot({ status }: { status: TerminalWsStatus }) {
  const color =
    status === "connected"
      ? "bg-green-400"
      : status === "connecting" || status === "reconnecting"
        ? "bg-yellow-400"
        : "bg-red-400";
  return <span className={`w-1.5 h-1.5 rounded-full ${color} ml-1.5`} />;
}
