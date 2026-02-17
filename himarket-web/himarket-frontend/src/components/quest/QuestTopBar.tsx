import { Select, Tooltip } from "antd";
import {
  useQuestState,
  useActiveQuest,
} from "../../context/QuestSessionContext";
import type { WsStatus } from "../../hooks/useAcpWebSocket";
import { CliProviderSelect } from "../coding/CliProviderSelect";
import type { RuntimeOption } from "../common/RuntimeSelector";
import type { ICliProvider } from "../../lib/apis/cliProvider";

interface QuestTopBarProps {
  status: WsStatus;
  onSetModel: (modelId: string) => void;
  currentProvider: string;
  onProviderChange: (providerKey: string, providerObj?: ICliProvider) => void;
  selectedRuntime: string;
  compatibleRuntimes: RuntimeOption[];
  onRuntimeChange: (runtimeType: string) => void;
}

export function QuestTopBar({
  status,
  onSetModel,
  currentProvider,
  onProviderChange,
  selectedRuntime,
  compatibleRuntimes,
  onRuntimeChange,
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

      {compatibleRuntimes.length > 1 && (
        <Tooltip title="运行时方案">
          <Select
            size="small"
            variant="outlined"
            placement="bottomLeft"
            className="min-w-[100px]"
            value={selectedRuntime}
            onChange={onRuntimeChange}
            options={compatibleRuntimes.map(r => ({
              value: r.type,
              label: r.label,
              disabled: !r.available,
              title: r.available ? r.description : r.unavailableReason,
            }))}
          />
        </Tooltip>
      )}

      {modelOptions.length > 0 && (
        <Select
          size="small"
          variant="outlined"
          placement="bottomLeft"
          className="min-w-[120px]"
          value={quest?.currentModelId ?? ""}
          onChange={onSetModel}
          options={modelOptions.map(m => ({
            value: m.modelId,
            label: m.name,
          }))}
        />
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
