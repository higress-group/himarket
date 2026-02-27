import { useCallback, useEffect, useRef, useState } from "react";

export type WsStatus = "disconnected" | "connecting" | "connected";

interface UseWebSocketOptions {
  url: string;
  onMessage: (data: string) => void;
  autoConnect?: boolean;
  maxReconnectAttempts?: number;
}

export function useAcpWebSocket({
  url,
  onMessage,
  autoConnect = true,
  maxReconnectAttempts = 2,
}: UseWebSocketOptions) {
  const [status, setStatus] = useState<WsStatus>("disconnected");
  const wsRef = useRef<WebSocket | null>(null);
  const reconnectAttemptRef = useRef(0);
  const reconnectTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const onMessageRef = useRef(onMessage);
  onMessageRef.current = onMessage;

  const cleanup = useCallback(() => {
    if (reconnectTimerRef.current) {
      clearTimeout(reconnectTimerRef.current!);
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
    console.log("[AcpWebSocket] Connecting to:", url);
    const ws = new WebSocket(url);
    wsRef.current = ws;

    ws.onopen = () => {
      if (wsRef.current !== ws) return;
      console.log("[AcpWebSocket] Connected successfully");
      setStatus("connected");
      reconnectAttemptRef.current = 0;
    };

    ws.onmessage = e => {
      onMessageRef.current(e.data);
    };

    ws.onerror = (e) => {
      console.error("[AcpWebSocket] Error:", e);
    };

    ws.onclose = (e) => {
      console.log("[AcpWebSocket] Closed:", e.code, e.reason);
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

  const send = useCallback((data: string) => {
    if (wsRef.current?.readyState === WebSocket.OPEN) {
      wsRef.current.send(data);
    }
  }, []);

  useEffect(() => {
    if (autoConnect) {
      reconnectAttemptRef.current = 0;
      connect();
    }
    return () => {
      cleanup();
      if (wsRef.current) {
        // 把 onclose 置空，避免触发旧的重连逻辑
        wsRef.current.onclose = null;
        wsRef.current.onerror = null;
        wsRef.current.onopen = null;
        wsRef.current.onmessage = null;
        wsRef.current.close();
        wsRef.current = null;
      }
      // 确保 status 回到 disconnected，让 useAcpSession 能正确重置
      setStatus("disconnected");
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [url]);

  return { status, connect, disconnect, send };
}
