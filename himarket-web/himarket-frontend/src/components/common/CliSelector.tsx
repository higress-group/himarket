import { useState, useEffect, useCallback } from "react";
import { Select, Button, Switch } from "antd";
import { Plug, RefreshCw, Loader2, AlertCircle } from "lucide-react";
import { getCliProviders, type ICliProvider } from "../../lib/apis/cliProvider";
import { RuntimeSelector } from "./RuntimeSelector";
import { useRuntimeSelection } from "../../hooks/useRuntimeSelection";
import { CustomModelForm, type CustomModelFormData } from "../hicli/CustomModelForm";
import { MarketModelSelector } from "../hicli/MarketModelSelector";

// ============ 类型定义 ============

export type ModelConfigMode = 'none' | 'custom' | 'market';

export interface CliSelectorProps {
  onSelect: (cliId: string, cwd: string, runtime?: string, providerObj?: ICliProvider, customModelConfig?: string) => void;
  disabled: boolean;
  showRuntimeSelector?: boolean; // 默认 false，HiCli 传 true
}

export function CliSelector({ onSelect, disabled, showRuntimeSelector = false }: CliSelectorProps) {
  const [providers, setProviders] = useState<ICliProvider[]>([]);
  const [selectedCliId, setSelectedCliId] = useState<string>("");
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);

  // 当前选中的 provider 对象
  const selectedProvider = providers.find(p => p.key === selectedCliId) ?? null;

  // 运行时选择
  const { selectedRuntime, compatibleRuntimes, selectRuntime } = useRuntimeSelection({
    provider: selectedProvider,
  });

  // 模型配置模式（替代原来的 customModelEnabled 布尔状态）
  const [modelConfigMode, setModelConfigMode] = useState<ModelConfigMode>('none');
  const [customModelData, setCustomModelData] = useState<CustomModelFormData | null>(null);

  // 模式切换处理：开启一个模式时自动关闭另一个，并清除前一个模式的配置数据
  const handleCustomModelSwitch = useCallback((checked: boolean) => {
    if (checked) {
      setModelConfigMode('custom');
      setCustomModelData(null); // 清除之前可能的 market 数据
    } else {
      setModelConfigMode('none');
      setCustomModelData(null);
    }
  }, []);

  const handleMarketModelSwitch = useCallback((checked: boolean) => {
    if (checked) {
      setModelConfigMode('market');
      setCustomModelData(null); // 清除之前可能的 custom 数据
    } else {
      setModelConfigMode('none');
      setCustomModelData(null);
    }
  }, []);

  // CustomModelForm 数据变化回调
  const handleCustomFormChange = useCallback((data: CustomModelFormData | null) => {
    setCustomModelData(data);
  }, []);

  // MarketModelSelector 数据变化回调
  const handleMarketModelChange = useCallback((data: CustomModelFormData | null) => {
    setCustomModelData(data);
  }, []);

  // 切换 CLI 工具时重置模型配置模式
  useEffect(() => {
    setModelConfigMode('none');
    setCustomModelData(null);
  }, [selectedCliId]);

  const fetchProviders = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const res = await getCliProviders();
      const list: ICliProvider[] = Array.isArray(res.data)
        ? res.data
        : (res as any).data?.data ?? [];
      setProviders(list);

      // 自动选中默认且可用的 provider
      const available = list.filter((p) => p.available);
      const def =
        available.find((p) => p.isDefault) ?? available[0] ?? null;
      if (def) {
        setSelectedCliId(def.key);
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : "获取 CLI 工具列表失败");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchProviders();
  }, [fetchProviders]);

  const handleConnect = () => {
    if (selectedCliId) {
      // 连接时统一从 customModelData 获取配置（无论来源是手动还是模型市场）
      const configJson = modelConfigMode !== 'none' && customModelData
        ? JSON.stringify(customModelData)
        : undefined;
      onSelect(selectedCliId, "", selectedRuntime, selectedProvider ?? undefined, configJson);
    }
  };

  // 加载中
  if (loading) {
    return (
      <div className="flex flex-col items-center gap-3 py-6 text-gray-400">
        <Loader2 size={24} className="animate-spin" />
        <span className="text-sm">正在加载 CLI 工具列表...</span>
      </div>
    );
  }

  // 错误状态
  if (error) {
    return (
      <div className="flex flex-col items-center gap-3 py-6">
        <div className="flex items-center gap-2 text-red-500 text-sm">
          <AlertCircle size={16} />
          <span>{error}</span>
        </div>
        <Button
          size="small"
          icon={<RefreshCw size={14} />}
          onClick={fetchProviders}
          disabled={disabled}
        >
          重试
        </Button>
      </div>
    );
  }

  const availableProviders = providers.filter((p) => p.available);

  // 无可用 CLI
  if (availableProviders.length === 0) {
    return (
      <div className="flex flex-col items-center gap-3 py-6 text-gray-400">
        <AlertCircle size={24} />
        <span className="text-sm">没有可用的 CLI 工具</span>
        <Button
          size="small"
          icon={<RefreshCw size={14} />}
          onClick={fetchProviders}
          disabled={disabled}
        >
          刷新
        </Button>
      </div>
    );
  }

  const supportsCustomModel = selectedProvider?.supportsCustomModel ?? false;

  return (
    <div className="flex flex-col items-center gap-5 w-full max-w-sm">
      {/* CLI 工具选择 */}
      <div className="flex flex-col gap-1.5 w-full">
        <label className="text-sm font-medium text-gray-600 text-center">CLI 工具</label>
        <Select
          value={selectedCliId || undefined}
          onChange={(val) => setSelectedCliId(val)}
          placeholder="选择 CLI 工具"
          disabled={disabled}
          className="w-full"
          options={providers.map((p) => ({
            value: p.key,
            label: p.displayName + (!p.available ? " (不可用)" : ""),
            disabled: !p.available,
          }))}
        />
      </div>

      {/* 运行时选择 - 仅在 showRuntimeSelector 为 true 且有兼容运行时时显示 */}
      {showRuntimeSelector && compatibleRuntimes.length > 0 && (
        <RuntimeSelector
          cliProvider={selectedCliId}
          compatibleRuntimes={compatibleRuntimes}
          selectedRuntime={selectedRuntime}
          onSelect={selectRuntime}
        />
      )}

      {/* 模型配置模式切换 - 仅在选中的 provider 支持自定义模型时显示 */}
      {supportsCustomModel && (
        <div className="flex flex-col gap-3 w-full">
          {/* 使用自定义模型开关 */}
          <div className="flex items-center justify-center gap-2">
            <Switch
              size="small"
              checked={modelConfigMode === 'custom'}
              onChange={handleCustomModelSwitch}
            />
            <span className="text-sm text-gray-600">使用自定义模型</span>
          </div>

          {/* 使用模型市场模型开关 */}
          <div className="flex items-center justify-center gap-2">
            <Switch
              size="small"
              checked={modelConfigMode === 'market'}
              onChange={handleMarketModelSwitch}
            />
            <span className="text-sm text-gray-600">使用模型市场模型</span>
          </div>
        </div>
      )}

      {/* 自定义模型表单 - mode 为 custom 时显示 */}
      {supportsCustomModel && (
        <CustomModelForm
          enabled={modelConfigMode === 'custom'}
          onChange={handleCustomFormChange}
        />
      )}

      {/* 模型市场选择器 - mode 为 market 时显示 */}
      {supportsCustomModel && (
        <MarketModelSelector
          enabled={modelConfigMode === 'market'}
          onChange={handleMarketModelChange}
        />
      )}

      {/* 连接按钮 */}
      <Button
        type="primary"
        icon={<Plug size={14} />}
        onClick={handleConnect}
        disabled={disabled || !selectedCliId}
        className="px-8"
      >
        连接
      </Button>
    </div>
  );
}
