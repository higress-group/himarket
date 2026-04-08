import { Drawer, Select, Button, Divider } from 'antd';
import { Plug, Loader2, AlertCircle, RefreshCw } from 'lucide-react';
import { useState, useEffect, useCallback, useMemo } from 'react';

import {
  getCliProviders,
  getMarketMcps,
  getMarketSkills,
  getMarketModels,
  type ICliProvider,
  type MarketModelInfo,
  type MarketMcpInfo,
  type MarketSkillInfo,
  type McpServerEntry,
  type SkillEntry,
} from '../../lib/apis/cliProvider';
import { sortCliProviders } from '../../lib/utils/cliProviderSort';
import { SelectableCard } from '../common/SelectableCard';

import type { CodingConfig } from '../../types/coding';

// ============ Props ============

export interface ConfigSidebarProps {
  open: boolean;
  onClose: () => void;
  config: CodingConfig;
  onConfigChange: (config: CodingConfig) => void;
  isFirstTime: boolean;
}

// ============ 组件 ============

export function ConfigSidebar({
  config,
  isFirstTime,
  onClose,
  onConfigChange,
  open,
}: ConfigSidebarProps) {
  // CLI providers
  const [providers, setProviders] = useState<ICliProvider[]>([]);
  const [cliLoading, setCliLoading] = useState(true);
  const [cliError, setCliError] = useState<string | null>(null);

  // MCP servers
  const [mcpServers, setMcpServers] = useState<MarketMcpInfo[]>([]);
  const [mcpLoading, setMcpLoading] = useState(false);

  // Skills
  const [skills, setSkills] = useState<MarketSkillInfo[]>([]);
  const [skillLoading, setSkillLoading] = useState(false);

  // 市场模型
  const [marketModels, setMarketModels] = useState<MarketModelInfo[]>([]);
  const [modelLoading, setModelLoading] = useState(false);

  const sortedProviders = useMemo(() => sortCliProviders(providers), [providers]);

  // 配置是否完整（modelProductId + cliProviderId 非空）
  const isComplete = config.modelProductId !== null && config.cliProviderId !== null;

  // ============ 数据加载 ============

  const fetchProviders = useCallback(async () => {
    setCliLoading(true);
    setCliError(null);
    try {
      const res = await getCliProviders();
      const list: ICliProvider[] = Array.isArray(res.data)
        ? res.data
        : (((
            (res as unknown as Record<string, unknown>).data as Record<string, unknown> | undefined
          )?.data ?? []) as ICliProvider[]);
      setProviders(list);
    } catch {
      setCliError('获取 CLI 工具列表失败');
    } finally {
      setCliLoading(false);
    }
  }, []);

  const fetchMcps = useCallback(async () => {
    setMcpLoading(true);
    try {
      const res = await getMarketMcps();
      setMcpServers(res.data.mcpServers ?? []);
    } catch {
      // 静默处理
    } finally {
      setMcpLoading(false);
    }
  }, []);

  const fetchSkills = useCallback(async () => {
    setSkillLoading(true);
    try {
      const res = await getMarketSkills();
      setSkills(Array.isArray(res.data) ? res.data : []);
    } catch {
      // 静默处理
    } finally {
      setSkillLoading(false);
    }
  }, []);

  const fetchModels = useCallback(async () => {
    setModelLoading(true);
    try {
      const res = await getMarketModels();
      setMarketModels(res.data.models ?? []);
    } catch {
      // 静默处理
    } finally {
      setModelLoading(false);
    }
  }, []);

  useEffect(() => {
    if (open) {
      fetchProviders();
      fetchMcps();
      fetchSkills();
      fetchModels();
    }
  }, [open, fetchProviders, fetchMcps, fetchSkills, fetchModels]);

  // ============ 事件处理 ============

  const handleSelectCli = useCallback(
    (key: string) => {
      const provider = sortedProviders.find((p) => p.key === key);
      if (provider?.available) {
        onConfigChange({ ...config, cliProviderId: key, cliProviderName: provider.displayName });
      }
    },
    [sortedProviders, config, onConfigChange],
  );

  const handleSelectModel = useCallback(
    (productId: string) => {
      const model = marketModels.find((m) => m.productId === productId);
      onConfigChange({ ...config, modelName: model?.name ?? null, modelProductId: productId });
    },
    [config, onConfigChange, marketModels],
  );

  const handleToggleMcp = useCallback(
    (productId: string) => {
      const current = config.mcpServers ?? [];
      const next = current.includes(productId)
        ? current.filter((id) => id !== productId)
        : [...current, productId];
      onConfigChange({ ...config, mcpServers: next });
    },
    [config, onConfigChange],
  );

  const handleToggleSkill = useCallback(
    (productId: string) => {
      const current = config.skills ?? [];
      const next = current.includes(productId)
        ? current.filter((id) => id !== productId)
        : [...current, productId];
      onConfigChange({ ...config, skills: next });
    },
    [config, onConfigChange],
  );

  // 构建包含模型/MCP/Skill 的完整 cliSessionConfig
  const buildFinalConfig = useCallback((): CodingConfig => {
    const sessionConfig: Record<string, unknown> = {};

    // 模型配置 — 仅传递 modelProductId（产品 ID 字符串）
    const selectedModel = marketModels.find((m) => m.productId === config.modelProductId);
    if (selectedModel) {
      sessionConfig.modelProductId = selectedModel.productId;
    }

    // MCP 配置 — 仅传递 { productId, name }
    const selectedMcpEntries: McpServerEntry[] = (config.mcpServers ?? [])
      .map((id) => {
        const mcp = mcpServers.find((m) => m.productId === id);
        if (!mcp) return null;
        return { name: mcp.name, productId: mcp.productId };
      })
      .filter((e): e is McpServerEntry => e !== null);

    if (selectedMcpEntries.length > 0) {
      sessionConfig.mcpServers = selectedMcpEntries;
    }

    // Skill 配置 — 仅传递 { productId, name }
    const selectedSkillEntries: SkillEntry[] = (config.skills ?? [])
      .map((id) => {
        const skill = skills.find((s) => s.productId === id);
        if (!skill) return null;
        return { name: skill.name, productId: skill.productId };
      })
      .filter((e): e is SkillEntry => e !== null);

    if (selectedSkillEntries.length > 0) {
      sessionConfig.skills = selectedSkillEntries;
    }

    const hasConfig = Object.keys(sessionConfig).length > 0;
    return {
      ...config,
      cliRuntime: config.cliRuntime,
      cliSessionConfig: hasConfig ? JSON.stringify(sessionConfig) : undefined,
    };
  }, [config, mcpServers, skills, marketModels]);

  const handleApply = useCallback(() => {
    const finalConfig = buildFinalConfig();
    onConfigChange(finalConfig);
    // 只保存配置并关闭侧边栏，不触发连接
    // 连接将在用户发送第一条消息时由 Coding.tsx 的 handleWelcomeSend 触发
    onClose();
  }, [buildFinalConfig, onConfigChange, onClose]);

  // ============ 渲染 ============

  return (
    <Drawer
      closable={!isFirstTime}
      footer={
        <div className="flex justify-end">
          <Button
            disabled={!isComplete}
            icon={<Plug size={14} />}
            onClick={handleApply}
            type="primary"
          >
            {isFirstTime ? '开始使用' : '应用配置'}
          </Button>
        </div>
      }
      keyboard={!isFirstTime}
      maskClosable={!isFirstTime}
      onClose={onClose}
      open={open}
      placement="right"
      title="HiCoding 配置"
      width={400}
    >
      <div className="flex flex-col gap-6">
        {/* ===== 模型选择 ===== */}
        <section>
          <label className="text-sm font-medium text-gray-700 mb-2 block">
            模型 <span className="text-red-400">*</span>
          </label>
          {modelLoading ? (
            <div className="flex items-center gap-2 text-gray-400 text-sm py-2">
              <Loader2 className="animate-spin" size={16} />
              <span>加载中...</span>
            </div>
          ) : marketModels.length > 0 ? (
            <Select
              className="w-full"
              onChange={handleSelectModel}
              options={marketModels.map((m) => ({
                label: m.name,
                value: m.productId,
              }))}
              placeholder="选择模型"
              value={config.modelProductId ?? undefined}
            />
          ) : (
            <div className="text-sm text-gray-400">暂无可用模型</div>
          )}
        </section>

        <Divider className="my-0" />

        {/* ===== CLI 选择 ===== */}
        <section>
          <label className="text-sm font-medium text-gray-700 mb-2 block">
            CLI 工具 <span className="text-red-400">*</span>
          </label>
          {cliLoading ? (
            <div className="flex items-center gap-2 text-gray-400 text-sm py-2">
              <Loader2 className="animate-spin" size={16} />
              <span>加载中...</span>
            </div>
          ) : cliError ? (
            <div className="flex flex-col gap-2">
              <div className="flex items-center gap-2 text-red-500 text-sm">
                <AlertCircle size={16} />
                <span>{cliError}</span>
              </div>
              <Button icon={<RefreshCw size={14} />} onClick={fetchProviders} size="small">
                重试
              </Button>
            </div>
          ) : (
            <div className="flex flex-col gap-3">
              <div className="grid grid-cols-2 gap-2">
                {sortedProviders.map((p) => (
                  <SelectableCard
                    disabled={!p.available}
                    key={p.key}
                    onClick={() => handleSelectCli(p.key)}
                    selected={config.cliProviderId === p.key}
                  >
                    <div className="flex flex-col gap-0.5">
                      <span
                        className={`text-sm font-medium ${!p.available ? 'text-gray-400' : 'text-gray-800'}`}
                      >
                        {p.displayName}
                      </span>
                      {!p.available && <span className="text-xs text-gray-400">不可用</span>}
                    </div>
                  </SelectableCard>
                ))}
              </div>
            </div>
          )}
        </section>

        <Divider className="my-0" />

        {/* ===== Skill 选择 ===== */}
        <section>
          <label className="text-sm font-medium text-gray-700 mb-2 block">Skill</label>
          {skillLoading ? (
            <div className="flex items-center gap-2 text-gray-400 text-sm py-2">
              <Loader2 className="animate-spin" size={16} />
              <span>加载中...</span>
            </div>
          ) : skills.length === 0 ? (
            <div className="text-sm text-gray-400">暂无可用 Skill</div>
          ) : (
            <div className="grid grid-cols-2 gap-2 max-h-[200px] overflow-y-auto">
              {skills.map((skill) => (
                <SelectableCard
                  key={skill.productId}
                  onClick={() => handleToggleSkill(skill.productId)}
                  selected={(config.skills ?? []).includes(skill.productId)}
                >
                  <div className="flex flex-col gap-0.5">
                    <span className="text-sm font-medium text-gray-800">{skill.name}</span>
                    {skill.description && (
                      <span className="text-xs text-gray-400 line-clamp-2">
                        {skill.description}
                      </span>
                    )}
                  </div>
                </SelectableCard>
              ))}
            </div>
          )}
        </section>

        <Divider className="my-0" />

        {/* ===== MCP 选择 ===== */}
        <section>
          <label className="text-sm font-medium text-gray-700 mb-2 block">MCP Server</label>
          {mcpLoading ? (
            <div className="flex items-center gap-2 text-gray-400 text-sm py-2">
              <Loader2 className="animate-spin" size={16} />
              <span>加载中...</span>
            </div>
          ) : mcpServers.length === 0 ? (
            <div className="text-sm text-gray-400">暂无可用 MCP Server</div>
          ) : (
            <div className="grid grid-cols-2 gap-2 max-h-[200px] overflow-y-auto">
              {mcpServers.map((mcp) => (
                <SelectableCard
                  key={mcp.productId}
                  onClick={() => handleToggleMcp(mcp.productId)}
                  selected={(config.mcpServers ?? []).includes(mcp.productId)}
                >
                  <div className="flex flex-col gap-0.5">
                    <span className="text-sm font-medium text-gray-800">{mcp.name}</span>
                    {mcp.description && (
                      <span className="text-xs text-gray-400 line-clamp-2">{mcp.description}</span>
                    )}
                  </div>
                </SelectableCard>
              ))}
            </div>
          )}
        </section>
      </div>
    </Drawer>
  );
}
