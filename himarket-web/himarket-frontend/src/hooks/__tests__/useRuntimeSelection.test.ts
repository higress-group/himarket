import { renderHook, act } from '@testing-library/react';
import { describe, it, expect, beforeEach, vi } from 'vitest';
import { useRuntimeSelection } from '../useRuntimeSelection';
import type { ICliProvider } from '../../lib/apis/cliProvider';
import type { SandboxMode } from '../../types/runtime';

// Mock localStorage
const localStorageMock = (() => {
  let store: Record<string, string> = {};
  return {
    getItem: vi.fn((key: string) => store[key] ?? null),
    setItem: vi.fn((key: string, value: string) => { store[key] = value; }),
    removeItem: vi.fn((key: string) => { delete store[key]; }),
    clear: vi.fn(() => { store = {}; }),
  };
})();

Object.defineProperty(window, 'localStorage', { value: localStorageMock });

beforeEach(() => {
  localStorageMock.clear();
  vi.clearAllMocks();
});

// ===== 测试数据 =====

const nativeProvider: ICliProvider = {
  key: 'qodercli',
  displayName: 'Qoder CLI',
  isDefault: true,
  available: true,
  compatibleRuntimes: ['local', 'k8s'],
  runtimeCategory: 'native',
};

const localOnlyProvider: ICliProvider = {
  key: 'test-cli',
  displayName: 'Test CLI',
  isDefault: false,
  available: true,
  compatibleRuntimes: ['local'],
  runtimeCategory: 'native',
};

const noRuntimeProvider: ICliProvider = {
  key: 'legacy-cli',
  displayName: 'Legacy CLI',
  isDefault: false,
  available: true,
};

// ===== 测试 =====

describe('useRuntimeSelection', () => {
  it('默认选中 local 运行时', () => {
    const { result } = renderHook(() =>
      useRuntimeSelection({ provider: nativeProvider })
    );

    expect(result.current.selectedRuntime).toBe('local');
  });

  it('根据 CLI Provider 的 compatibleRuntimes 生成选项列表', () => {
    const { result } = renderHook(() =>
      useRuntimeSelection({ provider: nativeProvider })
    );

    expect(result.current.compatibleRuntimes).toHaveLength(2);
    expect(result.current.compatibleRuntimes.map(r => r.type)).toEqual(['local', 'k8s']);
  });

  it('没有 compatibleRuntimes 字段时回退到 local', () => {
    const { result } = renderHook(() =>
      useRuntimeSelection({ provider: noRuntimeProvider })
    );

    expect(result.current.compatibleRuntimes).toHaveLength(1);
    expect(result.current.compatibleRuntimes[0].type).toBe('local');
  });

  it('provider 为 null 时回退到 local', () => {
    const { result } = renderHook(() =>
      useRuntimeSelection({ provider: null })
    );

    expect(result.current.compatibleRuntimes).toHaveLength(1);
    expect(result.current.compatibleRuntimes[0].type).toBe('local');
    expect(result.current.selectedRuntime).toBe('local');
  });

  it('selectRuntime 更新选中状态并持久化到 localStorage', () => {
    const { result } = renderHook(() =>
      useRuntimeSelection({ provider: nativeProvider })
    );

    act(() => {
      result.current.selectRuntime('k8s');
    });

    expect(result.current.selectedRuntime).toBe('k8s');
    expect(localStorageMock.setItem).toHaveBeenCalledWith('himarket:selectedRuntime', 'k8s');
  });

  it('当选中的运行时不在兼容列表中时自动切换到第一个可用的', () => {
    localStorageMock.setItem('himarket:selectedRuntime', 'k8s');

    const { result } = renderHook(() =>
      useRuntimeSelection({ provider: localOnlyProvider })
    );

    expect(result.current.selectedRuntime).toBe('local');
  });

  it('所有运行时选项都有 label 和 description', () => {
    const { result } = renderHook(() =>
      useRuntimeSelection({ provider: nativeProvider })
    );

    for (const option of result.current.compatibleRuntimes) {
      expect(option.label).toBeTruthy();
      expect(option.description).toBeTruthy();
    }
  });
});

describe('useRuntimeSelection - sandboxMode', () => {
  it('sandboxMode 默认为 user', () => {
    const { result } = renderHook(() =>
      useRuntimeSelection({ provider: nativeProvider })
    );

    expect(result.current.sandboxMode).toBe('user');
  });

  it('从 localStorage 恢复有效的 sandboxMode', () => {
    localStorageMock.setItem('himarket:sandboxMode', 'session');

    const { result } = renderHook(() =>
      useRuntimeSelection({ provider: nativeProvider })
    );

    expect(result.current.sandboxMode).toBe('session');
  });

  it('localStorage 中无效值时回退到 user', () => {
    localStorageMock.setItem('himarket:sandboxMode', 'invalid');

    const { result } = renderHook(() =>
      useRuntimeSelection({ provider: nativeProvider })
    );

    expect(result.current.sandboxMode).toBe('user');
  });

  it('setSandboxMode 更新状态并持久化到 localStorage', () => {
    const { result } = renderHook(() =>
      useRuntimeSelection({ provider: nativeProvider })
    );

    act(() => {
      result.current.setSandboxMode('session');
    });

    expect(result.current.sandboxMode).toBe('session');
    expect(localStorageMock.setItem).toHaveBeenCalledWith('himarket:sandboxMode', 'session');
  });

  it('sandboxMode 变化时持久化到 localStorage', () => {
    const { result } = renderHook(() =>
      useRuntimeSelection({ provider: nativeProvider })
    );

    act(() => {
      result.current.setSandboxMode('session');
    });

    expect(localStorageMock.setItem).toHaveBeenCalledWith('himarket:sandboxMode', 'session');

    act(() => {
      result.current.setSandboxMode('user');
    });

    expect(localStorageMock.setItem).toHaveBeenCalledWith('himarket:sandboxMode', 'user');
  });
});
