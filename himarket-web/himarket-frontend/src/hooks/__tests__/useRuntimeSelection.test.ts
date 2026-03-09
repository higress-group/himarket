import { renderHook, act, waitFor } from '@testing-library/react';
import { describe, it, expect, beforeEach, vi } from 'vitest';
import { useRuntimeSelection } from '../useRuntimeSelection';
import type { ICliProvider } from '../../lib/apis/cliProvider';

// Mock getAvailableRuntimes API，让 local 和 k8s 都可用
vi.mock('../../lib/apis/runtime', () => ({
  getAvailableRuntimes: vi.fn(() =>
    Promise.resolve({
      data: [
        { type: 'local', available: true },
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

// ===== 测试 =====

describe('useRuntimeSelection', () => {
  it('根据 CLI Provider 的 compatibleRuntimes 生成选项列表', () => {
    const { result } = renderHook(() =>
      useRuntimeSelection({ provider: nativeProvider })
    );

    expect(result.current.compatibleRuntimes).toHaveLength(2);
    expect(result.current.compatibleRuntimes.map(r => r.type)).toEqual(['local', 'k8s']);
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
