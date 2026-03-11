// Feature: poc-code-cleanup, Property 1: LogAggregator 流式消息聚合
// **Validates: Requirements 5.2, 5.3, 5.8**

import { describe, it, expect, beforeEach } from 'vitest';
import * as fc from 'fast-check';
import { LogAggregator, _resetEntryCounter } from '../logAggregator';
import type { AggregatedLogEntry } from '../../../types/log';
import { JSONRPC_VERSION, CODING_METHODS } from '../../../types/coding-protocol';

// ===== Arbitraries =====

/** 生成非空 session ID */
const arbSessionId = fc.stringMatching(/^[a-zA-Z0-9]{1,20}$/);

/** 生成 chunk 文本片段 */
const arbChunkText = fc.string({ minLength: 0, maxLength: 50 });

/** 生成 agent_message_chunk 通知 */
function arbAgentMessageChunk(sessionId: string, text: string) {
  return {
    jsonrpc: JSONRPC_VERSION,
    method: CODING_METHODS.SESSION_UPDATE,
    params: {
      sessionId,
      update: {
        sessionUpdate: 'agent_message_chunk',
        content: { type: 'text', text },
      },
    },
  };
}

/** 生成 agent_thought_chunk 通知 */
function arbAgentThoughtChunk(sessionId: string, text: string) {
  return {
    jsonrpc: JSONRPC_VERSION,
    method: CODING_METHODS.SESSION_UPDATE,
    params: {
      sessionId,
      update: {
        sessionUpdate: 'agent_thought_chunk',
        content: { text },
      },
    },
  };
}

/** chunk 类型 */
type ChunkType = 'agent_message' | 'agent_thought';

/** 生成一组同类型的连续 chunk 消息 */
const arbChunkGroup = fc.record({
  type: fc.constantFrom<ChunkType>('agent_message', 'agent_thought'),
  sessionId: arbSessionId,
  texts: fc.array(arbChunkText, { minLength: 1, maxLength: 10 }),
});

/** 生成非流式 ACP 消息（request / response / 非 chunk 的 notification） */
const arbNonStreamingMessage = fc.oneof(
  // JSON-RPC request (e.g. initialize)
  fc.record({
    jsonrpc: fc.constant(JSONRPC_VERSION),
    id: fc.nat({ max: 10000 }),
    method: fc.constantFrom('initialize', 'session/new', 'session/prompt', 'session/cancel'),
    params: fc.constant({}),
  }),
  // JSON-RPC response
  fc.record({
    jsonrpc: fc.constant(JSONRPC_VERSION),
    id: fc.nat({ max: 10000 }),
    result: fc.constant({}),
  }),
  // Non-chunk session/update notification (e.g. tool_call)
  fc.record({
    jsonrpc: fc.constant(JSONRPC_VERSION),
    method: fc.constant(CODING_METHODS.SESSION_UPDATE),
    params: fc.record({
      sessionId: arbSessionId,
      update: fc.record({
        sessionUpdate: fc.constantFrom('tool_call', 'plan', 'usage_update'),
        toolCallId: fc.constant('tc-1'),
        status: fc.constant('completed'),
        title: fc.constant('test'),
        kind: fc.constant('read'),
      }),
    }),
  }),
);


/** 消息方向 */
const arbDirection = fc.constantFrom<'client_to_agent' | 'agent_to_client'>(
  'client_to_agent',
  'agent_to_client',
);

// ===== 辅助函数 =====

/** 收集 LogAggregator 输出的所有日志条目 */
function collectEntries(aggregator: LogAggregator): AggregatedLogEntry[] {
  const entries: AggregatedLogEntry[] = [];
  aggregator.onEntry = (entry) => entries.push(entry);
  return entries;
}

// ===== 属性测试 =====

describe('LogAggregator 属性测试', () => {
  beforeEach(() => {
    _resetEntryCounter();
  });

  // Property 1: LogAggregator 流式消息聚合
  // 对于任意 ACP 消息序列，LogAggregator 处理后应满足：
  // - 连续的同类型流式 chunk 消息被聚合为单条日志条目
  // - 其 isAggregated 为 true，messageCount 等于 chunk 数量
  // - summary 为拼接文本的前 100 个字符
  // - 非流式消息各自产生一条独立日志条目，isAggregated 为 false，messageCount 为 1

  it('连续同类型流式 chunk 被聚合为单条日志，isAggregated=true，messageCount=chunk数量，summary=前100字符', () => {
    fc.assert(
      fc.property(arbChunkGroup, ({ type, sessionId, texts }) => {
        const aggregator = new LogAggregator();
        const entries = collectEntries(aggregator);

        // 发送所有 chunk
        for (const text of texts) {
          const msg =
            type === 'agent_message'
              ? arbAgentMessageChunk(sessionId, text)
              : arbAgentThoughtChunk(sessionId, text);
          aggregator.processMessage('agent_to_client', msg);
        }

        // flush 触发聚合输出
        aggregator.flush();

        // 应该只产生一条聚合日志
        expect(entries.length).toBe(1);

        const entry = entries[0];
        expect(entry.isAggregated).toBe(true);
        expect(entry.messageCount).toBe(texts.length);

        // summary 应为拼接文本的前 100 个字符
        const fullText = texts.join('');
        const expectedSummary =
          fullText.length > 100 ? fullText.slice(0, 100) + '...' : fullText;
        expect(entry.summary).toBe(expectedSummary);

        // direction 应为 agent_to_client（流式消息来自 agent）
        expect(entry.direction).toBe('agent_to_client');
      }),
      { numRuns: 100 },
    );
  });

  it('非流式消息各自产生独立日志条目，isAggregated=false，messageCount=1', () => {
    fc.assert(
      fc.property(
        fc.array(arbNonStreamingMessage, { minLength: 1, maxLength: 10 }),
        arbDirection,
        (messages, direction) => {
          const aggregator = new LogAggregator();
          const entries = collectEntries(aggregator);

          for (const msg of messages) {
            aggregator.processMessage(direction, msg);
          }

          // 每条非流式消息应产生一条独立日志
          expect(entries.length).toBe(messages.length);

          for (const entry of entries) {
            expect(entry.isAggregated).toBe(false);
            expect(entry.messageCount).toBe(1);
          }
        },
      ),
      { numRuns: 100 },
    );
  });

  it('混合序列：chunk 组之间被非流式消息打断时，每组 chunk 聚合为一条，非流式各自独立', () => {
    // 生成交替的 chunk 组和非流式消息序列
    const arbMixedSequence = fc.record({
      chunkGroup1: arbChunkGroup,
      nonStreaming: fc.array(arbNonStreamingMessage, { minLength: 1, maxLength: 3 }),
      chunkGroup2: arbChunkGroup,
    });

    fc.assert(
      fc.property(arbMixedSequence, ({ chunkGroup1, nonStreaming, chunkGroup2 }) => {
        const aggregator = new LogAggregator();
        const entries = collectEntries(aggregator);

        // 第一组 chunk
        for (const text of chunkGroup1.texts) {
          const msg =
            chunkGroup1.type === 'agent_message'
              ? arbAgentMessageChunk(chunkGroup1.sessionId, text)
              : arbAgentThoughtChunk(chunkGroup1.sessionId, text);
          aggregator.processMessage('agent_to_client', msg);
        }

        // 非流式消息（会触发 flush 第一组 chunk）
        for (const msg of nonStreaming) {
          aggregator.processMessage('client_to_agent', msg);
        }

        // 第二组 chunk
        for (const text of chunkGroup2.texts) {
          const msg =
            chunkGroup2.type === 'agent_message'
              ? arbAgentMessageChunk(chunkGroup2.sessionId, text)
              : arbAgentThoughtChunk(chunkGroup2.sessionId, text);
          aggregator.processMessage('agent_to_client', msg);
        }

        // flush 第二组
        aggregator.flush();

        // 验证：第一组聚合 + 非流式各自独立 + 第二组聚合
        // 第一条应该是第一组 chunk 的聚合
        const firstEntry = entries[0];
        expect(firstEntry.isAggregated).toBe(true);
        expect(firstEntry.messageCount).toBe(chunkGroup1.texts.length);

        const fullText1 = chunkGroup1.texts.join('');
        const expectedSummary1 =
          fullText1.length > 100 ? fullText1.slice(0, 100) + '...' : fullText1;
        expect(firstEntry.summary).toBe(expectedSummary1);

        // 中间的非流式消息
        for (let i = 0; i < nonStreaming.length; i++) {
          const entry = entries[1 + i];
          expect(entry.isAggregated).toBe(false);
          expect(entry.messageCount).toBe(1);
        }

        // 最后一条应该是第二组 chunk 的聚合
        const lastEntry = entries[entries.length - 1];
        expect(lastEntry.isAggregated).toBe(true);
        expect(lastEntry.messageCount).toBe(chunkGroup2.texts.length);

        const fullText2 = chunkGroup2.texts.join('');
        const expectedSummary2 =
          fullText2.length > 100 ? fullText2.slice(0, 100) + '...' : fullText2;
        expect(lastEntry.summary).toBe(expectedSummary2);

        // 总条目数 = 1（第一组聚合）+ 非流式数量 + 1（第二组聚合）
        expect(entries.length).toBe(1 + nonStreaming.length + 1);
      }),
      { numRuns: 100 },
    );
  });

  it('不同类型的 chunk 交替出现时，各自独立聚合', () => {
    fc.assert(
      fc.property(
        arbSessionId,
        fc.array(arbChunkText, { minLength: 1, maxLength: 5 }),
        fc.array(arbChunkText, { minLength: 1, maxLength: 5 }),
        (sessionId, messageTexts, thoughtTexts) => {
          const aggregator = new LogAggregator();
          const entries = collectEntries(aggregator);

          // 先发 agent_message_chunk
          for (const text of messageTexts) {
            aggregator.processMessage(
              'agent_to_client',
              arbAgentMessageChunk(sessionId, text),
            );
          }

          // 再发 agent_thought_chunk（同 session，不同类型 → 不同 buffer key）
          for (const text of thoughtTexts) {
            aggregator.processMessage(
              'agent_to_client',
              arbAgentThoughtChunk(sessionId, text),
            );
          }

          aggregator.flush();

          // 两种类型各自聚合为一条
          expect(entries.length).toBe(2);

          // 两条都是聚合的
          expect(entries[0].isAggregated).toBe(true);
          expect(entries[1].isAggregated).toBe(true);

          // messageCount 分别等于各自的 chunk 数量
          const counts = [entries[0].messageCount, entries[1].messageCount];
          expect(counts).toContain(messageTexts.length);
          expect(counts).toContain(thoughtTexts.length);
        },
      ),
      { numRuns: 100 },
    );
  });
});
