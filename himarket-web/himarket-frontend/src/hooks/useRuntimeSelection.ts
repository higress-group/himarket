import { useState, useEffect, useCallback, useMemo } from 'react';
import type { RuntimeType } from '../types/runtime';
import type { RuntimeOption } from '../components/common/RuntimeSelector';
import type { ICliProvider } from '../lib/apis/cliProvider';
import { getAvailableRuntimes, type IRuntimeAvailability } from '../lib/apis/runtime';

/**
 * 默认运行时方案定义（前端静态配置）
 * 后端 /api/runtime/available 接口可覆盖 available 状态
 */
const DEFAULT_RUNTIME_OPTIONS: Record<RuntimeType, Omit<RuntimeOption, 'available' | 'unavailableReason'>> = {
  k8s: {
    type: 'k8s',
    label: 'K8s 沙箱',
    description: '通过 K8s Pod 提供隔离运行环境（生产部署）',
  },
};

const STORAGE_KEY = 'himarket:selectedRuntime';

interface UseRuntimeSelectionOptions {
  /** 当前选中的 CLI Provider（从 providers 列表中获取） */
  provider?: ICliProvider | null;
}

interface UseRuntimeSelectionReturn {
  /** 当前选中的运行时类型 */
  selectedRuntime: RuntimeType;
  /** 当前 CLI Provider 兼容的运行时选项列表 */
  compatibleRuntimes: RuntimeOption[];
  /** 选择运行时 */
  selectRuntime: (type: string) => void;
}

/**
 * 运行时选择 Hook
 *
 * 根据当前 CLI Provider 的 compatibleRuntimes 生成可选运行时列表，
 * 通过后端 API 获取运行时可用性状态（如 K8s 是否已配置），
 * 管理选中状态并持久化到 localStorage。
 */
export function useRuntimeSelection({
  provider,
}: UseRuntimeSelectionOptions): UseRuntimeSelectionReturn {
  const [selectedRuntime, setSelectedRuntime] = useState<RuntimeType>(() => {
    try {
      const stored = localStorage.getItem(STORAGE_KEY);
      // 向后兼容：旧版本可能存储了 'local'，统一映射为 'k8s'
      if (stored === 'k8s' || stored === 'local') {
        return 'k8s';
      }
    } catch {
      // ignore
    }
    return 'k8s';
  });

  // 后端返回的运行时可用性状态
  const [runtimeAvailability, setRuntimeAvailability] = useState<IRuntimeAvailability[]>([]);

  // 组件挂载时从后端获取运行时可用性
  useEffect(() => {
    let cancelled = false;
    getAvailableRuntimes()
      .then((resp) => {
        if (!cancelled && resp.data) {
          setRuntimeAvailability(resp.data);
        }
      })
      .catch(() => {
        // 获取失败时回退：K8s 标记为不可用
        if (!cancelled) {
          setRuntimeAvailability([
            { type: 'k8s', available: false, unavailableReason: '无法获取运行时状态，请稍后重试' },
          ]);
        }
      });
    return () => { cancelled = true; };
  }, []);

  // 根据 CLI Provider 的 compatibleRuntimes 和后端可用性构建选项列表
  const compatibleRuntimes = useMemo<RuntimeOption[]>(() => {
    const compatible = provider?.compatibleRuntimes ?? ['k8s'];

    return compatible.map((type) => {
      const base = DEFAULT_RUNTIME_OPTIONS[type];
      if (!base) {
        return {
          type,
          label: type,
          description: '',
          available: false,
          unavailableReason: '未知运行时类型',
        };
      }

      // 从后端可用性状态中查找
      const backendStatus = runtimeAvailability.find((r) => r.type === type);

      let available = true;
      let unavailableReason: string | undefined;

      if (backendStatus) {
        // 后端已返回可用性状态，使用后端数据
        available = backendStatus.available;
        unavailableReason = backendStatus.unavailableReason;
      } else if (type === 'k8s') {
        // 后端尚未返回时，K8s 默认标记为不可用（需要集群配置）
        available = false;
        unavailableReason = '请先配置 K8s 集群连接信息';
      }

      return { ...base, available, unavailableReason };
    });
  }, [provider, runtimeAvailability]);

  // 当 compatibleRuntimes 变化时，如果当前选中的运行时不在兼容列表中，自动切换
  useEffect(() => {
    const availableTypes = compatibleRuntimes.filter((r) => r.available).map((r) => r.type);
    if (availableTypes.length > 0 && !availableTypes.includes(selectedRuntime)) {
      setSelectedRuntime(availableTypes[0]);
    }
  }, [compatibleRuntimes, selectedRuntime]);

  const selectRuntime = useCallback((type: string) => {
    const rt = type as RuntimeType;
    setSelectedRuntime(rt);
    try {
      localStorage.setItem(STORAGE_KEY, rt);
    } catch {
      // ignore
    }
  }, []);

  return {
    selectedRuntime,
    compatibleRuntimes,
    selectRuntime,
  };
}
