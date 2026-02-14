import { useEffect, useState } from "react";
import { getCliProviders, type ICliProvider } from "../../lib/apis/cliProvider";

interface CliProviderSelectProps {
  value: string;
  onChange: (providerKey: string) => void;
}

export function CliProviderSelect({ value, onChange }: CliProviderSelectProps) {
  const [providers, setProviders] = useState<ICliProvider[]>([]);

  useEffect(() => {
    getCliProviders()
      .then(res => {
        const list = Array.isArray(res.data) ? res.data : (res as any).data?.data ?? [];
        setProviders(list);
        // 如果当前没有选中值，自动选中默认且可用的 provider
        if (!value && list.length > 0) {
          const def =
            list.find((p: ICliProvider) => p.isDefault && p.available) ??
            list.find((p: ICliProvider) => p.available) ??
            list[0];
          onChange(def.key);
        }
        // 如果当前选中的 provider 不可用，自动切换到第一个可用的
        if (value) {
          const current = list.find((p: ICliProvider) => p.key === value);
          if (current && !current.available) {
            const fallback = list.find((p: ICliProvider) => p.available);
            if (fallback) onChange(fallback.key);
          }
        }
      })
      .catch(() => {});
  }, []);

  if (providers.length <= 1) return null;

  return (
    <select
      className="text-xs border border-gray-200 rounded-md px-2 py-1 bg-white/80
                 text-gray-600 outline-none focus:border-gray-400 transition-colors"
      value={value}
      onChange={e => onChange(e.target.value)}
      title="切换 CLI Agent"
    >
      {providers.map(p => (
        <option key={p.key} value={p.key} disabled={!p.available}>
          {p.displayName}{!p.available ? " (不可用)" : ""}
        </option>
      ))}
    </select>
  );
}
