interface ConversationTopBarProps {
  sessionTitle: string;
  usage?: { used: number; size: number; cost?: { amount: number } };
}

export function ConversationTopBar({
  sessionTitle,
  usage,
}: ConversationTopBarProps) {
  return (
    <div className="flex items-center gap-3 px-4 py-2 border-b border-gray-200/60 bg-white/30 backdrop-blur-sm flex-shrink-0">
      <div className="text-sm font-medium text-gray-700 truncate max-w-[200px]">
        {sessionTitle || "HiCoding"}
      </div>

      <div className="flex-1" />

      {usage && (
        <div className="text-[11px] text-gray-400">
          Tokens: {usage.used}/{usage.size}
          {usage.cost && <> | ${usage.cost.amount.toFixed(4)}</>}
        </div>
      )}
    </div>
  );
}
