import { Select } from 'antd';
import { FolderOpen, Folder } from 'lucide-react';

import { useCodingState, useActiveCodingSession } from '../../context/CodingSessionContext';

import type { WsStatus } from '../../hooks/useCodingWebSocket';

interface CodingTopBarProps {
  status: WsStatus;
  onSetModel: (modelId: string) => void;
  fileTreeVisible: boolean;
  onToggleFileTree: () => void;
}

export function CodingTopBar({
  fileTreeVisible,
  onSetModel,
  onToggleFileTree,
  status,
}: CodingTopBarProps) {
  const state = useCodingState();
  const quest = useActiveCodingSession();

  const statusColor =
    status === 'connected'
      ? 'bg-green-500'
      : status === 'connecting' || status === 'reconnecting'
        ? 'bg-yellow-500 animate-pulse'
        : 'bg-gray-400';
  const modelOptions =
    quest?.availableModels && quest.availableModels.length > 0
      ? quest.availableModels
      : state.models;

  return (
    <div className="flex items-center gap-3 px-3 py-1.5 border-b border-gray-200/60 bg-white/30 backdrop-blur-sm flex-shrink-0">
      <div className="text-sm font-semibold text-gray-700">HiCoding</div>

      {modelOptions.length > 0 && (
        <Select
          className="min-w-[120px]"
          onChange={onSetModel}
          options={modelOptions.map((m) => ({
            label: m.name,
            value: m.modelId,
          }))}
          placement="bottomLeft"
          size="small"
          value={quest?.currentModelId ?? ''}
          variant="outlined"
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

      <button
        className={`w-7 h-7 flex items-center justify-center rounded transition-colors ml-2
          ${fileTreeVisible ? 'text-blue-600 bg-blue-50' : 'text-gray-400 hover:text-gray-600 hover:bg-gray-100'}`}
        onClick={onToggleFileTree}
        title={fileTreeVisible ? '隐藏文件' : '显示文件'}
      >
        {fileTreeVisible ? <FolderOpen size={16} /> : <Folder size={16} />}
      </button>
    </div>
  );
}
