import { useEffect, useRef, useState } from "react";
import { WifiOff, Wifi, RefreshCw } from "lucide-react";
import type { WsStatus } from "../../hooks/useCodingWebSocket";

interface ConnectionBannerProps {
  acpStatus: WsStatus;
  reconnectAttempt: number;
  onManualReconnect: () => void;
}

export function ConnectionBanner({
  acpStatus,
  reconnectAttempt,
  onManualReconnect,
}: ConnectionBannerProps) {
  const prevStatusRef = useRef<WsStatus>(acpStatus);
  const [showRecovered, setShowRecovered] = useState(false);

  useEffect(() => {
    const prev = prevStatusRef.current;
    prevStatusRef.current = acpStatus;

    if (acpStatus === "connected" && prev === "reconnecting") {
      setShowRecovered(true);
      const timer = setTimeout(() => setShowRecovered(false), 2000);
      return () => clearTimeout(timer);
    }

    if (acpStatus !== "connected") {
      setShowRecovered(false);
    }
  }, [acpStatus]);

  // reconnecting → 黄色横幅
  if (acpStatus === "reconnecting") {
    return (
      <div
        className="flex items-center gap-2 px-4 py-2 text-sm bg-yellow-50 border-b border-yellow-200 text-yellow-700"
        role="alert"
      >
        <WifiOff size={16} className="shrink-0" />
        <span>连接已断开，正在重连...（第 {reconnectAttempt} 次）</span>
      </div>
    );
  }

  // disconnected → 红色横幅
  if (acpStatus === "disconnected") {
    return (
      <div
        className="flex items-center gap-2 px-4 py-2 text-sm bg-red-50 border-b border-red-200 text-red-700"
        role="alert"
      >
        <WifiOff size={16} className="shrink-0" />
        <span>连接已断开</span>
        <button
          onClick={onManualReconnect}
          className="ml-auto flex items-center gap-1 px-2 py-0.5 rounded text-xs font-medium bg-red-100 hover:bg-red-200 text-red-700 transition-colors"
        >
          <RefreshCw size={12} />
          重新连接
        </button>
      </div>
    );
  }

  // connected（刚从 reconnecting 恢复）→ 绿色横幅，2 秒后隐藏
  if (acpStatus === "connected" && showRecovered) {
    return (
      <div
        className="flex items-center gap-2 px-4 py-2 text-sm bg-green-50 border-b border-green-200 text-green-700"
        role="alert"
      >
        <Wifi size={16} className="shrink-0" />
        <span>已重新连接</span>
      </div>
    );
  }

  // connected（正常）→ 不渲染
  return null;
}
