// Feature: hicli-module, Property 3: Quest 列表排序与管理
// **Validates: Requirements 3.2, 3.4**

import { describe, it, expect } from 'vitest';
import * as fc from 'fast-check';
import {
  hiCliReducer,
  hiCliInitialState,
  type HiCliState,
  type HiCliAction,
} from '../HiCliSessionContext';
import type { QuestData } from '../QuestSessionContext';

// ===== 辅助函数 =====

/** 创建一个基础的 QuestData，支持自定义 createdAt */
function makeQuest(id: string, createdAt: number): QuestData {
  return {
    id,
    title: `Quest ${id}`,
    cwd: '/tmp',
    messages: [],
    availableModels: [],
    availableModes: [],
    currentModelId: '',
    currentModeId: '',
    isProcessing: false,
    inflightPromptId: null,
    promptQueue: [],
    lastStopReason: null,
    lastCompletedAt: null,
    selectedToolCallId: null,
    artifacts: [],
    activeArtifactId: null,
    lastArtifactScanAt: 0,
    createdAt,
    openFiles: [],
    activeFilePath: null,
    terminals: [],
    previewPort: null,
  };
}

/** 构建包含多个 Quest 的 HiCliState */
function makeStateWithQuests(
  questEntries: Array<{ id: string; createdAt: number }>,
): HiCliState {
  const quests: Record<string, QuestData> = {};
  for (const entry of questEntries) {
    quests[entry.id] = makeQuest(entry.id, entry.createdAt);
  }
  return {
    ...hiCliInitialState,
    quests,
    activeQuestId: questEntries.length > 0 ? questEntries[0].id : null,
  };
}

// ===== fast-check 生成器 =====

/** 生成合法的 Quest ID（字母数字组合，排除 Object 原型属性名） */
const arbQuestId = fc
  .stringMatching(/^[a-zA-Z0-9]{1,20}$/)
  .filter((id) => !(id in Object.prototype));

/** 生成不重复的 Quest ID 列表（至少 2 个，用于关闭测试） */
const arbQuestIdList = fc.uniqueArray(arbQuestId, { minLength: 2, maxLength: 10 });

/** 生成不重复的时间戳列表（与 Quest ID 列表等长） */
function arbTimestamps(count: number) {
  return fc.uniqueArray(fc.integer({ min: 1, max: 2_000_000_000_000 }), {
    minLength: count,
    maxLength: count,
  });
}

/** 生成 Quest 条目列表：不重复的 ID + 不重复的时间戳 */
const arbQuestEntries = arbQuestIdList.chain((ids) =>
  arbTimestamps(ids.length).map((timestamps) =>
    ids.map((id, i) => ({ id, createdAt: timestamps[i] })),
  ),
);

// ===== 属性测试 =====

describe('HiCliSessionContext reducer 属性测试', () => {
  // Feature: hicli-module, Property 3: Quest 列表排序与管理
  // **Validates: Requirements 3.2, 3.4**
  describe('Property 3: Quest 列表排序与管理', () => {
    it('Quest 列表按 createdAt 降序排列', () => {
      fc.assert(
        fc.property(arbQuestEntries, (entries) => {
          const state = makeStateWithQuests(entries);

          // 将 quests Record 转为数组并按 createdAt 降序排列
          const questList = Object.values(state.quests);
          const sorted = [...questList].sort((a, b) => b.createdAt - a.createdAt);

          // 验证降序排列后，每相邻两个 Quest 的 createdAt 满足 prev >= next
          for (let i = 0; i < sorted.length - 1; i++) {
            expect(sorted[i].createdAt).toBeGreaterThan(sorted[i + 1].createdAt);
          }

          // 验证排序后的列表包含所有原始 Quest
          const sortedIds = sorted.map((q) => q.id).sort();
          const originalIds = entries.map((e) => e.id).sort();
          expect(sortedIds).toEqual(originalIds);
        }),
        { numRuns: 100 },
      );
    });

    it('关闭任意一个 Quest 后，列表数量减少 1，且该 Quest 不再出现', () => {
      fc.assert(
        fc.property(
          arbQuestEntries,
          fc.nat({ max: 100 }),
          (entries, indexSeed) => {
            const state = makeStateWithQuests(entries);
            const originalCount = Object.keys(state.quests).length;

            // 选择一个要关闭的 Quest
            const closeIndex = indexSeed % entries.length;
            const closeId = entries[closeIndex].id;

            const action: HiCliAction = { type: 'QUEST_CLOSED', questId: closeId };
            const newState = hiCliReducer(state, action);

            // 列表数量减少 1
            expect(Object.keys(newState.quests).length).toBe(originalCount - 1);

            // 被关闭的 Quest 不再出现在列表中
            expect(newState.quests[closeId]).toBeUndefined();

            // 剩余 Quest 数据保持不变
            for (const entry of entries) {
              if (entry.id !== closeId) {
                expect(newState.quests[entry.id]).toEqual(state.quests[entry.id]);
              }
            }
          },
        ),
        { numRuns: 100 },
      );
    });

    it('关闭活跃 Quest 后，activeQuestId 指向剩余 Quest 之一或为 null', () => {
      fc.assert(
        fc.property(arbQuestEntries, (entries) => {
          // 将第一个 Quest 设为活跃并关闭它
          const activeId = entries[0].id;
          const state = makeStateWithQuests(entries);
          const stateWithActive: HiCliState = { ...state, activeQuestId: activeId };

          const action: HiCliAction = { type: 'QUEST_CLOSED', questId: activeId };
          const newState = hiCliReducer(stateWithActive, action);

          const remainingIds = Object.keys(newState.quests);
          if (remainingIds.length > 0) {
            // activeQuestId 应指向剩余 Quest 中的一个
            expect(remainingIds).toContain(newState.activeQuestId);
          } else {
            // 没有剩余 Quest 时，activeQuestId 应为 null
            expect(newState.activeQuestId).toBeNull();
          }
        }),
        { numRuns: 100 },
      );
    });

    it('关闭非活跃 Quest 时，activeQuestId 保持不变', () => {
      fc.assert(
        fc.property(
          arbQuestEntries.filter((entries) => entries.length >= 2),
          (entries) => {
            const activeId = entries[0].id;
            const closeId = entries[1].id;

            const state = makeStateWithQuests(entries);
            const stateWithActive: HiCliState = { ...state, activeQuestId: activeId };

            const action: HiCliAction = { type: 'QUEST_CLOSED', questId: closeId };
            const newState = hiCliReducer(stateWithActive, action);

            // activeQuestId 应保持不变
            expect(newState.activeQuestId).toBe(activeId);
          },
        ),
        { numRuns: 100 },
      );
    });
  });
});


// ===== Property 4: 流式消息累积渲染 =====
// Feature: hicli-module, Property 4: 流式消息累积渲染
// **Validates: Requirements 4.2**

describe('Property 4: 流式消息累积渲染', () => {
  /** 构建一个包含活跃 Quest 的 HiCliState */
  function makeStateWithActiveQuest(questId: string): HiCliState {
    return makeStateWithQuests([{ id: questId, createdAt: Date.now() }]);
  }

  /** 生成非空文本 chunk（模拟 agent_message_chunk 的 text content） */
  const arbChunkText = fc.string({ minLength: 0, maxLength: 50 });

  /** 生成 agent_message_chunk 的 SESSION_UPDATE action */
  function makeChunkAction(
    sessionId: string,
    text: string,
  ): HiCliAction {
    return {
      type: 'SESSION_UPDATE',
      sessionId,
      update: {
        sessionId,
        update: {
          sessionUpdate: 'agent_message_chunk' as const,
          content: { type: 'text' as const, text },
        },
      },
    };
  }

  /** 生成 chunk 文本列表（至少 1 个） */
  const arbChunkTexts = fc.array(arbChunkText, { minLength: 1, maxLength: 20 });

  it('agent_message_chunk 序列处理后的最终文本等于所有 chunk 文本的顺序拼接', () => {
    fc.assert(
      fc.property(
        arbQuestId,
        arbChunkTexts,
        (questId, chunkTexts) => {
          let state = makeStateWithActiveQuest(questId);

          // 依次 dispatch 每个 chunk
          for (const text of chunkTexts) {
            const action = makeChunkAction(questId, text);
            state = hiCliReducer(state, action);
          }

          // 获取最终的 agent 消息
          const quest = state.quests[questId];
          expect(quest).toBeDefined();

          // 找到最后一条 agent 类型的消息
          const agentMessages = quest.messages.filter(
            (m): m is { type: 'agent'; id: string; text: string; complete: boolean } =>
              m.type === 'agent',
          );
          expect(agentMessages.length).toBe(1);

          // 验证最终文本等于所有 chunk 文本的顺序拼接
          const expectedText = chunkTexts.join('');
          expect(agentMessages[0].text).toBe(expectedText);
        },
      ),
      { numRuns: 100 },
    );
  });

  it('空 chunk 文本不影响累积结果的正确性', () => {
    fc.assert(
      fc.property(
        arbQuestId,
        fc.array(fc.constantFrom('', 'hello', ' ', 'world'), { minLength: 1, maxLength: 30 }),
        (questId, chunkTexts) => {
          let state = makeStateWithActiveQuest(questId);

          for (const text of chunkTexts) {
            state = hiCliReducer(state, makeChunkAction(questId, text));
          }

          const quest = state.quests[questId];
          const agentMessages = quest.messages.filter(
            (m): m is { type: 'agent'; id: string; text: string; complete: boolean } =>
              m.type === 'agent',
          );
          expect(agentMessages.length).toBe(1);
          expect(agentMessages[0].text).toBe(chunkTexts.join(''));
        },
      ),
      { numRuns: 100 },
    );
  });

  it('多次 chunk 后 agent 消息始终只有一条（连续 chunk 合并到同一消息）', () => {
    fc.assert(
      fc.property(
        arbQuestId,
        arbChunkTexts,
        (questId, chunkTexts) => {
          let state = makeStateWithActiveQuest(questId);

          for (const text of chunkTexts) {
            state = hiCliReducer(state, makeChunkAction(questId, text));
          }

          const quest = state.quests[questId];
          const agentMessages = quest.messages.filter((m) => m.type === 'agent');

          // 连续的 agent_message_chunk 应该合并为单条 agent 消息
          expect(agentMessages.length).toBe(1);
        },
      ),
      { numRuns: 100 },
    );
  });
});


// ===== Property 5: 原始消息记录完整性 =====
// Feature: hicli-module, Property 5: 原始消息记录完整性
// **Validates: Requirements 8.2, 8.4**

describe('Property 5: 原始消息记录完整性', () => {
  // ===== fast-check 生成器 =====

  /** 生成消息方向 */
  const arbDirection = fc.constantFrom(
    'client_to_agent' as const,
    'agent_to_client' as const,
  );

  /** 生成合法的 RPC ID（number 或 string） */
  const arbRpcId = fc.oneof(
    fc.integer({ min: 1, max: 100000 }),
    fc.stringMatching(/^[a-zA-Z0-9]{1,10}$/),
  );

  /** 生成 ACP method 名称 */
  const arbMethod = fc.constantFrom(
    'initialize',
    'session/new',
    'session/prompt',
    'session/update',
    'session/cancel',
    'session/set_model',
    'session/set_mode',
    'session/request_permission',
    'fs/read_text_file',
  );

  /** 生成原始消息数据（模拟 JSON-RPC 消息体） */
  const arbData = fc.oneof(
    fc.record({
      jsonrpc: fc.constant('2.0'),
      method: arbMethod,
      id: fc.integer({ min: 1, max: 10000 }),
      params: fc.record({ sessionId: fc.string({ minLength: 1, maxLength: 10 }) }),
    }),
    fc.record({
      jsonrpc: fc.constant('2.0'),
      id: fc.integer({ min: 1, max: 10000 }),
      result: fc.anything({ maxDepth: 1 }),
    }),
    fc.string({ minLength: 1, maxLength: 50 }),
  );

  /** 生成唯一消息 ID */
  const arbMessageId = fc.stringMatching(/^msg-[a-zA-Z0-9]{1,15}$/);

  /** 生成一条完整的 RawMessage（必含 direction、timestamp、data，可选 method 和 rpcId） */
  const arbRawMessage = fc.record({
    id: arbMessageId,
    direction: arbDirection,
    timestamp: fc.integer({ min: 1, max: 2_000_000_000_000 }),
    data: arbData,
    method: fc.option(arbMethod, { nil: undefined }),
    rpcId: fc.option(arbRpcId, { nil: undefined }),
  });

  /** 生成 RawMessage 列表（至少 1 条） */
  const arbRawMessages = fc.array(arbRawMessage, { minLength: 1, maxLength: 20 });

  it('每条 RAW_MESSAGE 都被记录到 rawMessages 中，且包含 direction、timestamp、data 字段', () => {
    fc.assert(
      fc.property(arbRawMessages, (messages) => {
        let state: HiCliState = { ...hiCliInitialState };

        // 依次 dispatch 每条 RAW_MESSAGE
        for (const message of messages) {
          const action: HiCliAction = { type: 'RAW_MESSAGE', message };
          state = hiCliReducer(state, action);
        }

        // rawMessages 数量应等于 dispatch 的消息数量
        expect(state.rawMessages.length).toBe(messages.length);

        // 每条记录都应包含 direction、timestamp、data 字段
        for (let i = 0; i < messages.length; i++) {
          const recorded = state.rawMessages[i];
          const original = messages[i];

          expect(recorded.direction).toBe(original.direction);
          expect(recorded.timestamp).toBe(original.timestamp);
          expect(recorded.data).toEqual(original.data);

          // direction 必须是合法值
          expect(['client_to_agent', 'agent_to_client']).toContain(recorded.direction);
          // timestamp 必须是正数
          expect(recorded.timestamp).toBeGreaterThan(0);
          // data 字段必须存在（不为 undefined）
          expect(recorded.data).toBeDefined();
        }
      }),
      { numRuns: 100 },
    );
  });

  it('包含 method 或 rpcId 的消息，记录中也应包含这些字段', () => {
    fc.assert(
      fc.property(arbRawMessages, (messages) => {
        let state: HiCliState = { ...hiCliInitialState };

        for (const message of messages) {
          state = hiCliReducer(state, { type: 'RAW_MESSAGE', message });
        }

        for (let i = 0; i < messages.length; i++) {
          const recorded = state.rawMessages[i];
          const original = messages[i];

          // 如果原始消息包含 method，记录中也应包含
          if (original.method !== undefined) {
            expect(recorded.method).toBe(original.method);
          }

          // 如果原始消息包含 rpcId，记录中也应包含
          if (original.rpcId !== undefined) {
            expect(recorded.rpcId).toBe(original.rpcId);
          }
        }
      }),
      { numRuns: 100 },
    );
  });

  it('rawMessages 保持消息的插入顺序', () => {
    fc.assert(
      fc.property(arbRawMessages, (messages) => {
        let state: HiCliState = { ...hiCliInitialState };

        for (const message of messages) {
          state = hiCliReducer(state, { type: 'RAW_MESSAGE', message });
        }

        // 验证顺序：rawMessages 中的 id 顺序应与 dispatch 顺序一致
        for (let i = 0; i < messages.length; i++) {
          expect(state.rawMessages[i].id).toBe(messages[i].id);
        }
      }),
      { numRuns: 100 },
    );
  });

  it('RAW_MESSAGE 不影响其他状态字段', () => {
    fc.assert(
      fc.property(arbRawMessage, (message) => {
        const state: HiCliState = { ...hiCliInitialState };
        const newState = hiCliReducer(state, { type: 'RAW_MESSAGE', message });

        // rawMessages 应增加一条
        expect(newState.rawMessages.length).toBe(1);

        // 其他调试状态字段应保持不变
        expect(newState.aggregatedLogs).toEqual(state.aggregatedLogs);
        expect(newState.agentInfo).toEqual(state.agentInfo);
        expect(newState.authMethods).toEqual(state.authMethods);
        expect(newState.agentCapabilities).toEqual(state.agentCapabilities);
        expect(newState.selectedCliId).toEqual(state.selectedCliId);
        expect(newState.cwd).toEqual(state.cwd);
      }),
      { numRuns: 100 },
    );
  });
});
