import { Popover } from 'antd';
import { Sparkles, Zap, Wrench, Server, Check, Loader2, ChevronDown } from 'lucide-react';
import { useState, useEffect, useCallback, useMemo, useRef } from 'react';

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

import type { CodingConfig } from '../../types/coding';

interface ConfigDropdownsProps {
  config: CodingConfig;
  onConfigChange: (config: CodingConfig) => void;
  /** Hide the model dropdown (when rendered separately via ModelSelector) */
  hideModel?: boolean;
}

/* ── 下拉面板列表项 ── */
function DropdownItem({
  description,
  disabled,
  name,
  onClick,
  selected,
}: {
  name: string;
  description?: string;
  selected: boolean;
  disabled?: boolean;
  onClick: () => void;
}) {
  return (
    <button
      className={`flex items-center gap-2.5 px-3 py-2 mx-1 rounded-lg transition-colors border-0 w-full text-left
        ${
          disabled
            ? 'opacity-40 cursor-not-allowed'
            : selected
              ? 'bg-indigo-50/80 cursor-pointer'
              : 'hover:bg-gray-50 cursor-pointer'
        }`}
      disabled={disabled}
      onClick={disabled ? undefined : onClick}
      type="button"
    >
      {/* 选中指示器 */}
      <div
        className={`w-4 h-4 rounded-full border-2 flex items-center justify-center flex-shrink-0 transition-colors
        ${selected ? 'border-indigo-500 bg-indigo-500' : 'border-gray-300'}`}
      >
        {selected && <Check className="text-white" size={10} strokeWidth={3} />}
      </div>
      <div className="flex-1 min-w-0">
        <div
          className={`text-[13px] leading-tight ${selected ? 'font-medium text-gray-900' : 'text-gray-700'}`}
        >
          {name}
        </div>
        {description && (
          <div className="text-[11px] text-gray-400 line-clamp-1 mt-0.5">{description}</div>
        )}
        {disabled && !description && <div className="text-[11px] text-gray-400">不可用</div>}
      </div>
    </button>
  );
}

/* ── 多选面板列表项（Skill / MCP） ── */
function CheckItem({
  checked,
  description,
  name,
  onClick,
}: {
  name: string;
  description?: string;
  checked: boolean;
  onClick: () => void;
}) {
  return (
    <button
      className={`flex items-center gap-2.5 px-3 py-2 mx-1 rounded-lg cursor-pointer transition-colors border-0 w-full text-left
        ${checked ? 'bg-indigo-50/60' : 'hover:bg-gray-50'}`}
      onClick={onClick}
      type="button"
    >
      <div
        className={`w-4 h-4 rounded border-2 flex items-center justify-center flex-shrink-0 transition-colors
        ${checked ? 'border-indigo-500 bg-indigo-500' : 'border-gray-300'}`}
      >
        {checked && <Check className="text-white" size={10} strokeWidth={3} />}
      </div>
      <div className="flex-1 min-w-0">
        <div
          className={`text-[13px] leading-tight ${checked ? 'font-medium text-gray-900' : 'text-gray-700'}`}
        >
          {name}
        </div>
        {description && (
          <div className="text-[11px] text-gray-400 line-clamp-1 mt-0.5">{description}</div>
        )}
      </div>
    </button>
  );
}

/* ── 面板标题 ── */
function PanelHeader({ title }: { title: string }) {
  return (
    <div className="px-3 pt-2.5 pb-1.5">
      <span className="text-[11px] font-semibold text-gray-400 uppercase tracking-wider">
        {title}
      </span>
    </div>
  );
}

export function ConfigDropdowns({ config, hideModel, onConfigChange }: ConfigDropdownsProps) {
  const [providers, setProviders] = useState<ICliProvider[]>([]);
  const [marketModels, setMarketModels] = useState<MarketModelInfo[]>([]);
  const [mcpServers, setMcpServers] = useState<MarketMcpInfo[]>([]);
  const [skills, setSkills] = useState<MarketSkillInfo[]>([]);

  const [cliLoading, setCliLoading] = useState(true);
  const [modelLoading, setModelLoading] = useState(true);
  const [mcpLoading, setMcpLoading] = useState(true);
  const [skillLoading, setSkillLoading] = useState(true);

  const [modelOpen, setModelOpen] = useState(false);
  const [cliOpen, setCliOpen] = useState(false);
  const [skillOpen, setSkillOpen] = useState(false);
  const [mcpOpen, setMcpOpen] = useState(false);

  const sortedProviders = useMemo(() => sortCliProviders(providers), [providers]);
  const dataLoadedRef = useRef(false);

  const closeOthers = useCallback((except: string) => {
    if (except !== 'model') setModelOpen(false);
    if (except !== 'cli') setCliOpen(false);
    if (except !== 'skill') setSkillOpen(false);
    if (except !== 'mcp') setMcpOpen(false);
  }, []);

  const buildSessionConfig = useCallback(
    (cfg: CodingConfig): string | undefined => {
      const sc: Record<string, unknown> = {};
      const model = marketModels.find((m) => m.productId === cfg.modelProductId);
      if (model) sc.modelProductId = model.productId;

      const mcpEntries: McpServerEntry[] = (cfg.mcpServers ?? [])
        .map((id) => {
          const mcp = mcpServers.find((m) => m.productId === id);
          return mcp ? { name: mcp.name, productId: mcp.productId } : null;
        })
        .filter((e): e is McpServerEntry => e !== null);
      if (mcpEntries.length > 0) sc.mcpServers = mcpEntries;

      const skillEntries: SkillEntry[] = (cfg.skills ?? [])
        .map((id) => {
          const skill = skills.find((s) => s.productId === id);
          return skill ? { name: skill.name, productId: skill.productId } : null;
        })
        .filter((e): e is SkillEntry => e !== null);
      if (skillEntries.length > 0) sc.skills = skillEntries;

      return Object.keys(sc).length > 0 ? JSON.stringify(sc) : undefined;
    },
    [marketModels, mcpServers, skills],
  );

  useEffect(() => {
    if (dataLoadedRef.current) return;
    dataLoadedRef.current = true;

    getCliProviders()
      .then((res) => {
        const list = Array.isArray(res.data)
          ? res.data
          : (((
              (res as unknown as Record<string, unknown>).data as
                | Record<string, unknown>
                | undefined
            )?.data ?? []) as ICliProvider[]);
        setProviders(list);
      })
      .catch(() => {})
      .finally(() => setCliLoading(false));

    getMarketModels()
      .then((res) => setMarketModels(res.data.models ?? []))
      .catch(() => {})
      .finally(() => setModelLoading(false));

    getMarketMcps()
      .then((res) => setMcpServers(res.data.mcpServers ?? []))
      .catch(() => {})
      .finally(() => setMcpLoading(false));

    getMarketSkills()
      .then((res) => setSkills(Array.isArray(res.data) ? res.data : []))
      .catch(() => {})
      .finally(() => setSkillLoading(false));
  }, []);

  // --- Selection handlers ---

  const handleSelectModel = useCallback(
    (productId: string) => {
      const model = marketModels.find((m) => m.productId === productId);
      const next = { ...config, modelName: model?.name ?? null, modelProductId: productId };
      next.cliSessionConfig = buildSessionConfig(next);
      onConfigChange(next);
      setModelOpen(false);
    },
    [config, onConfigChange, marketModels, buildSessionConfig],
  );

  const handleSelectCli = useCallback(
    (key: string) => {
      const provider = sortedProviders.find((p) => p.key === key);
      if (!provider?.available) return;
      const next = { ...config, cliProviderId: key, cliProviderName: provider.displayName };
      next.cliSessionConfig = buildSessionConfig(next);
      onConfigChange(next);
      setCliOpen(false);
    },
    [config, onConfigChange, sortedProviders, buildSessionConfig],
  );

  const handleToggleMcp = useCallback(
    (productId: string) => {
      const current = config.mcpServers ?? [];
      const nextMcps = current.includes(productId)
        ? current.filter((id) => id !== productId)
        : [...current, productId];
      const next = { ...config, mcpServers: nextMcps };
      next.cliSessionConfig = buildSessionConfig(next);
      onConfigChange(next);
    },
    [config, onConfigChange, buildSessionConfig],
  );

  const handleToggleSkill = useCallback(
    (productId: string) => {
      const current = config.skills ?? [];
      const nextSkills = current.includes(productId)
        ? current.filter((id) => id !== productId)
        : [...current, productId];
      const next = { ...config, skills: nextSkills };
      next.cliSessionConfig = buildSessionConfig(next);
      onConfigChange(next);
    },
    [config, onConfigChange, buildSessionConfig],
  );

  const selectedSkillCount = (config.skills ?? []).length;
  const selectedMcpCount = (config.mcpServers ?? []).length;

  const loadingPanel = (
    <div className="w-[260px] flex items-center justify-center gap-2 py-8 text-gray-400 text-sm">
      <Loader2 className="animate-spin" size={14} />
      <span>加载中...</span>
    </div>
  );

  const emptyPanel = (text: string) => (
    <div className="w-[260px] text-center py-8 text-[13px] text-gray-400">{text}</div>
  );

  return (
    <div className="flex items-center gap-1.5">
      {/* ── Model ── */}
      {!hideModel && (
        <Popover
          arrow={false}
          content={
            modelLoading ? (
              loadingPanel
            ) : marketModels.length === 0 ? (
              emptyPanel('暂无可用模型')
            ) : (
              <div className="w-[260px] max-h-[300px] overflow-y-auto pb-1">
                <PanelHeader title="Model" />
                {marketModels.map((m) => (
                  <DropdownItem
                    description={m.description}
                    key={m.productId}
                    name={m.name}
                    onClick={() => handleSelectModel(m.productId)}
                    selected={config.modelProductId === m.productId}
                  />
                ))}
              </div>
            )
          }
          onOpenChange={(v) => {
            setModelOpen(v);
            if (v) closeOthers('model');
          }}
          open={modelOpen}
          overlayClassName="config-dropdown-overlay"
          overlayInnerStyle={{ borderRadius: 12, padding: 0 }}
          placement="bottomLeft"
          trigger="click"
        >
          <button
            className="inline-flex items-center gap-1 pl-2 pr-1.5 py-1 rounded-lg
                       border border-gray-200/80 bg-white/90 text-gray-600
                       text-[11px] font-medium cursor-pointer
                       hover:border-gray-300 hover:bg-white transition-all"
          >
            <Sparkles className="text-indigo-400 flex-shrink-0" size={12} />
            <span className="max-w-[88px] truncate">{config.modelName || 'Model'}</span>
            <ChevronDown className="text-gray-400 flex-shrink-0" size={11} />
          </button>
        </Popover>
      )}

      {/* ── CLI ── */}
      <Popover
        arrow={false}
        content={
          cliLoading ? (
            loadingPanel
          ) : sortedProviders.length === 0 ? (
            emptyPanel('暂无可用 CLI')
          ) : (
            <div className="w-[260px] max-h-[300px] overflow-y-auto pb-1">
              <PanelHeader title="CLI" />
              {sortedProviders.map((p) => (
                <DropdownItem
                  disabled={!p.available}
                  key={p.key}
                  name={p.displayName}
                  onClick={() => handleSelectCli(p.key)}
                  selected={config.cliProviderId === p.key}
                />
              ))}
            </div>
          )
        }
        onOpenChange={(v) => {
          setCliOpen(v);
          if (v) closeOthers('cli');
        }}
        open={cliOpen}
        overlayClassName="config-dropdown-overlay"
        overlayInnerStyle={{ borderRadius: 12, padding: 0 }}
        placement="bottomLeft"
        trigger="click"
      >
        <button
          className="inline-flex items-center gap-1 pl-2 pr-1.5 py-1 rounded-lg
                     border border-gray-200/80 bg-white/90 text-gray-600
                     text-[11px] font-medium cursor-pointer
                     hover:border-gray-300 hover:bg-white transition-all"
        >
          <Zap className="text-violet-400 flex-shrink-0" size={12} />
          <span className="max-w-[88px] truncate">{config.cliProviderName || 'CLI'}</span>
          <ChevronDown className="text-gray-400 flex-shrink-0" size={11} />
        </button>
      </Popover>

      {/* ── Skill ── */}
      <Popover
        arrow={false}
        content={
          skillLoading ? (
            loadingPanel
          ) : skills.length === 0 ? (
            emptyPanel('暂无可用 Skill')
          ) : (
            <div className="w-[260px] max-h-[300px] overflow-y-auto pb-1">
              <PanelHeader title="Skill" />
              {skills.map((s) => (
                <CheckItem
                  checked={(config.skills ?? []).includes(s.productId)}
                  description={s.description}
                  key={s.productId}
                  name={s.name}
                  onClick={() => handleToggleSkill(s.productId)}
                />
              ))}
            </div>
          )
        }
        onOpenChange={(v) => {
          setSkillOpen(v);
          if (v) closeOthers('skill');
        }}
        open={skillOpen}
        overlayClassName="config-dropdown-overlay"
        overlayInnerStyle={{ borderRadius: 12, padding: 0 }}
        placement="bottomLeft"
        trigger="click"
      >
        <button
          className="inline-flex items-center gap-1 pl-2 pr-1.5 py-1 rounded-lg
                     border border-gray-200/80 bg-white/90 text-gray-600
                     text-[11px] font-medium cursor-pointer
                     hover:border-gray-300 hover:bg-white transition-all"
        >
          <Wrench className="text-emerald-400 flex-shrink-0" size={12} />
          <span>Skill</span>
          {selectedSkillCount > 0 && (
            <span
              className="min-w-[16px] h-4 flex items-center justify-center rounded-full
                             bg-indigo-100 text-indigo-600 text-[10px] font-semibold px-1"
            >
              {selectedSkillCount}
            </span>
          )}
          <ChevronDown className="text-gray-400 flex-shrink-0" size={11} />
        </button>
      </Popover>

      {/* ── MCP ── */}
      <Popover
        arrow={false}
        content={
          mcpLoading ? (
            loadingPanel
          ) : mcpServers.length === 0 ? (
            emptyPanel('暂无可用 MCP Server')
          ) : (
            <div className="w-[260px] max-h-[300px] overflow-y-auto pb-1">
              <PanelHeader title="MCP Server" />
              {mcpServers.map((m) => (
                <CheckItem
                  checked={(config.mcpServers ?? []).includes(m.productId)}
                  description={m.description}
                  key={m.productId}
                  name={m.name}
                  onClick={() => handleToggleMcp(m.productId)}
                />
              ))}
            </div>
          )
        }
        onOpenChange={(v) => {
          setMcpOpen(v);
          if (v) closeOthers('mcp');
        }}
        open={mcpOpen}
        overlayClassName="config-dropdown-overlay"
        overlayInnerStyle={{ borderRadius: 12, padding: 0 }}
        placement="bottomLeft"
        trigger="click"
      >
        <button
          className="inline-flex items-center gap-1 pl-2 pr-1.5 py-1 rounded-lg
                     border border-gray-200/80 bg-white/90 text-gray-600
                     text-[11px] font-medium cursor-pointer
                     hover:border-gray-300 hover:bg-white transition-all"
        >
          <Server className="text-amber-400 flex-shrink-0" size={12} />
          <span>MCP</span>
          {selectedMcpCount > 0 && (
            <span
              className="min-w-[16px] h-4 flex items-center justify-center rounded-full
                             bg-indigo-100 text-indigo-600 text-[10px] font-semibold px-1"
            >
              {selectedMcpCount}
            </span>
          )}
          <ChevronDown className="text-gray-400 flex-shrink-0" size={11} />
        </button>
      </Popover>
    </div>
  );
}

/* ── Standalone Model Selector (for top-left of dialog) ── */

interface ModelSelectorProps {
  config: CodingConfig;
  onConfigChange: (config: CodingConfig) => void;
}

export function ModelSelector({ config, onConfigChange }: ModelSelectorProps) {
  const [marketModels, setMarketModels] = useState<MarketModelInfo[]>([]);
  const [loading, setLoading] = useState(true);
  const [open, setOpen] = useState(false);
  const dataLoadedRef = useRef(false);

  useEffect(() => {
    if (dataLoadedRef.current) return;
    dataLoadedRef.current = true;
    getMarketModels()
      .then((res) => setMarketModels(res.data.models ?? []))
      .catch(() => {})
      .finally(() => setLoading(false));
  }, []);

  const handleSelect = useCallback(
    (productId: string) => {
      const model = marketModels.find((m) => m.productId === productId);
      const next = { ...config, modelName: model?.name ?? null, modelProductId: productId };
      // Rebuild cliSessionConfig with new model
      const sc: Record<string, unknown> = {};
      if (next.modelProductId) sc.modelProductId = next.modelProductId;
      next.cliSessionConfig = Object.keys(sc).length > 0 ? JSON.stringify(sc) : undefined;
      onConfigChange(next);
      setOpen(false);
    },
    [config, onConfigChange, marketModels],
  );

  const loadingPanel = (
    <div className="w-[280px] flex items-center justify-center gap-2 py-8 text-gray-400 text-sm">
      <Loader2 className="animate-spin" size={14} />
      <span>加载中...</span>
    </div>
  );

  return (
    <Popover
      arrow={false}
      content={
        loading ? (
          loadingPanel
        ) : marketModels.length === 0 ? (
          <div className="w-[280px] text-center py-8 text-[13px] text-gray-400">暂无可用模型</div>
        ) : (
          <div className="w-[280px] max-h-[300px] overflow-y-auto pb-1">
            <PanelHeader title="Model" />
            {marketModels.map((m) => (
              <DropdownItem
                description={m.description}
                key={m.productId}
                name={m.name}
                onClick={() => handleSelect(m.productId)}
                selected={config.modelProductId === m.productId}
              />
            ))}
          </div>
        )
      }
      onOpenChange={setOpen}
      open={open}
      overlayClassName="config-dropdown-overlay"
      overlayInnerStyle={{ borderRadius: 12, padding: 0 }}
      placement="bottomLeft"
      trigger="click"
    >
      <button
        className="inline-flex items-center gap-1.5 px-2 py-1 rounded-lg
                   text-gray-500 text-[13px] cursor-pointer
                   hover:bg-gray-50 transition-all"
      >
        <Sparkles className="text-indigo-400 flex-shrink-0" size={13} />
        <span className="text-gray-700 font-medium">{config.modelName || '选择模型'}</span>
        <ChevronDown className="text-gray-400 flex-shrink-0" size={12} />
      </button>
    </Popover>
  );
}
