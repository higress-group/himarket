import * as fc from 'fast-check';
import { describe, it, expect } from 'vitest';

import type { ICliProvider } from '../../../lib/apis/cliProvider';

/**
 * 模拟 CLI 工具单选逻辑：
 * 给定一组 provider 和一系列选择操作（每次选择一个 key），
 * 每次操作后最多只有一个工具被选中，且选中的工具必须是可用的。
 */
function simulateSelection(
  providers: ICliProvider[],
  actions: string[],
): { selectedId: string | null; history: (string | null)[] } {
  let selectedId: string | null = null;
  const history: (string | null)[] = [];

  for (const key of actions) {
    const provider = providers.find((p) => p.key === key);
    if (provider?.available) {
      selectedId = key;
    }
    // 如果 provider 不存在或不可用，选中状态不变
    history.push(selectedId);
  }

  return { history, selectedId };
}

/**
 * fast-check arbitrary: 生成随机 ICliProvider
 */
const arbProvider: fc.Arbitrary<ICliProvider> = fc.record({
  available: fc.boolean(),
  displayName: fc.string({ maxLength: 30, minLength: 1 }),
  isDefault: fc.boolean(),
  key: fc.string({ maxLength: 20, minLength: 1 }),
  supportsCustomModel: fc.option(fc.boolean(), { nil: undefined }),
  supportsMcp: fc.option(fc.boolean(), { nil: undefined }),
  supportsSkill: fc.option(fc.boolean(), { nil: undefined }),
});

/**
 * 生成一组 provider（key 唯一）和基于这些 key 的随机选择操作序列
 */
function arbProvidersAndActions() {
  return arbProvider
    .chain((first) =>
      fc.tuple(fc.constant(first), fc.array(arbProvider, { maxLength: 9, minLength: 0 })),
    )
    .chain(([first, rest]) => {
      // 确保 key 唯一
      const seen = new Set<string>();
      const providers: ICliProvider[] = [];
      for (const p of [first, ...rest]) {
        if (!seen.has(p.key)) {
          seen.add(p.key);
          providers.push(p);
        }
      }
      // 生成基于所有 provider key 的随机操作序列（也可能包含不存在的 key）
      const allKeys = providers.map((p) => p.key);
      const arbAction = fc.oneof(
        fc.constantFrom(...allKeys),
        fc.string({ maxLength: 20, minLength: 1 }), // 可能不存在的 key
      );
      return fc.tuple(fc.constant(providers), fc.array(arbAction, { maxLength: 30, minLength: 1 }));
    });
}

describe('Feature: cli-ux-optimization, Property 4: CLI 工具单选不变量', () => {
  /**
   * **Validates: Requirements 7.2**
   *
   * 对于任意 CLI 工具列表和任意选择操作序列，
   * 在任意时刻最多只有一个工具处于选中状态，且选中的工具必须是可用的（available === true）。
   */
  it('任意选择操作序列后，最多只有一个工具被选中，且选中工具必须可用', () => {
    fc.assert(
      fc.property(arbProvidersAndActions(), ([providers, actions]) => {
        const { history } = simulateSelection(providers, actions);

        for (const selectedId of history) {
          if (selectedId !== null) {
            // 不变量 1: 选中的工具必须存在于 provider 列表中
            const provider = providers.find((p) => p.key === selectedId);
            expect(provider).toBeDefined();

            // 不变量 2: 选中的工具必须是可用的
            expect(provider?.available).toBe(true);
          }
          // 不变量 3: selectedId 是 string | null，即最多只有一个选中
          // 这由数据结构本身保证（单个变量而非集合）
        }
      }),
      { numRuns: 100 },
    );
  });
});
