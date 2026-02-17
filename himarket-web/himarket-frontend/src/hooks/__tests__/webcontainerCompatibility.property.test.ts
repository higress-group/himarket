/**
 * Property 5: WebContainer 兼容性过滤
 *
 * 对于任意 CLI Provider 配置，如果其 runtimeCategory 不是 "nodejs"，
 * 则 WebContainer_Runtime 应该拒绝创建实例并返回不兼容错误；
 * 如果 runtimeCategory 是 "nodejs"，则应该允许创建。
 *
 * **Validates: Requirements 4.6, 4.7**
 */
import { renderHook } from '@testing-library/react';
import { describe, it, expect, beforeEach, vi } from 'vitest';
import fc from 'fast-check';
import { useRuntimeSelection } from '../useRuntimeSelection';
import type { ICliProvider } from '../../lib/apis/cliProvider';
import type { RuntimeType } from '../../types/runtime';

// Mock localStorage
const localStorageMock = (() => {
  let store: Record<string, string> = {};
  return {
    getItem: vi.fn((key: string) => store[key] ?? null),
    setItem: vi.fn((key: string, value: string) => {
      store[key] = value;
    }),
    removeItem: vi.fn((key: string) => {
      delete store[key];
    }),
    clear: vi.fn(() => {
      store = {};
    }),
  };
})();

Object.defineProperty(window, 'localStorage', { value: localStorageMock });

beforeEach(() => {
  localStorageMock.clear();
  vi.clearAllMocks();
});

// ===== Generators =====

/** 生成随机 runtimeCategory */
const runtimeCategoryArb = fc.constantFrom('native', 'nodejs', 'python') as fc.Arbitrary<
  'native' | 'nodejs' | 'python'
>;

/** 生成随机 RuntimeType 子集（至少包含一个元素） */
const runtimeTypesArb = fc
  .subarray(['local', 'agentrun', 'webcontainer'] as RuntimeType[], { minLength: 1 })
  .filter((arr) => arr.length > 0);

/** 生成包含 webcontainer 的 compatibleRuntimes */
const runtimeTypesWithWebcontainerArb = fc
  .subarray(['local', 'agentrun'] as RuntimeType[])
  .map((others) => [...others, 'webcontainer' as RuntimeType]);

/** 生成随机 CliProvider 配置 */
const cliProviderArb = fc.record({
  key: fc.stringMatching(/^[a-z][a-z0-9-]{0,19}$/, { minLength: 1, maxLength: 20 }),
  displayName: fc.string({ minLength: 1, maxLength: 50 }),
  isDefault: fc.boolean(),
  available: fc.constant(true),
  runtimeCategory: runtimeCategoryArb,
  compatibleRuntimes: runtimeTypesArb,
});

/** 生成 runtimeCategory 为 nodejs 且 compatibleRuntimes 包含 webcontainer 的 CliProvider */
const nodejsProviderWithWebcontainerArb = fc.record({
  key: fc.stringMatching(/^[a-z][a-z0-9-]{0,19}$/, { minLength: 1, maxLength: 20 }),
  displayName: fc.string({ minLength: 1, maxLength: 50 }),
  isDefault: fc.boolean(),
  available: fc.constant(true),
  runtimeCategory: fc.constant('nodejs' as const),
  compatibleRuntimes: runtimeTypesWithWebcontainerArb,
});

/** 生成 runtimeCategory 不是 nodejs 且 compatibleRuntimes 包含 webcontainer 的 CliProvider */
const nonNodejsProviderWithWebcontainerArb = fc.record({
  key: fc.stringMatching(/^[a-z][a-z0-9-]{0,19}$/, { minLength: 1, maxLength: 20 }),
  displayName: fc.string({ minLength: 1, maxLength: 50 }),
  isDefault: fc.boolean(),
  available: fc.constant(true),
  runtimeCategory: fc.constantFrom('native', 'python') as fc.Arbitrary<'native' | 'python'>,
  compatibleRuntimes: runtimeTypesWithWebcontainerArb,
});

// ===== Property Tests =====

describe('Property 5: WebContainer 兼容性过滤', () => {
  /**
   * 属性 5a: 对于任意 runtimeCategory 为 "nodejs" 的 CLI Provider，
   * 当 compatibleRuntimes 包含 "webcontainer" 时，
   * WebContainer 选项应该标记为可用 (available = true)。
   *
   * **Validates: Requirements 4.6**
   */
  it('nodejs 类型的 CLI Provider 应兼容 WebContainer', () => {
    fc.assert(
      fc.property(nodejsProviderWithWebcontainerArb, (providerData) => {
        const provider: ICliProvider = providerData;

        const { result } = renderHook(() => useRuntimeSelection({ provider }));

        const wcOption = result.current.compatibleRuntimes.find((r) => r.type === 'webcontainer');
        expect(wcOption).toBeDefined();
        expect(wcOption!.available).toBe(true);
        expect(wcOption!.unavailableReason).toBeUndefined();
      }),
      { numRuns: 100 },
    );
  });

  /**
   * 属性 5b: 对于任意 runtimeCategory 不是 "nodejs" 的 CLI Provider（native 或 python），
   * 当 compatibleRuntimes 包含 "webcontainer" 时，
   * WebContainer 选项应该标记为不可用 (available = false) 并包含不兼容错误信息。
   *
   * **Validates: Requirements 4.7**
   */
  it('非 nodejs 类型的 CLI Provider 不应兼容 WebContainer', () => {
    fc.assert(
      fc.property(nonNodejsProviderWithWebcontainerArb, (providerData) => {
        const provider: ICliProvider = providerData;

        const { result } = renderHook(() => useRuntimeSelection({ provider }));

        const wcOption = result.current.compatibleRuntimes.find((r) => r.type === 'webcontainer');
        expect(wcOption).toBeDefined();
        expect(wcOption!.available).toBe(false);
        expect(wcOption!.unavailableReason).toBeDefined();
        expect(wcOption!.unavailableReason!.length).toBeGreaterThan(0);
      }),
      { numRuns: 100 },
    );
  });

  /**
   * 属性 5c: 对于任意 CLI Provider 配置，WebContainer 兼容性完全由 runtimeCategory 决定：
   * runtimeCategory === "nodejs" ↔ WebContainer 可用。
   * 这是属性 5 的完整双向验证。
   *
   * **Validates: Requirements 4.6, 4.7**
   */
  it('WebContainer 兼容性完全由 runtimeCategory 是否为 nodejs 决定', () => {
    fc.assert(
      fc.property(
        fc.record({
          key: fc.stringMatching(/^[a-z][a-z0-9-]{0,19}$/, { minLength: 1, maxLength: 20 }),
          displayName: fc.string({ minLength: 1, maxLength: 50 }),
          isDefault: fc.boolean(),
          available: fc.constant(true),
          runtimeCategory: runtimeCategoryArb,
          compatibleRuntimes: runtimeTypesWithWebcontainerArb,
        }),
        (providerData) => {
          const provider: ICliProvider = providerData;

          const { result } = renderHook(() => useRuntimeSelection({ provider }));

          const wcOption = result.current.compatibleRuntimes.find(
            (r) => r.type === 'webcontainer',
          );
          expect(wcOption).toBeDefined();

          if (provider.runtimeCategory === 'nodejs') {
            expect(wcOption!.available).toBe(true);
            expect(wcOption!.unavailableReason).toBeUndefined();
          } else {
            expect(wcOption!.available).toBe(false);
            expect(wcOption!.unavailableReason).toBeDefined();
            expect(wcOption!.unavailableReason!.length).toBeGreaterThan(0);
          }
        },
      ),
      { numRuns: 100 },
    );
  });
});
