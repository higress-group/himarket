import { describe, it, expect } from 'vitest';
import * as fc from 'fast-check';
import { filterByKeyword } from '../filterUtils';

/**
 * fast-check arbitrary: 生成随机对象，包含字符串字段和数组字段
 */
const arbItem = fc.record({
  name: fc.string({ minLength: 0, maxLength: 30 }),
  description: fc.string({ minLength: 0, maxLength: 50 }),
  tags: fc.array(fc.string({ minLength: 0, maxLength: 15 }), { minLength: 0, maxLength: 5 }),
});

type TestItem = { name: string; description: string; tags: string[] };

/**
 * 生成随机对象列表
 */
const arbItemList = fc.array(arbItem, { minLength: 0, maxLength: 30 });

/**
 * 生成随机关键词（包含空白、普通字符串等场景）
 */
const arbKeyword = fc.oneof(
  fc.string({ minLength: 0, maxLength: 20 }),
  fc.constant(''),
  fc.constant('  '),
);

/**
 * 生成随机字段子集
 */
const arbFields = fc.subarray(['name', 'description', 'tags'] as const, { minLength: 1 });

describe('Feature: cli-ux-optimization, Property 2: 关键词过滤正确性', () => {
  /**
   * **Validates: Requirements 2.4, 3.4**
   *
   * 对任意对象列表和任意关键词字符串，filterByKeyword 返回的结果中：
   * (a) 每一项都应在指定字段中包含该关键词（大小写不敏感）
   * (b) 结果集应是原列表的子集（长度 ≤ 原列表长度）
   */
  it('过滤结果中每项都包含关键词且结果是原列表的子集', () => {
    fc.assert(
      fc.property(arbItemList, arbKeyword, arbFields, (items, keyword, fields) => {
        const result = filterByKeyword(items, keyword, fields as (keyof TestItem)[]);
        const trimmed = keyword.trim();

        // (b) 结果长度 ≤ 原列表长度
        expect(result.length).toBeLessThanOrEqual(items.length);

        // (b) 结果中的每一项都来自原列表
        for (const item of result) {
          expect(items).toContain(item);
        }

        // (a) 如果关键词非空，每一项都应在指定字段中包含该关键词（大小写不敏感）
        if (trimmed) {
          const lowerKeyword = trimmed.toLowerCase();
          for (const item of result) {
            const matchesAnyField = fields.some((field) => {
              const value = item[field];
              if (value == null) return false;
              if (Array.isArray(value)) {
                return value.join(' ').toLowerCase().includes(lowerKeyword);
              }
              return String(value).toLowerCase().includes(lowerKeyword);
            });
            expect(matchesAnyField).toBe(true);
          }
        }

        // 空关键词时应返回全部列表
        if (!trimmed) {
          expect(result).toHaveLength(items.length);
        }
      }),
      { numRuns: 100 },
    );
  });
});
