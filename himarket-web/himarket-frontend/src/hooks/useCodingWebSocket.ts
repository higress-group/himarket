import { useCallback, useEffect, useRef, useState } from 'react';

export type WsStatus = 'disconnected' | 'connecting' | 'connected' | 'reconnecting';

export const RECONNECT_CONFIG = {
  backoffMultiplier: 2, // 指数退避倍数
  baseDelay: 1000, // 初始重连延迟 1 秒
  maxDelay: 30000, // 最大重连延迟 30 秒
} as const;

/**
 * 计算指数退避延迟：delay = min(baseDelay * 2^attempt, maxDelay)
 */
export function calcReconnectDelay(attempt: number): number {
  const { backoffMultiplier, baseDelay, maxDelay } = RECONNECT_CONFIG;
  return Math.min(baseDelay * Math.pow(backoffMultiplier, attempt), maxDelay);
}

interface UseWebSocketOptions {
  url: string;
  onMessage: (data: string) => void;
  /** Called once right after WebSocket connection is established. */
  onConnected?: (send: (data: string) => void) => void;
  autoConnect?: boolean;
}

interface UseWebSocketReturn {
  status: WsStatus;
  reconnectAttempt: number;
  connect: () => void;
  disconnect: () => void;
  send: (data: string) => void;
  manualReconnect: () => void;
}

export function useCodingWebSocket({
  autoConnect = true,
  onConnected,
  onMessage,
  url,
}: UseWebSocketOptions): UseWebSocketReturn {
  const [status, setStatus] = useState<WsStatus>('disconnected');
  const [reconnectAttempt, setReconnectAttempt] = useState(0);

  const wsRef = useRef<WebSocket | null>(null);
  const reconnectAttemptRef = useRef(0);
  const reconnectTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const onMessageRef = useRef(onMessage);
  const onConnectedRef = useRef(onConnected);
  const urlRef = useRef(url);
  // Whether we had a successful connection before (for reconnecting vs first connect)
  const wasConnectedRef = useRef(false);
  // Whether disconnect() was called intentionally — suppresses auto-reconnect
  const intentionalDisconnectRef = useRef(false);

  onMessageRef.current = onMessage;
  onConnectedRef.current = onConnected;
  urlRef.current = url;

  // Use a stable ref for the core connect logic to break circular deps
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
    setReconnectAttempt(attempt + 1);
    console.warn(`[CodingWebSocket] Scheduling reconnect #${attempt + 1} in ${delay}ms`);
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

    setStatus('connecting');
    console.warn('[CodingWebSocket] Connecting to:', urlRef.current);
    const ws = new WebSocket(urlRef.current);
    wsRef.current = ws;

    ws.onopen = () => {
      if (wsRef.current !== ws) return;
      console.warn('[CodingWebSocket] Connected successfully');
      if (onConnectedRef.current) {
        onConnectedRef.current((data: string) => ws.send(data));
      }
      setStatus('connected');
      wasConnectedRef.current = true;
      reconnectAttemptRef.current = 0;
      setReconnectAttempt(0);
    };

    ws.onmessage = (e) => {
      onMessageRef.current(e.data);
    };

    ws.onerror = (e) => {
      console.error('[CodingWebSocket] Error:', e);
    };

    ws.onclose = (e) => {
      console.warn('[CodingWebSocket] Closed:', e.code, e.reason);
      if (wsRef.current !== ws) return;
      wsRef.current = null;

      if (intentionalDisconnectRef.current) {
        setStatus('disconnected');
        return;
      }

      // Unexpected disconnect — enter reconnecting state and schedule retry
      setStatus('reconnecting');
      scheduleReconnect();
    };
  }, [clearReconnectTimer, scheduleReconnect]);

  // Keep the ref in sync so scheduleReconnect's setTimeout always calls latest doConnect
  doConnectRef.current = doConnect;

  const connect = useCallback(() => {
    intentionalDisconnectRef.current = false;
    reconnectAttemptRef.current = 0;
    setReconnectAttempt(0);
    wasConnectedRef.current = false;
    doConnectRef.current();
  }, []);

  const disconnect = useCallback(() => {
    intentionalDisconnectRef.current = true;
    clearReconnectTimer();
    reconnectAttemptRef.current = 0;
    setReconnectAttempt(0);
    wasConnectedRef.current = false;
    if (wsRef.current) {
      wsRef.current.onclose = null;
      wsRef.current.onerror = null;
      wsRef.current.onopen = null;
      wsRef.current.onmessage = null;
      wsRef.current.close();
      wsRef.current = null;
    }
    setStatus('disconnected');
  }, [clearReconnectTimer]);

  const send = useCallback((data: string) => {
    if (wsRef.current?.readyState === WebSocket.OPEN) {
      wsRef.current.send(data);
    }
  }, []);

  const manualReconnect = useCallback(() => {
    intentionalDisconnectRef.current = false;
    reconnectAttemptRef.current = 0;
    setReconnectAttempt(0);
    wasConnectedRef.current = false;
    doConnectRef.current();
  }, []);

  useEffect(() => {
    if (autoConnect) {
      intentionalDisconnectRef.current = false;
      reconnectAttemptRef.current = 0;
      setReconnectAttempt(0);
      wasConnectedRef.current = false;
      doConnectRef.current();
    }
    return () => {
      // Component unmount: stop reconnection and clean up
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
      setStatus('disconnected');
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [url]);

  return { connect, disconnect, manualReconnect, reconnectAttempt, send, status };
}
