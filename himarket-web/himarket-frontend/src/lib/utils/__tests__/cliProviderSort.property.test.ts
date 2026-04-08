import * as fc from 'fast-check';
import { describe, it, expect } from 'vitest';

import { sortCliProviders } from '../cliProviderSort';

import type { ICliProvider } from '../../apis/cliProvider';

/**
 * fast-check arbitrary: 生成随机 provider key
 * 混合普通 key 和包含 "qwen" 的 key，确保属性测试能覆盖 Qwen 场景
 */
const arbKey = fc.oneof(
  fc.string({ maxLength: 20, minLength: 1 }),
  fc.constant('qwen-code'),
  fc.constant('QWEN-CLI'),
  fc.string({ maxLength: 10, minLength: 1 }).map((s) => `qwen-${s}`),
);

/**
 * fast-check arbitrary: 生成随机 ICliProvider
 */
const arbCliProvider: fc.Arbitrary<ICliProvider> = fc.record({
  available: fc.boolean(),
  displayName: fc.string({ maxLength: 30, minLength: 1 }),
  isDefault: fc.boolean(),
  key: arbKey,
  supportsCustomModel: fc.option(fc.boolean(), { nil: undefined }),
  supportsMcp: fc.option(fc.boolean(), { nil: undefined }),
  supportsSkill: fc.option(fc.boolean(), { nil: undefined }),
});

/**
 * 生成随机 provider 列表
 */
const arbProviderList = fc.array(arbCliProvider, { maxLength: 30, minLength: 0 });

describe('Feature: cli-ux-optimization, Property 1: CLI Provider 排序不变量', () => {
  /**
   * **Validates: Requirements 1.1**
   *
   * (a) 如果列表中存在 key 包含 "qwen" 的可用 provider，该 provider 应位于排序结果的第一位
   * (b) 所有可用 provider 应排在所有不可用 provider 之前
   * (c) 排序不应改变列表的长度
   */
  it('排序后满足所有不变量：Qwen 可用则排首位、可用在前不可用在后、长度不变', () => {
    fc.assert(
      fc.property(arbProviderList, (providers) => {
        const sorted = sortCliProviders(providers);

        // (c) 排序不应改变列表的长度
        expect(sorted).toHaveLength(providers.length);

        // (b) 所有可用 provider 应排在所有不可用 provider 之前
        let seenUnavailable = false;
        for (const p of sorted) {
          if (!p.available) {
            seenUnavailable = true;
          } else if (seenUnavailable) {
            // 可用 provider 出现在不可用 provider 之后，违反不变量
            expect.unreachable('可用 provider 不应出现在不可用 provider 之后');
          }
        }

        // (a) 如果列表中存在 key 包含 "qwen" 的可用 provider，该 provider 应位于排序结果的第一位
        const hasAvailableQwen = providers.some(
          (p) => p.available && p.key.toLowerCase().includes('qwen'),
        );
        if (hasAvailableQwen) {
          expect(sorted.at(0)?.key.toLowerCase()).toContain('qwen');
          expect(sorted.at(0)?.available).toBe(true);
        }
      }),
      { numRuns: 100 },
    );
  });
});
