import { Select, Tooltip } from "antd";
import { ScrollText, Bot, Wifi, WifiOff, Loader2 } from "lucide-react";
import type { RuntimeOption } from "../common/RuntimeSelector";
import { useHiCliState } from "../../context/HiCliSessionContext";
import { useActiveQuest } from "../../context/QuestSessionContext";
import type { WsStatus } from "../../hooks/useAcpWebSocket";

type DebugTab = "none" | "acplog" | "info";

interface HiCliTopBarProps {
  status: WsStatus;
  onSetModel: (modelId: string) => void;
  onSetMode: (modeId: string) => void;
  currentProvider: string;
  onProviderChange: (providerKey: string) => void;
  selectedRuntime?: string;
  compatibleRuntimes?: RuntimeOption[];
  onRuntimeChange?: (runtimeType: string) => void;
  debugTab: DebugTab;
  onToggleDebugTab: (tab: DebugTab) => void;
}

export function HiCliTopBar({
  status,
  onSetModel,
  onSetMode,
  currentProvider,
  selectedRuntime,
  compatibleRuntimes,
  onRuntimeChange,
  debugTab,
  onToggleDebugTab,
}: HiCliTopBarProps) {
  const hiCliState = useHiCliState();
  const quest = useActiveQuest();

  const { agentInfo, usage } = hiCliState;

  // Model / Mode options: prefer active quest's list, fallback to global
  const modelOptions =
    quest?.availableModels && quest.availableModels.length > 0
      ? quest.availableModels
      : hiCliState.models;

  const modeOptions =
    quest?.availableModes && quest.availableModes.length > 0
      ? quest.availableModes
      : hiCliState.modes;

  // Connection status indicator
  const statusIcon =
    status === "connected" ? (
      <Wifi size={14} className="text-green-500" />
    ) : status === "connecting" ? (
      <Loader2 size={14} className="text-yellow-500 animate-spin" />
    ) : (
      <WifiOff size={14} className="text-gray-400" />
    );

  const statusColor =
    status === "connected"
      ? "text-green-600"
      : status === "connecting"
        ? "text-yellow-600"
        : "text-gray-400";

  return (
    <div className="flex items-center gap-3 px-3 py-1.5 border-b border-gray-200/60 bg-white/30 backdrop-blur-sm flex-shrink-0">
      {/* Agent name / version */}
      {agentInfo?.name && (
        <div className="flex items-center gap-1.5 text-sm font-semibold text-gray-700">
          <span>{agentInfo.title ?? agentInfo.name}</span>
          {agentInfo.version && (
            <span className="text-[11px] font-normal text-gray-400">
              v{agentInfo.version}
            </span>
          )}
        </div>
      )}

      {/* Current CLI tool name */}
      {currentProvider && (
        <div
          className="text-xs text-gray-500 bg-gray-100 px-2 py-0.5 rounded truncate max-w-[160px]"
          title={currentProvider}
        >
          {currentProvider}
        </div>
      )}

      {/* Runtime select */}
      {compatibleRuntimes && compatibleRuntimes.length > 1 && onRuntimeChange && (
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

      {/* Model select */}
      {modelOptions.length > 0 && (
        <Select
          size="small"
          variant="outlined"
          placement="bottomLeft"
          className="min-w-[120px] max-w-[220px]"
          popupMatchSelectWidth={false}
          value={quest?.currentModelId || undefined}
          onChange={onSetModel}
          options={modelOptions.map((m) => ({
            value: m.modelId,
            label: m.name,
            title: m.name,
          }))}
        />
      )}

      {/* Mode select */}
      {modeOptions.length > 0 && (
        <Select
          size="small"
          variant="outlined"
          placement="bottomLeft"
          className="min-w-[100px] max-w-[180px]"
          popupMatchSelectWidth={false}
          value={quest?.currentModeId || undefined}
          onChange={onSetMode}
          options={modeOptions.map((m) => ({
            value: m.id,
            label: m.name,
            title: m.name,
          }))}
        />
      )}

      <div className="flex-1" />

      {/* Token usage & cost */}
      {usage && (
        <div className="text-[11px] text-gray-400">
          Tokens: {usage.used}/{usage.size}
          {usage.cost && <> | ${usage.cost.amount.toFixed(4)}</>}
        </div>
      )}

      {/* WebSocket connection status */}
      <div className={`flex items-center gap-1.5 text-[11px] ${statusColor}`}>
        {statusIcon}
        <span>{status}</span>
      </div>

      {/* Debug tab toggle buttons */}
      <div className="flex items-center gap-1 ml-2">
        <button
          className={`flex items-center gap-1 px-2 py-1 rounded text-xs transition-colors ${
            debugTab === "acplog"
              ? "text-blue-600 bg-blue-50"
              : "text-gray-400 hover:text-gray-600 hover:bg-gray-100"
          }`}
          onClick={() =>
            onToggleDebugTab(debugTab === "acplog" ? "none" : "acplog")
          }
          title="ACP 日志"
        >
          <ScrollText size={14} />
          <span>ACP 日志</span>
        </button>
        <button
          className={`flex items-center gap-1 px-2 py-1 rounded text-xs transition-colors ${
            debugTab === "info"
              ? "text-blue-600 bg-blue-50"
              : "text-gray-400 hover:text-gray-600 hover:bg-gray-100"
          }`}
          onClick={() =>
            onToggleDebugTab(debugTab === "info" ? "none" : "info")
          }
          title="Agent 信息"
        >
          <Bot size={14} />
          <span>Agent 信息</span>
        </button>
      </div>
    </div>
  );
}
