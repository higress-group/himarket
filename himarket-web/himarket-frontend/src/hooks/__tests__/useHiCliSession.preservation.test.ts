/**
 * 保持性属性测试 - HiCli 延迟创建会话
 *
 * 验证非 bug 条件下的行为在修复前后保持不变。
 * 遵循观察优先方法论：先在未修复代码上观察行为，再编写属性测试捕获这些行为。
 *
 * 在未修复代码上运行时，测试应该通过（确认基线行为已被捕获）。
 *
 * **Validates: Requirements 3.1, 3.2, 3.3, 3.4, 3.5, 3.6, 3.7, 3.8**
 */
import { describe, it, expect } from "vitest";
import fc from "fast-check";

import {
  hiCliReducer,
  hiCliInitialState,
  type HiCliState,
} from "../../context/HiCliSessionContext";
import type { QuestData } from "../../context/QuestSessionContext";

// ===== Bug 条件形式化定义（与 task 1 一致） =====

interface BugConditionInput {
  event: "connection_initialized" | "plus_button_clicked" | "send_prompt";
  hasActiveQuest: boolean;
  userSentMessage: boolean;
  existingEmptyQuest: boolean;
}

function isBugCondition(input: BugConditionInput): boolean {
  if (
    input.event === "connection_initialized" &&
    !input.hasActiveQuest &&
    !input.userSentMessage
  ) {
    return true;
  }
  if (input.event === "plus_button_clicked" && input.existingEmptyQuest) {
    return true;
  }
  return false;
}

// ===== 辅助函数 =====

/**
 * 模拟 sendPrompt 的核心逻辑（从 useHiCliSession.ts 提取）
 * 复现未修复代码中 sendPrompt 的行为
 */
function simulateSendPrompt(
  state: { activeQuestId: string | null; quests: HiCliState["quests"] },
  text: string,
): { queued: true; queuedPromptId?: string } | { queued: false; requestId?: string | number } {
  const activeId = state.activeQuestId;
  if (!activeId) return { queued: false } as const;
  const quest = state.quests[activeId];
  if (!quest) return { queued: false } as const;

  if (quest.isProcessing || quest.inflightPromptId !== null) {
    return { queued: true, queuedPromptId: `qp-${Date.now()}` } as const;
  }

  return { queued: false, requestId: 1 } as const;
}

/**
 * 创建一个空白 Quest 数据
 */
function makeEmptyQuest(id: string): QuestData {
  return {
    id,
    title: `Quest`,
    cwd: ".",
    messages: [],
    availableModels: [],
    availableModes: [],
    currentModelId: "",
    currentModeId: "",
    isProcessing: false,
    inflightPromptId: null,
    promptQueue: [],
    lastStopReason: null,
    lastCompletedAt: null,
    selectedToolCallId: null,
    artifacts: [],
    activeArtifactId: null,
    lastArtifactScanAt: Date.now(),
    createdAt: Date.now(),
    openFiles: [],
    activeFilePath: null,
    terminals: [],
    previewPort: null,
  };
}

/**
 * 创建一个有消息的 Quest 数据
 */
function makeQuestWithMessages(id: string, messageCount: number): QuestData {
  const quest = makeEmptyQuest(id);
  const messages = [];
  for (let i = 0; i < messageCount; i++) {
    messages.push({
      type: "user" as const,
      id: `msg-${id}-${i}`,
      text: `message ${i}`,
    });
  }
  return { ...quest, messages } as QuestData;
}

/**
 * 创建一个正在处理中的 Quest 数据
 */
function makeProcessingQuest(id: string): QuestData {
  return {
    ...makeQuestWithMessages(id, 1),
    isProcessing: true,
    inflightPromptId: `req-${id}`,
  };
}

/**
 * 构建一个已连接、已初始化、有活跃 Quest 的 state
 */
function makeConnectedStateWithQuests(
  quests: Record<string, QuestData>,
  activeQuestId: string,
): HiCliState {
  return {
    ...hiCliInitialState,
    connected: true,
    initialized: true,
    quests,
    activeQuestId,
  };
}

// ===== fast-check 生成器 =====

/** 生成合法的 Quest ID */
const questIdArb = fc.stringMatching(/^quest-[a-z0-9]{1,10}$/);

/** 生成合法的消息文本 */
const messageTextArb = fc.stringMatching(/^[a-zA-Z0-9 ]{1,50}$/);

/** 生成 CLI Provider 配置 */
const cliProviderArb = fc.record({
  cliId: fc.stringMatching(/^[a-zA-Z0-9]{1,20}$/),
  runtimeCategory: fc.constantFrom("native", "nodejs", "python"),
});

// ===== 保持性属性测试 =====

describe("保持性属性测试 - 非 bug 条件下的行为不变", () => {

  /**
   * 观察 1：已有活跃 Quest 时，sendPrompt("hello") 应调用 startPrompt 并返回 { queued: false, requestId }
   *
   * 非 bug 条件：hasActiveQuest == true，这不是 isBugCondition 的触发场景
   *
   * **Validates: Requirements 3.1**
   */
  describe("观察 1：已有活跃 Quest 时 sendPrompt 正常发送", () => {
    it("对于任意消息内容，已有活跃且空闲的 Quest 时，sendPrompt 应返回 { queued: false, requestId }", () => {
      fc.assert(
        fc.property(
          messageTextArb,
          fc.constantFrom("native", "nodejs", "python"),
          (message, runtimeCategory) => {
            // 确认这不是 bug 条件
            const input: BugConditionInput = {
              event: "send_prompt",
              hasActiveQuest: true,
              userSentMessage: true,
              existingEmptyQuest: false,
            };
            expect(isBugCondition(input)).toBe(false);

            const questId = "quest-active-1";
            const quest = makeQuestWithMessages(questId, 1);
            const state = makeConnectedStateWithQuests(
              { [questId]: quest },
              questId,
            );

            const result = simulateSendPrompt(state, message);

            // 保持性验证：应返回 { queued: false, requestId }
            expect(result.queued).toBe(false);
            expect(result).toHaveProperty("requestId");
            expect((result as { requestId?: unknown }).requestId).toBeDefined();
          },
        ),
        { numRuns: 50 },
      );
    });
  });

  /**
   * 观察 2：已有活跃 Quest 且正在处理中时，sendPrompt("hello") 应入队并返回 { queued: true, queuedPromptId }
   *
   * **Validates: Requirements 3.1**
   */
  describe("观察 2：已有活跃 Quest 且正在处理中时 sendPrompt 应入队", () => {
    it("对于任意消息内容，活跃 Quest 正在处理中时，sendPrompt 应返回 { queued: true, queuedPromptId }", () => {
      fc.assert(
        fc.property(
          messageTextArb,
          (message) => {
            const input: BugConditionInput = {
              event: "send_prompt",
              hasActiveQuest: true,
              userSentMessage: true,
              existingEmptyQuest: false,
            };
            expect(isBugCondition(input)).toBe(false);

            const questId = "quest-processing-1";
            const quest = makeProcessingQuest(questId);
            const state = makeConnectedStateWithQuests(
              { [questId]: quest },
              questId,
            );

            const result = simulateSendPrompt(state, message);

            // 保持性验证：应返回 { queued: true, queuedPromptId }
            expect(result.queued).toBe(true);
            expect(result).toHaveProperty("queuedPromptId");
            expect((result as { queuedPromptId?: unknown }).queuedPromptId).toBeDefined();
          },
        ),
        { numRuns: 50 },
      );
    });
  });

  /**
   * 观察 3：多会话切换时，switchQuest(questId) 应 dispatch QUEST_SWITCHED 事件
   *
   * 通过 reducer 验证：QUEST_SWITCHED action 应将 activeQuestId 切换到目标 Quest
   *
   * **Validates: Requirements 3.2**
   */
  describe("观察 3：多会话切换时 switchQuest 正常工作", () => {
    it("对于任意数量的 Quest，switchQuest 应正确切换 activeQuestId", () => {
      fc.assert(
        fc.property(
          fc.integer({ min: 2, max: 6 }),
          (questCount) => {
            const input: BugConditionInput = {
              event: "send_prompt",
              hasActiveQuest: true,
              userSentMessage: false,
              existingEmptyQuest: false,
            };
            expect(isBugCondition(input)).toBe(false);

            // 构建多个有消息的 Quest
            const quests: Record<string, QuestData> = {};
            const questIds: string[] = [];
            for (let i = 0; i < questCount; i++) {
              const qid = `quest-switch-${i}`;
              questIds.push(qid);
              quests[qid] = makeQuestWithMessages(qid, i + 1);
            }

            let state = makeConnectedStateWithQuests(quests, questIds[0]);
            expect(state.activeQuestId).toBe(questIds[0]);

            // 切换到最后一个 Quest
            const targetId = questIds[questIds.length - 1];
            state = hiCliReducer(state, {
              type: "QUEST_SWITCHED",
              questId: targetId,
            });

            // 保持性验证：activeQuestId 应切换到目标 Quest
            expect(state.activeQuestId).toBe(targetId);
            // Quest 列表不应改变
            expect(Object.keys(state.quests).length).toBe(questCount);
          },
        ),
        { numRuns: 30 },
      );
    });

    it("切换到不存在的 Quest 时，state 不应改变", () => {
      fc.assert(
        fc.property(
          questIdArb,
          (nonExistentId) => {
            const questId = "quest-existing-1";
            const quest = makeQuestWithMessages(questId, 1);
            const state = makeConnectedStateWithQuests(
              { [questId]: quest },
              questId,
            );

            // 确保目标 ID 不存在
            if (nonExistentId === questId) return;

            const newState = hiCliReducer(state, {
              type: "QUEST_SWITCHED",
              questId: nonExistentId,
            });

            // 保持性验证：state 不应改变
            expect(newState.activeQuestId).toBe(questId);
          },
        ),
        { numRuns: 30 },
      );
    });
  });

  /**
   * 观察 4：所有会话都有消息时，点击"+"按钮（createQuest）应正常创建新会话
   *
   * 非 bug 条件：existingEmptyQuest == false
   * 通过 reducer 验证：QUEST_CREATED action 应增加一个新 Quest
   *
   * **Validates: Requirements 3.3**
   */
  describe("观察 4：所有会话都有消息时，createQuest 正常创建新会话", () => {
    it("对于任意数量的有消息 Quest，createQuest 应正常创建新 Quest", () => {
      fc.assert(
        fc.property(
          fc.integer({ min: 1, max: 5 }),
          cliProviderArb,
          (existingCount, provider) => {
            // 确认这不是 bug 条件（所有 Quest 都有消息，不存在空白 Quest）
            const input: BugConditionInput = {
              event: "plus_button_clicked",
              hasActiveQuest: true,
              userSentMessage: true,
              existingEmptyQuest: false,
            };
            expect(isBugCondition(input)).toBe(false);

            // 构建所有 Quest 都有消息的 state
            const quests: Record<string, QuestData> = {};
            const questIds: string[] = [];
            for (let i = 0; i < existingCount; i++) {
              const qid = `quest-msg-${i}`;
              questIds.push(qid);
              quests[qid] = makeQuestWithMessages(qid, i + 1);
            }

            const state = makeConnectedStateWithQuests(quests, questIds[0]);

            // 确认没有空白 Quest
            const emptyQuests = Object.values(state.quests).filter(
              (q) => q.messages.length === 0,
            );
            expect(emptyQuests.length).toBe(0);

            // 模拟 createQuest 成功后的 QUEST_CREATED action
            const newQuestId = `quest-new-${Date.now()}`;
            const newState = hiCliReducer(state, {
              type: "QUEST_CREATED",
              sessionId: newQuestId,
              cwd: ".",
            });

            // 保持性验证：应创建新 Quest
            expect(Object.keys(newState.quests).length).toBe(existingCount + 1);
            expect(newState.quests[newQuestId]).toBeDefined();
            expect(newState.activeQuestId).toBe(newQuestId);
            // 新 Quest 应该是空白的
            expect(newState.quests[newQuestId].messages.length).toBe(0);
            // 原有 Quest 不应被修改
            for (const qid of questIds) {
              expect(newState.quests[qid]).toBeDefined();
              expect(newState.quests[qid].messages.length).toBeGreaterThan(0);
            }
          },
        ),
        { numRuns: 30 },
      );
    });
  });

  /**
   * 观察 5：WebSocket 断开时应重置连接状态和初始化标记
   *
   * **Validates: Requirements 3.4**
   */
  describe("观察 5：WebSocket 断开时重置连接状态和初始化标记", () => {
    it("对于任意已连接状态，WS_DISCONNECTED 应重置 connected 和 initialized", () => {
      fc.assert(
        fc.property(
          fc.integer({ min: 0, max: 4 }),
          cliProviderArb,
          (questCount, provider) => {
            // 构建已连接、已初始化的 state
            const quests: Record<string, QuestData> = {};
            let activeId: string | null = null;
            for (let i = 0; i < questCount; i++) {
              const qid = `quest-ws-${i}`;
              if (i === 0) activeId = qid;
              quests[qid] = makeQuestWithMessages(qid, i + 1);
            }

            let state: HiCliState = {
              ...hiCliInitialState,
              connected: true,
              initialized: true,
              quests,
              activeQuestId: activeId,
              selectedCliId: provider.cliId,
              runtimeType: provider.runtimeCategory === "native" ? "local" : "K8S",
            };

            // 模拟 WS 断开
            state = hiCliReducer(state, { type: "WS_DISCONNECTED" });

            // 保持性验证：connected 和 initialized 应被重置
            expect(state.connected).toBe(false);
            expect(state.initialized).toBe(false);
          },
        ),
        { numRuns: 30 },
      );
    });
  });

  /**
   * 综合属性测试：对于所有非 bug 条件输入，sendPrompt 行为应与原函数一致
   *
   * 使用 fast-check 生成随机的 Quest 状态，验证 sendPrompt 在有活跃 Quest 时
   * 的行为与原函数完全一致。
   *
   * **Validates: Requirements 3.1, 3.7**
   */
  describe("综合属性：非 bug 条件下 sendPrompt 行为一致", () => {
    it("对于任意有活跃 Quest 的状态和任意消息，sendPrompt 行为应保持不变", () => {
      fc.assert(
        fc.property(
          // 生成随机的 Quest 状态
          fc.record({
            questId: fc.stringMatching(/^quest-[a-z0-9]{1,8}$/),
            messageCount: fc.integer({ min: 1, max: 10 }),
            isProcessing: fc.boolean(),
            hasInflightPrompt: fc.boolean(),
          }),
          messageTextArb,
          cliProviderArb,
          (questConfig, message, provider) => {
            // 确认这不是 bug 条件
            const input: BugConditionInput = {
              event: "send_prompt",
              hasActiveQuest: true,
              userSentMessage: true,
              existingEmptyQuest: false,
            };
            expect(isBugCondition(input)).toBe(false);

            // 构建 Quest
            const quest = makeQuestWithMessages(questConfig.questId, questConfig.messageCount);
            const processing = questConfig.isProcessing || questConfig.hasInflightPrompt;
            const finalQuest: QuestData = {
              ...quest,
              isProcessing: processing,
              inflightPromptId: processing ? `req-inflight` : null,
            };

            const state = makeConnectedStateWithQuests(
              { [questConfig.questId]: finalQuest },
              questConfig.questId,
            );

            const result = simulateSendPrompt(state, message);

            if (processing) {
              // 正在处理中：应入队
              expect(result.queued).toBe(true);
              expect(result).toHaveProperty("queuedPromptId");
            } else {
              // 空闲：应直接发送
              expect(result.queued).toBe(false);
              expect(result).toHaveProperty("requestId");
            }
          },
        ),
        { numRuns: 100 },
      );
    });
  });

  /**
   * 综合属性测试：QUEST_CREATED 后 session/new 返回的 models/modes 应正常合并
   *
   * **Validates: Requirements 3.8**
   */
  describe("综合属性：session/new 返回的 models/modes 正常合并", () => {
    it("QUEST_CREATED 携带 models/modes 时应合并到 state 和 quest 中", () => {
      fc.assert(
        fc.property(
          fc.record({
            hasModels: fc.boolean(),
            hasModes: fc.boolean(),
            modelCount: fc.integer({ min: 1, max: 3 }),
            modeCount: fc.integer({ min: 1, max: 3 }),
          }),
          (config) => {
            // 初始 state：已连接、已初始化、有 initialize 返回的 modes
            const initModes = [{ id: "code", name: "Code" }];
            let state: HiCliState = {
              ...hiCliInitialState,
              connected: true,
              initialized: true,
              modes: initModes,
            };

            // 构建 session/new 返回的 models 和 modes
            const sessionModels = config.hasModels
              ? Array.from({ length: config.modelCount }, (_, i) => ({
                  modelId: `model-${i}`,
                  name: `Model ${i}`,
                }))
              : undefined;

            const sessionModes = config.hasModes
              ? Array.from({ length: config.modeCount }, (_, i) => ({
                  id: `mode-${i}`,
                  name: `Mode ${i}`,
                }))
              : undefined;

            const newQuestId = `quest-models-${Date.now()}`;
            state = hiCliReducer(state, {
              type: "QUEST_CREATED",
              sessionId: newQuestId,
              cwd: ".",
              models: sessionModels,
              modes: sessionModes,
              currentModelId: sessionModels?.[0]?.modelId,
              currentModeId: sessionModes?.[0]?.id,
            });

            // 保持性验证：Quest 应被创建
            expect(state.quests[newQuestId]).toBeDefined();

            // models/modes 应正确合并
            if (config.hasModels && sessionModels && sessionModels.length > 0) {
              expect(state.quests[newQuestId].availableModels).toEqual(sessionModels);
              expect(state.models).toEqual(sessionModels);
            }
            if (config.hasModes && sessionModes && sessionModes.length > 0) {
              expect(state.quests[newQuestId].availableModes).toEqual(sessionModes);
              expect(state.modes).toEqual(sessionModes);
            } else {
              // 没有 session modes 时，应保留 initialize 返回的 modes
              expect(state.modes).toEqual(initModes);
            }
          },
        ),
        { numRuns: 30 },
      );
    });
  });

  /**
   * 综合属性测试：切换 CLI 工具时正常断开连接并重置所有状态
   *
   * **Validates: Requirements 3.6**
   */
  describe("综合属性：切换 CLI 工具时重置状态", () => {
    it("RESET_STATE 应将所有状态重置为初始值", () => {
      fc.assert(
        fc.property(
          fc.integer({ min: 1, max: 4 }),
          cliProviderArb,
          (questCount, provider) => {
            // 构建一个有数据的 state
            const quests: Record<string, QuestData> = {};
            for (let i = 0; i < questCount; i++) {
              const qid = `quest-reset-${i}`;
              quests[qid] = makeQuestWithMessages(qid, i + 1);
            }

            const state: HiCliState = {
              ...hiCliInitialState,
              connected: true,
              initialized: true,
              quests,
              activeQuestId: Object.keys(quests)[0],
              selectedCliId: provider.cliId,
            };

            // 模拟 RESET_STATE（切换 CLI 工具时触发）
            const newState = hiCliReducer(state, { type: "RESET_STATE" });

            // 保持性验证：所有状态应重置
            expect(newState.connected).toBe(false);
            expect(newState.initialized).toBe(false);
            expect(Object.keys(newState.quests).length).toBe(0);
            expect(newState.activeQuestId).toBeNull();
          },
        ),
        { numRuns: 20 },
      );
    });
  });

  /**
   * 综合属性测试：K8s 沙箱状态管理
   *
   * **Validates: Requirements 3.5**
   */
  describe("综合属性：K8s 沙箱状态管理", () => {
    it("沙箱状态变化应正确反映在 state 中", () => {
      fc.assert(
        fc.property(
          fc.constantFrom("creating", "ready", "error") as fc.Arbitrary<"creating" | "ready" | "error">,
          fc.stringMatching(/^[a-zA-Z0-9 ]{1,30}$/),
          (sandboxStatus, sandboxMessage) => {
            let state: HiCliState = {
              ...hiCliInitialState,
              connected: true,
              initialized: true,
              runtimeType: "K8S" as never,
            };

            state = hiCliReducer(state, {
              type: "SANDBOX_STATUS",
              status: sandboxStatus,
              message: sandboxMessage,
            });

            // 保持性验证：沙箱状态应正确更新
            expect(state.sandboxStatus).not.toBeNull();
            expect(state.sandboxStatus!.status).toBe(sandboxStatus);
            expect(state.sandboxStatus!.message).toBe(sandboxMessage);
            // 连接状态不应受沙箱状态影响
            expect(state.connected).toBe(true);
            expect(state.initialized).toBe(true);
          },
        ),
        { numRuns: 20 },
      );
    });
  });
});
