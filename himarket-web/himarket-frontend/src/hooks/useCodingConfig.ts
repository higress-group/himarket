import { useState, useCallback, useMemo } from 'react';

import { DEFAULT_CONFIG, isConfigComplete } from '../types/coding';

import type { CodingConfig } from '../types/coding';

interface UseCodingConfigReturn {
  config: CodingConfig;
  setConfig: (config: CodingConfig) => void;
  isFirstTime: boolean;
  isComplete: boolean;
}

/**
 * HiCoding 配置 hook（纯内存状态，不持久化到 localStorage）。
 *
 * 每次页面加载从 DEFAULT_CONFIG 开始，由 Coding.tsx 负责自动填充默认 CLI 和模型。
 */
export function useCodingConfig(): UseCodingConfigReturn {
  const [config, setConfigState] = useState<CodingConfig>(DEFAULT_CONFIG);

  const setConfig = useCallback((newConfig: CodingConfig) => {
    setConfigState(newConfig);
  }, []);

  const isComplete = useMemo(() => isConfigComplete(config), [config]);

  return {
    config,
    isComplete,
    isFirstTime: false,
    setConfig,
  };
}
