import { useState, useCallback, useMemo } from "react";
import type { CodingConfig } from "../types/coding";
import { DEFAULT_CONFIG, isConfigComplete } from "../types/coding";

const STORAGE_KEY = "hicoding:config";

interface UseCodingConfigReturn {
  config: CodingConfig;
  setConfig: (config: CodingConfig) => void;
  isFirstTime: boolean;
  isComplete: boolean;
}

function readConfig(): { config: CodingConfig; isFirstTime: boolean } {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (raw === null) {
      return { config: DEFAULT_CONFIG, isFirstTime: true };
    }
    const parsed = JSON.parse(raw) as CodingConfig;
    return { config: parsed, isFirstTime: false };
  } catch {
    // 数据损坏，清除并返回默认配置
    try {
      localStorage.removeItem(STORAGE_KEY);
    } catch {
      // ignore
    }
    return { config: DEFAULT_CONFIG, isFirstTime: true };
  }
}

function writeConfig(config: CodingConfig): void {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(config));
  } catch {
    // ignore
  }
}

export function useCodingConfig(): UseCodingConfigReturn {
  const [state, setState] = useState(() => readConfig());

  const setConfig = useCallback((newConfig: CodingConfig) => {
    writeConfig(newConfig);
    setState({ config: newConfig, isFirstTime: false });
  }, []);

  const isComplete = useMemo(
    () => isConfigComplete(state.config),
    [state.config]
  );

  return {
    config: state.config,
    setConfig,
    isFirstTime: state.isFirstTime,
    isComplete,
  };
}
