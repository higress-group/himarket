import { useCallback, useEffect, useRef, useState } from "react";
import { calcReconnectDelay } from "./useCodingWebSocket";

export type TerminalWsStatus =
  | "disconnected"
  | "connecting"
  | "connected"
  | "reconnecting";

interface UseTerminalWebSocketOptions {
  url: string;
  onOutput: (data: Uint8Array) => void;
  onExit?: (code: number) => void;
  autoConnect?: boolean;
}

interface UseTerminalWebSocketReturn {
  status: TerminalWsStatus;
  connect: () => void;
  disconnect: () => void;
  sendInput: (data: string) => void;
  sendResize: (cols: number, rows: number) => void;
  reconnect: () => void;
}

export function useTerminalWebSocket({
  url,
  onOutput,
  onExit,
  autoConnect = true,
}: UseTerminalWebSocketOptions): UseTerminalWebSocketReturn {
  const [status, setStatus] = useState<TerminalWsStatus>("disconnected");

  const wsRef = useRef<WebSocket | null>(null);
  const reconnectAttemptRef = useRef(0);
  const reconnectTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const onOutputRef = useRef(onOutput);
  const onExitRef = useRef(onExit);
  const urlRef = useRef(url);
  // Track whether we had a successful connection (for reconnecting vs first connect)
  const wasConnectedRef = useRef(false);
  // Whether disconnect() was called intentionally — suppresses auto-reconnect
  const intentionalDisconnectRef = useRef(false);
  // Save last resize dimensions for auto-resend after reconnection
  const lastResizeRef = useRef<{ cols: number; rows: number } | null>(null);

  onOutputRef.current = onOutput;
  onExitRef.current = onExit;
  urlRef.current = url;

  // Stable ref for core connect logic to break circular deps
  const doConnectRef = useRef<() => void>(() => {});

  const clearReconnectTimer = useCallback(() => {
    if (reconnectTimerRef.current) {
      clearTimeout(reconnectTimerRef.current);
      reconnectTimerRef.current = null;
    }
  }, []);

  const scheduleReconnect = useCallback(() => {
    clearReconnectTimer();
    const attempt = reconnectAttemptRef.current;
    const delay = calcReconnectDelay(attempt);
    reconnectAttemptRef.current = attempt + 1;
    console.log(`[TerminalWebSocket] Scheduling reconnect #${attempt + 1} in ${delay}ms`);
    reconnectTimerRef.current = setTimeout(() => {
      reconnectTimerRef.current = null;
      doConnectRef.current();
    }, delay);
  }, [clearReconnectTimer]);

  // Core connect logic — always reads latest url from ref
  const doConnect = useCallback(() => {
    clearReconnectTimer();
    if (wsRef.current) {
      wsRef.current.onclose = null;
      wsRef.current.onerror = null;
      wsRef.current.onopen = null;
      wsRef.current.onmessage = null;
      wsRef.current.close();
      wsRef.current = null;
    }

    if (intentionalDisconnectRef.current) return;

    setStatus("connecting");
    console.log("[TerminalWebSocket] Connecting to:", urlRef.current);
    const ws = new WebSocket(urlRef.current);
    wsRef.current = ws;

    ws.onopen = () => {
      if (wsRef.current !== ws) return;
      console.log("[TerminalWebSocket] Connected successfully");
      setStatus("connected");
      wasConnectedRef.current = true;
      reconnectAttemptRef.current = 0;

      // Auto-resend last resize info after reconnection
      if (lastResizeRef.current && ws.readyState === WebSocket.OPEN) {
        const { cols, rows } = lastResizeRef.current;
        ws.send(JSON.stringify({ type: "resize", cols, rows }));
      }
    };

    ws.onmessage = (e) => {
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

    ws.onerror = (e) => {
      console.error("[TerminalWebSocket] Error:", e);
    };

    ws.onclose = () => {
      if (wsRef.current !== ws) return;
      wsRef.current = null;

      if (intentionalDisconnectRef.current) {
        setStatus("disconnected");
        return;
      }

      // Unexpected disconnect — enter reconnecting state and schedule retry
      setStatus("reconnecting");
      scheduleReconnect();
    };
  }, [clearReconnectTimer, scheduleReconnect]);

  // Keep the ref in sync so scheduleReconnect's setTimeout always calls latest doConnect
  doConnectRef.current = doConnect;

  const connect = useCallback(() => {
    intentionalDisconnectRef.current = false;
    reconnectAttemptRef.current = 0;
    wasConnectedRef.current = false;
    doConnectRef.current();
  }, []);

  const disconnect = useCallback(() => {
    intentionalDisconnectRef.current = true;
    clearReconnectTimer();
    reconnectAttemptRef.current = 0;
    wasConnectedRef.current = false;
    if (wsRef.current) {
      wsRef.current.onclose = null;
      wsRef.current.onerror = null;
      wsRef.current.onopen = null;
      wsRef.current.onmessage = null;
      wsRef.current.close();
      wsRef.current = null;
    }
    setStatus("disconnected");
  }, [clearReconnectTimer]);

  const sendInput = useCallback((data: string) => {
    if (wsRef.current?.readyState === WebSocket.OPEN) {
      wsRef.current.send(JSON.stringify({ type: "input", data }));
    }
  }, []);

  const sendResize = useCallback((cols: number, rows: number) => {
    // Save last resize dimensions for auto-resend after reconnection
    lastResizeRef.current = { cols, rows };
    if (wsRef.current?.readyState === WebSocket.OPEN) {
      wsRef.current.send(JSON.stringify({ type: "resize", cols, rows }));
    }
  }, []);

  /** External reconnect — resets attempt counter and immediately reconnects */
  const reconnect = useCallback(() => {
    intentionalDisconnectRef.current = false;
    reconnectAttemptRef.current = 0;
    wasConnectedRef.current = false;
    doConnectRef.current();
  }, []);

  useEffect(() => {
    if (autoConnect) {
      intentionalDisconnectRef.current = false;
      reconnectAttemptRef.current = 0;
      wasConnectedRef.current = false;
      doConnectRef.current();
    }
    return () => {
      // Component unmount: stop reconnection and clean up all resources
      intentionalDisconnectRef.current = true;
      clearReconnectTimer();
      if (wsRef.current) {
        wsRef.current.onclose = null;
        wsRef.current.onerror = null;
        wsRef.current.onopen = null;
        wsRef.current.onmessage = null;
        wsRef.current.close();
        wsRef.current = null;
      }
      setStatus("disconnected");
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [url]);

  return { status, connect, disconnect, sendInput, sendResize, reconnect };
}
