import { FolderOpen, Folder } from "lucide-react";
import {
  useQuestState,
  useActiveQuest,
} from "../../context/QuestSessionContext";
import type { WsStatus } from "../../hooks/useAcpWebSocket";
import { CliProviderSelect } from "./CliProviderSelect";

interface CodingTopBarProps {
  status: WsStatus;
  onSetModel: (modelId: string) => void;
  fileTreeVisible: boolean;
  onToggleFileTree: () => void;
  currentProvider: string;
  onProviderChange: (providerKey: string) => void;
}

export function CodingTopBar({
  status,
  onSetModel,
  fileTreeVisible,
  onToggleFileTree,
  currentProvider,
  onProviderChange,
}: CodingTopBarProps) {
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
    <div className="flex items-center gap-3 px-3 py-1.5 border-b border-gray-200/60 bg-white/30 backdrop-blur-sm flex-shrink-0">
      <div className="text-sm font-semibold text-gray-700">HiCoding</div>

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

      <button
        className={`w-7 h-7 flex items-center justify-center rounded transition-colors ml-2
          ${fileTreeVisible ? "text-blue-600 bg-blue-50" : "text-gray-400 hover:text-gray-600 hover:bg-gray-100"}`}
        onClick={onToggleFileTree}
        title={fileTreeVisible ? "隐藏文件" : "显示文件"}
      >
        {fileTreeVisible ? <FolderOpen size={16} /> : <Folder size={16} />}
      </button>
    </div>
  );
}
