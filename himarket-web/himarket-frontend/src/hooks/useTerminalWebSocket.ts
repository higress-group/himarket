import { useCallback, useEffect, useRef, useState } from "react";

export type TerminalWsStatus = "disconnected" | "connecting" | "connected";

interface UseTerminalWebSocketOptions {
  url: string;
  onOutput: (data: Uint8Array) => void;
  onExit?: (code: number) => void;
  autoConnect?: boolean;
  maxReconnectAttempts?: number;
}

export function useTerminalWebSocket({
  url,
  onOutput,
  onExit,
  autoConnect = true,
  maxReconnectAttempts = 10,
}: UseTerminalWebSocketOptions) {
  const [status, setStatus] = useState<TerminalWsStatus>("disconnected");
  const wsRef = useRef<WebSocket | null>(null);
  const reconnectAttemptRef = useRef(0);
  const reconnectTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const onOutputRef = useRef(onOutput);
  const onExitRef = useRef(onExit);
  onOutputRef.current = onOutput;
  onExitRef.current = onExit;

  const cleanup = useCallback(() => {
    if (reconnectTimerRef.current) {
      clearTimeout(reconnectTimerRef.current);
      reconnectTimerRef.current = null;
    }
  }, []);

  const connect = useCallback(() => {
    cleanup();
    if (wsRef.current) {
      wsRef.current.close();
      wsRef.current = null;
    }

    setStatus("connecting");
    const ws = new WebSocket(url);
    wsRef.current = ws;

    ws.onopen = () => {
      if (wsRef.current !== ws) return;
      setStatus("connected");
      reconnectAttemptRef.current = 0;
    };

    ws.onmessage = e => {
      try {
        const msg = JSON.parse(e.data);
        if (msg.type === "output" && msg.data) {
          // Decode base64 to raw bytes (Uint8Array) so xterm.js handles
          // UTF-8 properly, including partial multi-byte sequences.
          const binaryStr = atob(msg.data);
          const bytes = new Uint8Array(binaryStr.length);
          for (let i = 0; i < binaryStr.length; i++) {
            bytes[i] = binaryStr.charCodeAt(i);
          }
          onOutputRef.current(bytes);
        } else if (msg.type === "exit") {
          onExitRef.current?.(msg.code ?? 0);
        }
      } catch {
        // Not valid JSON, ignore
      }
    };

    ws.onerror = () => {
      // onclose will fire after this
    };

    ws.onclose = () => {
      if (wsRef.current !== ws) return;
      wsRef.current = null;
      setStatus("disconnected");

      if (reconnectAttemptRef.current < maxReconnectAttempts) {
        const delay = Math.min(
          1000 * Math.pow(2, reconnectAttemptRef.current),
          30000
        );
        reconnectAttemptRef.current++;
        reconnectTimerRef.current = setTimeout(() => {
          connect();
        }, delay);
      }
    };
  }, [url, maxReconnectAttempts, cleanup]);

  const disconnect = useCallback(() => {
    cleanup();
    reconnectAttemptRef.current = maxReconnectAttempts;
    if (wsRef.current) {
      wsRef.current.close();
      wsRef.current = null;
    }
    setStatus("disconnected");
  }, [cleanup, maxReconnectAttempts]);

  const sendInput = useCallback((data: string) => {
    if (wsRef.current?.readyState === WebSocket.OPEN) {
      wsRef.current.send(JSON.stringify({ type: "input", data }));
    }
  }, []);

  const sendResize = useCallback((cols: number, rows: number) => {
    if (wsRef.current?.readyState === WebSocket.OPEN) {
      wsRef.current.send(JSON.stringify({ type: "resize", cols, rows }));
    }
  }, []);

  useEffect(() => {
    if (autoConnect) {
      connect();
    }
    return () => {
      cleanup();
      if (wsRef.current) {
        wsRef.current.close();
        wsRef.current = null;
      }
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [url]);

  return { status, connect, disconnect, sendInput, sendResize };
}
