import { useState, useEffect, useCallback } from "react";
import { Select, Input, Button } from "antd";
import { FolderOpen, Plug, RefreshCw, Loader2, AlertCircle } from "lucide-react";
import { getCliProviders, type ICliProvider } from "../../lib/apis/cliProvider";
import { RuntimeSelector } from "./RuntimeSelector";
import { useRuntimeSelection } from "../../hooks/useRuntimeSelection";

export interface CliSelectorProps {
  onSelect: (cliId: string, cwd: string, runtime?: string, providerObj?: ICliProvider) => void;
  disabled: boolean;
  showRuntimeSelector?: boolean; // 默认 false，HiCli 传 true
}

export function CliSelector({ onSelect, disabled, showRuntimeSelector = false }: CliSelectorProps) {
  const [providers, setProviders] = useState<ICliProvider[]>([]);
  const [selectedCliId, setSelectedCliId] = useState<string>("");
  const [cwd, setCwd] = useState<string>("/Users/xujingfeng/NodeProjects/qoderwork");
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);

  // 当前选中的 provider 对象
  const selectedProvider = providers.find(p => p.key === selectedCliId) ?? null;

  // 运行时选择
  const { selectedRuntime, compatibleRuntimes, selectRuntime } = useRuntimeSelection({
    provider: selectedProvider,
  });

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
    if (selectedCliId && cwd.trim()) {
      onSelect(selectedCliId, cwd.trim(), selectedRuntime, selectedProvider ?? undefined);
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

      {/* 工作目录输入 */}
      <div className="flex flex-col gap-1.5 w-full">
        <label className="text-sm font-medium text-gray-600 text-center">工作目录</label>
        <Input
          value={cwd}
          onChange={(e) => setCwd(e.target.value)}
          placeholder="输入工作目录路径，例如 /home/user/project"
          prefix={<FolderOpen size={14} className="text-gray-400" />}
          disabled={disabled}
          onPressEnter={handleConnect}
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

      {/* 连接按钮 */}
      <Button
        type="primary"
        icon={<Plug size={14} />}
        onClick={handleConnect}
        disabled={disabled || !selectedCliId || !cwd.trim()}
        className="px-8"
      >
        连接
      </Button>
    </div>
  );
}
