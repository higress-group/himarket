import { useState, useEffect, useCallback, useMemo, useRef } from "react";
import { Popover } from "antd";
import { Sparkles, Zap, Wrench, Server, Check, Loader2, ChevronDown } from "lucide-react";
import type { CodingConfig } from "../../types/coding";
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
} from "../../lib/apis/cliProvider";
import { sortCliProviders } from "../../lib/utils/cliProviderSort";

interface ConfigDropdownsProps {
  config: CodingConfig;
  onConfigChange: (config: CodingConfig) => void;
  /** Hide the model dropdown (when rendered separately via ModelSelector) */
  hideModel?: boolean;
}

/* ── 下拉面板列表项 ── */
function DropdownItem({
  name,
  description,
  selected,
  disabled,
  onClick,
}: {
  name: string;
  description?: string;
  selected: boolean;
  disabled?: boolean;
  onClick: () => void;
}) {
  return (
    <div
      className={`flex items-center gap-2.5 px-3 py-2 mx-1 rounded-lg transition-colors
        ${disabled
          ? "opacity-40 cursor-not-allowed"
          : selected
            ? "bg-indigo-50/80 cursor-pointer"
            : "hover:bg-gray-50 cursor-pointer"
        }`}
      onClick={disabled ? undefined : onClick}
    >
      {/* 选中指示器 */}
      <div className={`w-4 h-4 rounded-full border-2 flex items-center justify-center flex-shrink-0 transition-colors
        ${selected ? "border-indigo-500 bg-indigo-500" : "border-gray-300"}`}
      >
        {selected && <Check size={10} className="text-white" strokeWidth={3} />}
      </div>
      <div className="flex-1 min-w-0">
        <div className={`text-[13px] leading-tight ${selected ? "font-medium text-gray-900" : "text-gray-700"}`}>
          {name}
        </div>
        {description && (
          <div className="text-[11px] text-gray-400 line-clamp-1 mt-0.5">{description}</div>
        )}
        {disabled && !description && (
          <div className="text-[11px] text-gray-400">不可用</div>
        )}
      </div>
    </div>
  );
}

/* ── 多选面板列表项（Skill / MCP） ── */
function CheckItem({
  name,
  description,
  checked,
  onClick,
}: {
  name: string;
  description?: string;
  checked: boolean;
  onClick: () => void;
}) {
  return (
    <div
      className={`flex items-center gap-2.5 px-3 py-2 mx-1 rounded-lg cursor-pointer transition-colors
        ${checked ? "bg-indigo-50/60" : "hover:bg-gray-50"}`}
      onClick={onClick}
    >
      <div className={`w-4 h-4 rounded border-2 flex items-center justify-center flex-shrink-0 transition-colors
        ${checked ? "border-indigo-500 bg-indigo-500" : "border-gray-300"}`}
      >
        {checked && <Check size={10} className="text-white" strokeWidth={3} />}
      </div>
      <div className="flex-1 min-w-0">
        <div className={`text-[13px] leading-tight ${checked ? "font-medium text-gray-900" : "text-gray-700"}`}>
          {name}
        </div>
        {description && (
          <div className="text-[11px] text-gray-400 line-clamp-1 mt-0.5">{description}</div>
        )}
      </div>
    </div>
  );
}

/* ── 面板标题 ── */
function PanelHeader({ title }: { title: string }) {
  return (
    <div className="px-3 pt-2.5 pb-1.5">
      <span className="text-[11px] font-semibold text-gray-400 uppercase tracking-wider">{title}</span>
    </div>
  );
}

export function ConfigDropdowns({ config, onConfigChange, hideModel }: ConfigDropdownsProps) {
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
    if (except !== "model") setModelOpen(false);
    if (except !== "cli") setCliOpen(false);
    if (except !== "skill") setSkillOpen(false);
    if (except !== "mcp") setMcpOpen(false);
  }, []);

  const buildSessionConfig = useCallback(
    (cfg: CodingConfig): string | undefined => {
      const sc: Record<string, unknown> = {};
      const model = marketModels.find((m) => m.productId === cfg.modelProductId);
      if (model) sc.modelProductId = model.productId;

      const mcpEntries: McpServerEntry[] = (cfg.mcpServers ?? [])
        .map((id) => {
          const mcp = mcpServers.find((m) => m.productId === id);
          return mcp ? { productId: mcp.productId, name: mcp.name } : null;
        })
        .filter((e): e is McpServerEntry => e !== null);
      if (mcpEntries.length > 0) sc.mcpServers = mcpEntries;

      const skillEntries: SkillEntry[] = (cfg.skills ?? [])
        .map((id) => {
          const skill = skills.find((s) => s.productId === id);
          return skill ? { productId: skill.productId, name: skill.name } : null;
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
          : ((res as any).data?.data ?? []);
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
      const next = { ...config, modelProductId: productId, modelName: model?.name ?? null };
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
      <Loader2 size={14} className="animate-spin" />
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
          trigger="click"
          open={modelOpen}
          onOpenChange={(v) => { setModelOpen(v); if (v) closeOthers("model"); }}
          placement="bottomLeft"
          arrow={false}
          overlayInnerStyle={{ padding: 0, borderRadius: 12 }}
          overlayClassName="config-dropdown-overlay"
          content={
            modelLoading ? loadingPanel : marketModels.length === 0
              ? emptyPanel("暂无可用模型")
              : (
                <div className="w-[260px] max-h-[300px] overflow-y-auto pb-1">
                  <PanelHeader title="Model" />
                  {marketModels.map((m) => (
                    <DropdownItem
                      key={m.productId}
                      name={m.name}
                      description={m.description}
                      selected={config.modelProductId === m.productId}
                      onClick={() => handleSelectModel(m.productId)}
                    />
                  ))}
                </div>
              )
          }
        >
          <button
            className="inline-flex items-center gap-1 pl-2 pr-1.5 py-1 rounded-lg
                       border border-gray-200/80 bg-white/90 text-gray-600
                       text-[11px] font-medium cursor-pointer
                       hover:border-gray-300 hover:bg-white transition-all"
          >
            <Sparkles size={12} className="text-indigo-400 flex-shrink-0" />
            <span className="max-w-[88px] truncate">{config.modelName || "Model"}</span>
            <ChevronDown size={11} className="text-gray-400 flex-shrink-0" />
          </button>
        </Popover>
      )}

      {/* ── CLI ── */}
      <Popover
        trigger="click"
        open={cliOpen}
        onOpenChange={(v) => { setCliOpen(v); if (v) closeOthers("cli"); }}
        placement="bottomLeft"
        arrow={false}
        overlayInnerStyle={{ padding: 0, borderRadius: 12 }}
        overlayClassName="config-dropdown-overlay"
        content={
          cliLoading ? loadingPanel : sortedProviders.length === 0
            ? emptyPanel("暂无可用 CLI")
            : (
              <div className="w-[260px] max-h-[300px] overflow-y-auto pb-1">
                <PanelHeader title="CLI" />
                {sortedProviders.map((p) => (
                  <DropdownItem
                    key={p.key}
                    name={p.displayName}
                    selected={config.cliProviderId === p.key}
                    disabled={!p.available}
                    onClick={() => handleSelectCli(p.key)}
                  />
                ))}
              </div>
            )
        }
      >
        <button
          className="inline-flex items-center gap-1 pl-2 pr-1.5 py-1 rounded-lg
                     border border-gray-200/80 bg-white/90 text-gray-600
                     text-[11px] font-medium cursor-pointer
                     hover:border-gray-300 hover:bg-white transition-all"
        >
          <Zap size={12} className="text-violet-400 flex-shrink-0" />
          <span className="max-w-[88px] truncate">{config.cliProviderName || "CLI"}</span>
          <ChevronDown size={11} className="text-gray-400 flex-shrink-0" />
        </button>
      </Popover>

      {/* ── Skill ── */}
      <Popover
        trigger="click"
        open={skillOpen}
        onOpenChange={(v) => { setSkillOpen(v); if (v) closeOthers("skill"); }}
        placement="bottomLeft"
        arrow={false}
        overlayInnerStyle={{ padding: 0, borderRadius: 12 }}
        overlayClassName="config-dropdown-overlay"
        content={
          skillLoading ? loadingPanel : skills.length === 0
            ? emptyPanel("暂无可用 Skill")
            : (
              <div className="w-[260px] max-h-[300px] overflow-y-auto pb-1">
                <PanelHeader title="Skill" />
                {skills.map((s) => (
                  <CheckItem
                    key={s.productId}
                    name={s.name}
                    description={s.description}
                    checked={(config.skills ?? []).includes(s.productId)}
                    onClick={() => handleToggleSkill(s.productId)}
                  />
                ))}
              </div>
            )
        }
      >
        <button
          className="inline-flex items-center gap-1 pl-2 pr-1.5 py-1 rounded-lg
                     border border-gray-200/80 bg-white/90 text-gray-600
                     text-[11px] font-medium cursor-pointer
                     hover:border-gray-300 hover:bg-white transition-all"
        >
          <Wrench size={12} className="text-emerald-400 flex-shrink-0" />
          <span>Skill</span>
          {selectedSkillCount > 0 && (
            <span className="min-w-[16px] h-4 flex items-center justify-center rounded-full
                             bg-indigo-100 text-indigo-600 text-[10px] font-semibold px-1">
              {selectedSkillCount}
            </span>
          )}
          <ChevronDown size={11} className="text-gray-400 flex-shrink-0" />
        </button>
      </Popover>

      {/* ── MCP ── */}
      <Popover
        trigger="click"
        open={mcpOpen}
        onOpenChange={(v) => { setMcpOpen(v); if (v) closeOthers("mcp"); }}
        placement="bottomLeft"
        arrow={false}
        overlayInnerStyle={{ padding: 0, borderRadius: 12 }}
        overlayClassName="config-dropdown-overlay"
        content={
          mcpLoading ? loadingPanel : mcpServers.length === 0
            ? emptyPanel("暂无可用 MCP Server")
            : (
              <div className="w-[260px] max-h-[300px] overflow-y-auto pb-1">
                <PanelHeader title="MCP Server" />
                {mcpServers.map((m) => (
                  <CheckItem
                    key={m.productId}
                    name={m.name}
                    description={m.description}
                    checked={(config.mcpServers ?? []).includes(m.productId)}
                    onClick={() => handleToggleMcp(m.productId)}
                  />
                ))}
              </div>
            )
        }
      >
        <button
          className="inline-flex items-center gap-1 pl-2 pr-1.5 py-1 rounded-lg
                     border border-gray-200/80 bg-white/90 text-gray-600
                     text-[11px] font-medium cursor-pointer
                     hover:border-gray-300 hover:bg-white transition-all"
        >
          <Server size={12} className="text-amber-400 flex-shrink-0" />
          <span>MCP</span>
          {selectedMcpCount > 0 && (
            <span className="min-w-[16px] h-4 flex items-center justify-center rounded-full
                             bg-indigo-100 text-indigo-600 text-[10px] font-semibold px-1">
              {selectedMcpCount}
            </span>
          )}
          <ChevronDown size={11} className="text-gray-400 flex-shrink-0" />
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
      const next = { ...config, modelProductId: productId, modelName: model?.name ?? null };
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
      <Loader2 size={14} className="animate-spin" />
      <span>加载中...</span>
    </div>
  );

  return (
    <Popover
      trigger="click"
      open={open}
      onOpenChange={setOpen}
      placement="bottomLeft"
      arrow={false}
      overlayInnerStyle={{ padding: 0, borderRadius: 12 }}
      overlayClassName="config-dropdown-overlay"
      content={
        loading ? loadingPanel : marketModels.length === 0
          ? <div className="w-[280px] text-center py-8 text-[13px] text-gray-400">暂无可用模型</div>
          : (
            <div className="w-[280px] max-h-[300px] overflow-y-auto pb-1">
              <PanelHeader title="Model" />
              {marketModels.map((m) => (
                <DropdownItem
                  key={m.productId}
                  name={m.name}
                  description={m.description}
                  selected={config.modelProductId === m.productId}
                  onClick={() => handleSelect(m.productId)}
                />
              ))}
            </div>
          )
      }
    >
      <button
        className="inline-flex items-center gap-1.5 px-2 py-1 rounded-lg
                   text-gray-500 text-[13px] cursor-pointer
                   hover:bg-gray-50 transition-all"
      >
        <Sparkles size={13} className="text-indigo-400 flex-shrink-0" />
        <span className="text-gray-700 font-medium">{config.modelName || "选择模型"}</span>
        <ChevronDown size={12} className="text-gray-400 flex-shrink-0" />
      </button>
    </Popover>
  );
}
