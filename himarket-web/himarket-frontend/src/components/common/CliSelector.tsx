import { useState, useEffect, useCallback, useMemo } from "react";
import { Button, Segmented } from "antd";
import { Plug, RefreshCw, Loader2, AlertCircle, ChevronLeft, ChevronRight } from "lucide-react";
import { getCliProviders, type ICliProvider, type McpServerEntry, type SkillEntry, type CliSessionConfig } from "../../lib/apis/cliProvider";
import { RuntimeSelector } from "./RuntimeSelector";
import { useRuntimeSelection } from "../../hooks/useRuntimeSelection";
import { CustomModelForm, type CustomModelFormData } from "../hicli/CustomModelForm";
import { MarketModelSelector } from "../hicli/MarketModelSelector";
import { MarketMcpSelector } from "../hicli/MarketMcpSelector";
import { MarketSkillSelector } from "../hicli/MarketSkillSelector";
import { SelectableCard } from "./SelectableCard";
import { sortCliProviders } from "../../lib/utils/cliProviderSort";
import { getVisibleSteps, type StepConfig } from "./stepUtils";

// ============ 类型定义 ============

export type ModelConfigMode = 'none' | 'custom' | 'market';

export interface CliSelectorProps {
  onSelect: (cliId: string, cwd: string, runtime?: string, providerObj?: ICliProvider, cliSessionConfig?: string) => void;
  disabled: boolean;
  showRuntimeSelector?: boolean; // 默认 false，HiCli 传 true
}

export function CliSelector({ onSelect, disabled, showRuntimeSelector = false }: CliSelectorProps) {
  const [providers, setProviders] = useState<ICliProvider[]>([]);
  const [selectedCliId, setSelectedCliId] = useState<string>("");
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);

  // 当前步骤索引（从 0 开始）
  const [currentStepIndex, setCurrentStepIndex] = useState<number>(0);

  // 当前选中的 provider 对象
  const selectedProvider = providers.find(p => p.key === selectedCliId) ?? null;

  // 运行时选择
  const { selectedRuntime, compatibleRuntimes, selectRuntime } = useRuntimeSelection({
    provider: selectedProvider,
  });

  // 模型配置模式
  const [modelConfigMode, setModelConfigMode] = useState<ModelConfigMode>('none');
  const [customModelData, setCustomModelData] = useState<CustomModelFormData | null>(null);

  // MCP 和 Skill 选择状态
  const [mcpEnabled, setMcpEnabled] = useState(false);
  const [skillEnabled, setSkillEnabled] = useState(false);
  const [selectedMcps, setSelectedMcps] = useState<McpServerEntry[] | null>(null);
  const [selectedSkills, setSelectedSkills] = useState<SkillEntry[] | null>(null);

  // 使用 sortCliProviders 对 provider 列表排序
  const sortedProviders = useMemo(() => sortCliProviders(providers), [providers]);

  // 根据选中 provider 的能力动态计算可见步骤
  const visibleSteps: StepConfig[] = useMemo(
    () => getVisibleSteps(selectedProvider),
    [selectedProvider]
  );

  // 当前步骤配置
  const currentStep = visibleSteps[currentStepIndex] ?? visibleSteps[0];
  const isLastStep = currentStepIndex === visibleSteps.length - 1;
  const isFirstStep = currentStepIndex === 0;

  // 步骤一是否配置完成（选中了可用的 CLI 工具）
  const isStep1Complete = !!selectedCliId && (selectedProvider?.available ?? false);

  // 当前步骤的"下一步"按钮是否可用
  const canGoNext = useMemo(() => {
    if (!currentStep) return false;
    switch (currentStep.key) {
      case 'select-tool':
        return isStep1Complete;
      case 'model-config':
        // 模型配置步骤：始终可以继续（默认模型不需要额外配置）
        return true;
      case 'extension-config':
        // 扩展配置步骤：始终可以继续（MCP/Skill 是可选的）
        return true;
      default:
        return false;
    }
  }, [currentStep, isStep1Complete]);

  // CustomModelForm 数据变化回调
  const handleCustomFormChange = useCallback((data: CustomModelFormData | null) => {
    setCustomModelData(data);
  }, []);

  // MarketModelSelector 数据变化回调
  const handleMarketModelChange = useCallback((data: CustomModelFormData | null) => {
    setCustomModelData(data);
  }, []);

  // 切换 CLI 工具时重置模型配置模式和 MCP/Skill 选择状态，并回到步骤一
  useEffect(() => {
    setModelConfigMode('none');
    setCustomModelData(null);
    setMcpEnabled(false);
    setSkillEnabled(false);
    setSelectedMcps(null);
    setSelectedSkills(null);
    setCurrentStepIndex(0);
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

      // 使用排序后的列表自动选中第一个可用 provider
      const sorted = sortCliProviders(list);
      const firstAvailable = sorted.find(p => p.available);
      if (firstAvailable) {
        setSelectedCliId(firstAvailable.key);
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

  // 导航：上一步
  const handlePrevStep = useCallback(() => {
    setCurrentStepIndex(prev => Math.max(0, prev - 1));
  }, []);

  // 导航：下一步
  const handleNextStep = useCallback(() => {
    setCurrentStepIndex(prev => Math.min(visibleSteps.length - 1, prev + 1));
  }, [visibleSteps.length]);

  // 连接处理
  const handleConnect = useCallback(() => {
    if (!selectedCliId) return;

    const sessionConfig: CliSessionConfig = {};

    // 模型配置
    if (modelConfigMode !== 'none' && customModelData) {
      sessionConfig.customModelConfig = customModelData;
    }

    // MCP 配置
    if (mcpEnabled && selectedMcps && selectedMcps.length > 0) {
      sessionConfig.mcpServers = selectedMcps;
    }

    // Skill 配置
    if (skillEnabled && selectedSkills && selectedSkills.length > 0) {
      sessionConfig.skills = selectedSkills;
    }

    // 有任何配置时传递 cliSessionConfig
    const hasConfig = sessionConfig.customModelConfig
      || sessionConfig.mcpServers
      || sessionConfig.skills;
    const configJson = hasConfig ? JSON.stringify(sessionConfig) : undefined;

    onSelect(selectedCliId, "", selectedRuntime, selectedProvider ?? undefined, configJson);
  }, [selectedCliId, modelConfigMode, customModelData, mcpEnabled, selectedMcps, skillEnabled, selectedSkills, selectedRuntime, selectedProvider, onSelect]);

  // 选择 CLI 工具卡片
  const handleSelectProvider = useCallback((key: string) => {
    const provider = sortedProviders.find(p => p.key === key);
    if (provider?.available) {
      setSelectedCliId(key);
    }
  }, [sortedProviders]);

  // ============ 渲染 ============

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

  const availableProviders = sortedProviders.filter((p) => p.available);

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
  const supportsMcp = selectedProvider?.supportsMcp ?? false;
  const supportsSkill = selectedProvider?.supportsSkill ?? false;

  // ============ 步骤内容渲染 ============

  /** 步骤一：选择工具 + 运行时 */
  const renderStep1 = () => (
    <div className="flex flex-col gap-4 w-full">
      {/* CLI 工具卡片式单选 */}
      <div className="flex flex-col gap-2 w-full">
        <label className="text-sm font-medium text-gray-600">CLI 工具</label>
        <div className="grid grid-cols-2 gap-2">
          {sortedProviders.map((p) => (
            <SelectableCard
              key={p.key}
              selected={selectedCliId === p.key}
              disabled={!p.available}
              onClick={() => handleSelectProvider(p.key)}
            >
              <div className="flex flex-col gap-0.5">
                <span className={`text-sm font-medium ${!p.available ? 'text-gray-400' : 'text-gray-800'}`}>
                  {p.displayName}
                </span>
                {!p.available && (
                  <span className="text-xs text-gray-400">不可用</span>
                )}
              </div>
            </SelectableCard>
          ))}
        </div>
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
    </div>
  );

  /** 步骤二：模型配置（Segmented Control 三选一） */
  const modelConfigOptions = [
    { label: '默认模型', value: 'none' as ModelConfigMode },
    { label: '自定义模型', value: 'custom' as ModelConfigMode },
    { label: '市场模型', value: 'market' as ModelConfigMode },
  ];

  const handleModelConfigModeChange = (value: ModelConfigMode) => {
    setModelConfigMode(value);
    setCustomModelData(null);
  };

  const renderStep2 = () => (
    <div className="flex flex-col gap-3 w-full">
      {supportsCustomModel && (
        <>
          <Segmented
            options={modelConfigOptions}
            value={modelConfigMode}
            onChange={handleModelConfigModeChange}
            block
          />

          {/* 自定义模型表单 */}
          <CustomModelForm
            enabled={modelConfigMode === 'custom'}
            onChange={handleCustomFormChange}
          />

          {/* 模型市场选择器 */}
          <MarketModelSelector
            enabled={modelConfigMode === 'market'}
            onChange={handleMarketModelChange}
          />
        </>
      )}
    </div>
  );

  /** 步骤三：扩展配置（MCP + Skill，保留当前组件用法） */
  const renderStep3 = () => (
    <div className="flex flex-col gap-4 w-full">
      {/* MCP Server 选择器 */}
      {supportsMcp && (
        <div className="flex flex-col gap-2 w-full">
          <label className="text-sm font-medium text-gray-600">MCP Server</label>
          <MarketMcpSelector
            onChange={(mcps) => {
              setSelectedMcps(mcps);
              setMcpEnabled(mcps !== null && mcps.length > 0);
            }}
          />
        </div>
      )}

      {/* Skill 选择器 */}
      {supportsSkill && (
        <div className="flex flex-col gap-2 w-full">
          <label className="text-sm font-medium text-gray-600">Skill</label>
          <MarketSkillSelector
            onChange={(skills) => {
              setSelectedSkills(skills);
              setSkillEnabled(skills !== null && skills.length > 0);
            }}
          />
        </div>
      )}
    </div>
  );

  /** 根据当前步骤 key 渲染对应内容 */
  const renderCurrentStep = () => {
    if (!currentStep) return null;
    switch (currentStep.key) {
      case 'select-tool':
        return renderStep1();
      case 'model-config':
        return renderStep2();
      case 'extension-config':
        return renderStep3();
      default:
        return null;
    }
  };

  return (
    <div className="flex flex-col items-center gap-5 w-full max-w-md">
      {/* 步骤指示器 */}
      {visibleSteps.length > 1 && (
        <div className="flex items-center justify-center gap-2 text-sm text-gray-500 w-full">
          <span>
            步骤 {currentStepIndex + 1} / {visibleSteps.length}
          </span>
          <span className="text-gray-400">—</span>
          <span className="font-medium text-gray-700">{currentStep?.title}</span>
        </div>
      )}

      {/* 当前步骤内容 */}
      {renderCurrentStep()}

      {/* 导航按钮 */}
      <div className="flex items-center justify-center gap-3 w-full">
        {/* 上一步按钮 - 仅在非第一步且有多步骤时显示 */}
        {visibleSteps.length > 1 && !isFirstStep && (
          <Button
            icon={<ChevronLeft size={14} />}
            onClick={handlePrevStep}
            disabled={disabled}
          >
            上一步
          </Button>
        )}

        {/* 下一步 / 连接按钮 */}
        {isLastStep ? (
          <Button
            type="primary"
            icon={<Plug size={14} />}
            onClick={handleConnect}
            disabled={disabled || !isStep1Complete}
            className="px-8"
          >
            连接
          </Button>
        ) : (
          <Button
            type="primary"
            onClick={handleNextStep}
            disabled={disabled || !canGoNext}
          >
            下一步
            <ChevronRight size={14} />
          </Button>
        )}
      </div>
    </div>
  );
}
