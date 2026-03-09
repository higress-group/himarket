import type { WsStatus } from "../../hooks/useAcpWebSocket";

interface ConversationTopBarProps {
  status: WsStatus;
  questTitle: string;
  usage?: { used: number; size: number; cost?: { amount: number } };
}

export function ConversationTopBar({
  status,
  questTitle,
  usage,
}: ConversationTopBarProps) {
  const statusColor =
    status === "connected"
      ? "bg-green-500"
      : status === "connecting" || status === "reconnecting"
        ? "bg-yellow-500 animate-pulse"
        : "bg-gray-400";

  return (
    <div className="flex items-center gap-3 px-4 py-2 border-b border-gray-200/60 bg-white/30 backdrop-blur-sm flex-shrink-0">
      <div className="text-sm font-medium text-gray-700 truncate max-w-[200px]">
        {questTitle || "HiCoding"}
      </div>

      <div className="flex-1" />

      {usage && (
        <div className="text-[11px] text-gray-400">
          Tokens: {usage.used}/{usage.size}
          {usage.cost && <> | ${usage.cost.amount.toFixed(4)}</>}
        </div>
      )}

      <div className="flex items-center gap-1.5 text-[11px] text-gray-400">
        <span className={`w-1.5 h-1.5 rounded-full ${statusColor}`} />
        {status}
      </div>
    </div>
  );
}
