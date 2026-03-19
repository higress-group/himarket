import { describe, it, expect } from 'vitest';
import * as fc from 'fast-check';
import { getVisibleSteps, type ProviderCapabilities } from '../stepUtils';

/**
 * fast-check arbitrary: 生成随机 ProviderCapabilities
 * 每个能力字段可以是 true、false 或 undefined
 */
const arbCapabilities: fc.Arbitrary<ProviderCapabilities> = fc.record({
  supportsCustomModel: fc.option(fc.boolean(), { nil: undefined }),
  supportsMcp: fc.option(fc.boolean(), { nil: undefined }),
  supportsSkill: fc.option(fc.boolean(), { nil: undefined }),
});

describe('Feature: cli-ux-optimization, Property 3: 动态步骤计算', () => {
  /**
   * **Validates: Requirements 4.3**
   *
   * 当 supportsCustomModel、supportsMcp、supportsSkill 全部为 false（或 undefined）时，
   * 计算出的可见步骤数应为 1；当任一为 true 时，步骤数应大于 1 且不超过 3。
   */
  it('能力全部为 false/undefined 时可见步骤为 1，任一为 true 时步骤数在 (1, 3] 之间', () => {
    fc.assert(
      fc.property(arbCapabilities, (caps) => {
        const visibleSteps = getVisibleSteps(caps);
        const count = visibleSteps.length;

        const hasCustomModel = caps.supportsCustomModel === true;
        const hasMcp = caps.supportsMcp === true;
        const hasSkill = caps.supportsSkill === true;
        const anyTrue = hasCustomModel || hasMcp || hasSkill;

        if (!anyTrue) {
          // 全部为 false/undefined → 仅步骤一可见
          expect(count).toBe(1);
        } else {
          // 任一为 true → 步骤数 > 1 且 ≤ 3
          expect(count).toBeGreaterThan(1);
          expect(count).toBeLessThanOrEqual(3);
        }
      }),
      { numRuns: 100 },
    );
  });
});
