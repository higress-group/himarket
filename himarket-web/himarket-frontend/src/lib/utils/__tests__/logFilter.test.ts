// Feature: poc-code-cleanup, Property 2: 日志过滤正确性
// **Validates: Requirements 5.7**

import { describe, it, expect } from 'vitest';
import * as fc from 'fast-check';
import { filterLogs } from '../logFilter';
import type { AggregatedLogEntry } from '../../../types/log';

// ===== fast-check 生成器 =====

/** 生成消息方向 */
const arbDirection = fc.constantFrom(
  'client_to_agent' as const,
  'agent_to_client' as const,
);

/** 生成可选的 method 名称 */
const arbMethod = fc.option(
  fc.constantFrom(
    'initialize',
    'session/new',
    'session/prompt',
    'session/update',
    'session/set_model',
    'session/set_mode',
    'session/cancel',
    'session/request_permission',
    'tool_call',
  ),
  { nil: undefined },
);

/** 生成非空 summary 文本 */
const arbSummary = fc.string({ minLength: 1, maxLength: 80 });

/** 生成单条 AggregatedLogEntry */
const arbLogEntry: fc.Arbitrary<AggregatedLogEntry> = fc.record({
  id: fc.uuid(),
  direction: arbDirection,
  timestamp: fc.integer({ min: 1_000_000_000_000, max: 2_000_000_000_000 }),
  endTimestamp: fc.integer({ min: 1_000_000_000_000, max: 2_000_000_000_000 }),
  method: arbMethod,
  rpcId: fc.option(fc.integer({ min: 1, max: 10000 }), { nil: undefined }),
  summary: arbSummary,
  data: fc.constant(null),
  messageCount: fc.integer({ min: 1, max: 50 }),
  isAggregated: fc.boolean(),
});

/** 生成任意顺序的日志列表 */
const arbLogs: fc.Arbitrary<AggregatedLogEntry[]> = fc.array(arbLogEntry, {
  minLength: 0,
  maxLength: 30,
});

/** 生成非空过滤关键词 */
const arbNonEmptyFilter = fc.string({ minLength: 1, maxLength: 30 });


// ===== 属性测试 =====

describe('日志过滤正确性属性测试', () => {
  // Feature: poc-code-cleanup, Property 2: 日志过滤正确性
  // **Validates: Requirements 5.7**

  it('过滤结果中每条日志的 method 或 summary 应包含关键词（不区分大小写）', () => {
    fc.assert(
      fc.property(arbLogs, arbNonEmptyFilter, (logs, filter) => {
        const filtered = filterLogs(logs, filter);
        const q = filter.toLowerCase();

        for (const entry of filtered) {
          const method = (entry.method ?? '').toLowerCase();
          const summary = entry.summary.toLowerCase();
          expect(method.includes(q) || summary.includes(q)).toBe(true);
        }
      }),
      { numRuns: 100 },
    );
  });

  it('所有匹配的日志都应被包含在过滤结果中（无遗漏）', () => {
    fc.assert(
      fc.property(arbLogs, arbNonEmptyFilter, (logs, filter) => {
        const filtered = filterLogs(logs, filter);
        const q = filter.toLowerCase();

        const expected = logs.filter((entry) => {
          const method = (entry.method ?? '').toLowerCase();
          const summary = entry.summary.toLowerCase();
          return method.includes(q) || summary.includes(q);
        });

        expect(filtered.length).toBe(expected.length);
        const filteredIds = new Set(filtered.map((e) => e.id));
        const expectedIds = new Set(expected.map((e) => e.id));
        expect(filteredIds).toEqual(expectedIds);
      }),
      { numRuns: 100 },
    );
  });

  it('空过滤关键词应返回完整列表', () => {
    fc.assert(
      fc.property(arbLogs, (logs) => {
        const filtered = filterLogs(logs, '');
        expect(filtered).toBe(logs); // 引用相等，直接返回原数组
      }),
      { numRuns: 100 },
    );
  });

  it('过滤结果是原始列表的子集且保持顺序', () => {
    fc.assert(
      fc.property(arbLogs, arbNonEmptyFilter, (logs, filter) => {
        const filtered = filterLogs(logs, filter);

        expect(filtered.length).toBeLessThanOrEqual(logs.length);

        let lastIdx = -1;
        for (const entry of filtered) {
          const idx = logs.indexOf(entry);
          expect(idx).toBeGreaterThan(lastIdx);
          lastIdx = idx;
        }
      }),
      { numRuns: 100 },
    );
  });

  it('过滤不区分大小写', () => {
    fc.assert(
      fc.property(arbLogs, arbNonEmptyFilter, (logs, filter) => {
        const upper = filterLogs(logs, filter.toUpperCase());
        const lower = filterLogs(logs, filter.toLowerCase());
        const mixed = filterLogs(logs, filter);

        const upperIds = upper.map((e) => e.id);
        const lowerIds = lower.map((e) => e.id);
        const mixedIds = mixed.map((e) => e.id);

        expect(upperIds).toEqual(lowerIds);
        expect(upperIds).toEqual(mixedIds);
      }),
      { numRuns: 100 },
    );
  });
});
