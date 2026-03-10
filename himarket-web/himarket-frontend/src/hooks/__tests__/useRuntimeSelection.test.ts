import { renderHook, act, waitFor } from '@testing-library/react';
import { describe, it, expect, beforeEach, vi } from 'vitest';
import { useRuntimeSelection } from '../useRuntimeSelection';
import type { ICliProvider } from '../../lib/apis/cliProvider';

// Mock getAvailableRuntimes API，让 k8s 可用
vi.mock('../../lib/apis/runtime', () => ({
  getAvailableRuntimes: vi.fn(() =>
    Promise.resolve({
      data: [
        { type: 'k8s', available: true },
      ],
    })
  ),
}));

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
  compatibleRuntimes: ['k8s'],
  runtimeCategory: 'native',
};

// ===== 测试 =====

describe('useRuntimeSelection', () => {
  it('根据 CLI Provider 的 compatibleRuntimes 生成选项列表', () => {
    const { result } = renderHook(() =>
      useRuntimeSelection({ provider: nativeProvider })
    );

    expect(result.current.compatibleRuntimes).toHaveLength(1);
    expect(result.current.compatibleRuntimes.map(r => r.type)).toEqual(['k8s']);
  });

  it('selectRuntime 更新选中状态并持久化到 localStorage', async () => {
    const { result } = renderHook(() =>
      useRuntimeSelection({ provider: nativeProvider })
    );

    // 等待 API mock 返回，确保 k8s 被标记为 available
    await waitFor(() => {
      expect(result.current.compatibleRuntimes.find(r => r.type === 'k8s')?.available).toBe(true);
    });

    act(() => {
      result.current.selectRuntime('k8s');
    });

    expect(result.current.selectedRuntime).toBe('k8s');
    expect(localStorageMock.setItem).toHaveBeenCalledWith('himarket:selectedRuntime', 'k8s');
  });

  it('localStorage 中旧的 local 值向后兼容映射为 k8s', () => {
    localStorageMock.setItem('himarket:selectedRuntime', 'local');

    const { result } = renderHook(() =>
      useRuntimeSelection({ provider: nativeProvider })
    );

    expect(result.current.selectedRuntime).toBe('k8s');
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
