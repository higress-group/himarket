import { Select } from 'antd';
import { useEffect, useState } from 'react';

import { getCliProviders, type ICliProvider } from '@/lib/apis';

interface CliProviderSelectProps {
  value: string;
  onChange: (providerKey: string, providerObj?: ICliProvider) => void;
}

export function CliProviderSelect({ onChange, value }: CliProviderSelectProps) {
  const [providers, setProviders] = useState<ICliProvider[]>([]);

  useEffect(() => {
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
        // 如果当前没有选中值，自动选中默认且可用的 provider
        if (!value && list.length > 0) {
          const def =
            list.find((p: ICliProvider) => p.isDefault && p.available) ??
            list.find((p: ICliProvider) => p.available) ??
            list[0];
          if (def) {
            onChange(def.key, def);
          }
        }
        // 如果当前选中的 provider 不可用，自动切换到第一个可用的
        if (value) {
          const current = list.find((p: ICliProvider) => p.key === value);
          if (current && !current.available) {
            const fallback = list.find((p: ICliProvider) => p.available);
            if (fallback) onChange(fallback.key, fallback);
          }
          // 通知父组件当前 provider 对象（用于运行时选择）
          if (current) {
            onChange(current.key, current);
          }
        }
      })
      .catch(() => {});
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  if (providers.length <= 1) return null;

  return (
    <Select
      className="min-w-[100px]"
      onChange={(val) => {
        const providerObj = providers.find((p) => p.key === val);
        onChange(val, providerObj);
      }}
      options={providers.map((p) => ({
        disabled: !p.available,
        label: p.displayName + (!p.available ? ' (不可用)' : ''),
        value: p.key,
      }))}
      placement="bottomLeft"
      size="small"
      title="切换 CLI Agent"
      value={value}
      variant="outlined"
    />
  );
}
