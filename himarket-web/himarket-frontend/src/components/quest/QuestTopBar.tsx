import {
  useQuestState,
  useActiveQuest,
} from "../../context/QuestSessionContext";
import type { WsStatus } from "../../hooks/useAcpWebSocket";
import { CliProviderSelect } from "../coding/CliProviderSelect";

interface QuestTopBarProps {
  status: WsStatus;
  onSetModel: (modelId: string) => void;
  currentProvider: string;
  onProviderChange: (providerKey: string) => void;
}

export function QuestTopBar({
  status,
  onSetModel,
  currentProvider,
  onProviderChange,
}: QuestTopBarProps) {
  const state = useQuestState();
  const quest = useActiveQuest();

  const statusColor =
    status === "connected"
      ? "bg-green-500"
      : status === "connecting"
        ? "bg-yellow-500 animate-pulse"
        : "bg-gray-400";
  const modelOptions =
    quest?.availableModels && quest.availableModels.length > 0
      ? quest.availableModels
      : state.models;

  return (
    <div className="flex items-center gap-3 px-4 py-2 border-b border-gray-200/60 bg-white/30 backdrop-blur-sm">
      <div className="text-sm font-medium text-gray-700 truncate max-w-[200px]">
        {quest?.title ?? "HiWork"}
      </div>

      <CliProviderSelect value={currentProvider} onChange={onProviderChange} />

      {modelOptions.length > 0 && (
        <select
          className="text-xs border border-gray-200 rounded-md px-2 py-1 bg-white/80
                     text-gray-600 outline-none focus:border-gray-400 transition-colors"
          value={quest?.currentModelId ?? ""}
          onChange={e => onSetModel(e.target.value)}
        >
          {modelOptions.map(m => (
            <option key={m.modelId} value={m.modelId}>
              {m.name}
            </option>
          ))}
        </select>
      )}

      <div className="flex-1" />

      {state.usage && (
        <div className="text-[11px] text-gray-400">
          Tokens: {state.usage.used}/{state.usage.size}
          {state.usage.cost && <> | ${state.usage.cost.amount.toFixed(4)}</>}
        </div>
      )}

      <div className="flex items-center gap-1.5 text-[11px] text-gray-400">
        <span className={`w-1.5 h-1.5 rounded-full ${statusColor}`} />
        {status}
      </div>
    </div>
  );
}
