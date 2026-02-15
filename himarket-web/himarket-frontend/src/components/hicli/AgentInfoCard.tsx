import { Info, Shield, Layers, Cpu, Terminal, Bot } from "lucide-react";
import { useHiCliState } from "../../context/HiCliSessionContext";

/**
 * Agent 信息卡片 —— 展示当前连接 Agent 的元数据。
 * 数据来源：useHiCliState（agentInfo / authMethods / agentCapabilities / modesSource）
 *           以及 QuestState 上的 modes / models / commands。
 */
export function AgentInfoCard() {
  const state = useHiCliState();
  const { agentInfo, authMethods, agentCapabilities, modesSource, modes, models, commands } = state;

  return (
    <div className="flex flex-col gap-3 p-3 overflow-y-auto h-full text-sm">
      {/* Agent 基本信息 */}
      <Section title="Agent 信息" icon={<Bot size={14} />} source="initialize">
        {agentInfo ? (
          <div className="grid grid-cols-[auto_1fr] gap-x-4 gap-y-1.5">
            <Field label="name" value={agentInfo.name} />
            <Field label="title" value={agentInfo.title} />
            <Field label="version" value={agentInfo.version} />
          </div>
        ) : (
          <Empty />
        )}
      </Section>

      {/* 认证方式 */}
      <Section title="认证方式" icon={<Shield size={14} />} source="initialize">
        {authMethods.length > 0 ? (
          <div className="flex flex-col gap-2">
            {authMethods.map((m) => (
              <div
                key={m.id}
                className="rounded-md border border-gray-200/60 bg-white/40 p-2"
              >
                <div className="flex items-center gap-2">
                  <span className="text-xs font-mono text-gray-500">{m.id}</span>
                  {m.type && (
                    <span className="text-[10px] px-1.5 py-0.5 rounded bg-blue-50 text-blue-600 font-medium">
                      {m.type}
                    </span>
                  )}
                </div>
                <div className="text-xs font-medium text-gray-700 mt-1">{m.name}</div>
                {m.description && (
                  <div className="text-[11px] text-gray-400 mt-0.5">{m.description}</div>
                )}
                {m.args && m.args.length > 0 && (
                  <code className="block mt-1 text-[11px] text-gray-500 font-mono bg-gray-50 rounded px-1.5 py-0.5">
                    {m.args.join(" ")}
                  </code>
                )}
              </div>
            ))}
          </div>
        ) : (
          <Empty />
        )}
      </Section>

      {/* Modes */}
      <Section
        title="Modes"
        icon={<Layers size={14} />}
        source={modesSource === "session_new" ? "session/new" : modesSource ?? undefined}
      >
        {modes.length > 0 ? (
          <div className="flex flex-col gap-1.5">
            {modes.map((m) => (
              <div key={m.id} className="flex items-baseline gap-2 text-xs">
                <span className="font-mono text-gray-500 shrink-0">{m.id}</span>
                <span className="font-medium text-gray-700">{m.name}</span>
                {m.description && (
                  <span className="text-gray-400 truncate">{m.description}</span>
                )}
              </div>
            ))}
          </div>
        ) : (
          <Empty />
        )}
      </Section>

      {/* Agent 能力配置 */}
      <Section title="Agent 能力配置" icon={<Cpu size={14} />} source="initialize">
        {agentCapabilities ? (
          <pre
            className="text-[11px] leading-relaxed font-mono bg-gray-50 rounded-md
                       border border-gray-200/60 p-2 overflow-x-auto text-gray-700
                       whitespace-pre-wrap break-all"
          >
            {JSON.stringify(agentCapabilities, null, 2)}
          </pre>
        ) : (
          <Empty />
        )}
      </Section>

      {/* Models */}
      <Section title="Models" icon={<Info size={14} />} source="session/new">
        {models.length > 0 ? (
          <div className="flex flex-col gap-1.5">
            {models.map((m) => (
              <div key={m.modelId} className="flex items-baseline gap-2 text-xs">
                <span className="font-mono text-gray-500 shrink-0">{m.modelId}</span>
                <span className="font-medium text-gray-700">{m.name}</span>
              </div>
            ))}
          </div>
        ) : (
          <Empty />
        )}
      </Section>

      {/* Slash Commands */}
      <Section title="Slash Commands" icon={<Terminal size={14} />} source="session/update">
        {commands.length > 0 ? (
          <div className="flex flex-col gap-1.5">
            {commands.map((c) => (
              <div key={c.name} className="flex items-baseline gap-2 text-xs">
                <span className="font-mono text-blue-600 shrink-0">/{c.name}</span>
                <span className="text-gray-500">{c.description}</span>
              </div>
            ))}
          </div>
        ) : (
          <Empty />
        )}
      </Section>
    </div>
  );
}

/** 信息分区 */
function Section({
  title,
  icon,
  source,
  children,
}: {
  title: string;
  icon?: React.ReactNode;
  source?: string;
  children: React.ReactNode;
}) {
  return (
    <div className="rounded-lg border border-gray-200/60 bg-white/60">
      <div className="flex items-center gap-1.5 px-3 py-2 border-b border-gray-100">
        {icon && <span className="text-gray-400">{icon}</span>}
        <span className="text-xs font-semibold text-gray-600">{title}</span>
        {source && (
          <span className="ml-auto text-[10px] px-1.5 py-0.5 rounded bg-gray-100 text-gray-400 font-mono">
            {source}
          </span>
        )}
      </div>
      <div className="px-3 py-2">{children}</div>
    </div>
  );
}

/** 字段行 */
function Field({ label, value }: { label: string; value?: string }) {
  return (
    <>
      <span className="text-xs text-gray-400 font-mono">{label}</span>
      <span className="text-xs text-gray-700">{value ?? "未提供"}</span>
    </>
  );
}

/** 空占位 */
function Empty() {
  return <div className="text-xs text-gray-400">未提供</div>;
}
