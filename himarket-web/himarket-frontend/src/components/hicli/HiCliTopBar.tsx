import { Select } from "antd";
import { ScrollText, Bot, Wifi, WifiOff, Loader2, Monitor, Container, FolderOpen } from "lucide-react";
import { useHiCliState } from "../../context/HiCliSessionContext";
import { useActiveQuest } from "../../context/QuestSessionContext";
import type { WsStatus } from "../../hooks/useAcpWebSocket";

type DebugTab = "none" | "acplog" | "info";

interface HiCliTopBarProps {
  status: WsStatus;
  onSetModel: (modelId: string) => void;
  onSetMode: (modeId: string) => void;
  currentProvider: string;
  debugTab: DebugTab;
  onToggleDebugTab: (tab: DebugTab) => void;
}

export function HiCliTopBar({
  status,
  onSetModel,
  onSetMode,
  currentProvider,
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

      {/* Runtime type badge */}
      {hiCliState.runtimeType && (
        <div
          className={`flex items-center gap-1 text-xs px-2 py-0.5 rounded ${
            hiCliState.runtimeType === "k8s"
              ? "text-blue-600 bg-blue-50"
              : "text-gray-500 bg-gray-100"
          }`}
          title={hiCliState.runtimeType === "k8s" ? "K8s 沙箱运行" : "本地运行"}
        >
          {hiCliState.runtimeType === "k8s" ? <Container size={12} /> : <Monitor size={12} />}
          <span>{hiCliState.runtimeType === "k8s" ? "沙箱" : "本地"}</span>
        </div>
      )}

      {/* Working directory */}
      {hiCliState.cwd && (
        <div
          className="flex items-center gap-1 text-xs text-gray-500 bg-gray-100 px-2 py-0.5 rounded truncate max-w-[240px]"
          title={`工作目录: ${hiCliState.cwd}`}
        >
          <FolderOpen size={12} className="flex-shrink-0" />
          <span className="truncate">{hiCliState.cwd}</span>
        </div>
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
        {hiCliState.sandboxStatus?.status === "creating" && (
          <span className="ml-2 text-blue-500 animate-pulse">
            {hiCliState.sandboxStatus.message}
          </span>
        )}
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
