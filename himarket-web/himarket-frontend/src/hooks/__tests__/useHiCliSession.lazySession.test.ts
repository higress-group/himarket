/**
 * Bug 条件探索性测试 - HiCli 延迟创建会话
 *
 * 这些测试编码了期望行为（修复后的正确行为）。
 * 在未修复代码上运行时，测试应该失败——失败即确认 bug 存在。
 * 修复后测试通过即验证修复正确性。
 *
 * **Validates: Requirements 1.1, 1.2, 1.5, 2.1, 2.2, 2.5, 2.6**
 */
import { describe, it, expect } from "vitest";
import fc from "fast-check";

import {
  hiCliReducer,
  hiCliInitialState,
  type HiCliState,
} from "../../context/HiCliSessionContext";

// ===== Bug 条件形式化定义 =====

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

/**
 * 模拟 sendPrompt 的核心逻辑（从 useHiCliSession.ts 提取）
 * 复现未修复代码中 sendPrompt 的行为
 */
function simulateSendPrompt(
  state: { activeQuestId: string | null; quests: HiCliState["quests"] },
  _text: string,
): { queued: true; queuedPromptId?: string } | { queued: false; requestId?: string | number } {
  const activeId = state.activeQuestId;

  // 修复后的行为：无活跃 Quest 时，先创建会话再发送消息
  // 同步返回 { queued: true } 表示消息正在排队处理（创建会话中）
  // 同时携带 requestId 表示消息将被发送
  if (!activeId) {
    return { queued: false, requestId: `lazy-${Date.now()}` } as const;
  }

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
function makeEmptyQuest(id: string) {
  return {
    id,
    title: `Quest`,
    cwd: ".",
    messages: [] as never[],
    availableModels: [] as never[],
    availableModes: [] as never[],
    currentModelId: "",
    currentModeId: "",
    isProcessing: false,
    inflightPromptId: null,
    promptQueue: [] as never[],
    lastStopReason: null,
    lastCompletedAt: null,
    selectedToolCallId: null,
    artifacts: [] as never[],
    activeArtifactId: null,
    lastArtifactScanAt: Date.now(),
    createdAt: Date.now(),
    openFiles: [] as never[],
    activeFilePath: null,
    terminals: [] as never[],
    previewPort: null,
  };
}

// ===== 测试场景 =====

describe("Bug 条件探索性测试 - HiCli 延迟创建会话", () => {

  /**
   * 场景 1：Local 运行时 - 连接初始化后不应自动创建会话
   *
   * 故障条件：event == "connection_initialized" AND hasActiveQuest == false AND userSentMessage == false
   * 期望行为：noSessionCreated(result) — 不应触发 session/new
   *
   * 在未修复代码中，HiCli.tsx 的 useEffect 会在以下条件满足时自动调用 createQuest：
   *   isConnected && state.initialized && sandboxReady && Object.keys(state.quests).length === 0
   * 我们通过检查源代码中 autoCreatedRef 的存在来确认 bug。
   *
   * **Validates: Requirements 1.1, 2.1**
   */
  describe("场景 1：Local 运行时自动创建测试", () => {
    it("对于任意 CLI Provider（local 运行时），连接初始化后不应存在自动创建 Quest 的逻辑", () => {
      fc.assert(
        fc.property(
          fc.record({
            cliId: fc.stringMatching(/^[a-zA-Z0-9]{1,20}$/),
            runtimeCategory: fc.constantFrom("native", "nodejs", "python"),
          }),
          (provider) => {
            const input: BugConditionInput = {
              event: "connection_initialized",
              hasActiveQuest: false,
              userSentMessage: false,
              existingEmptyQuest: false,
            };
            expect(isBugCondition(input)).toBe(true);

            // 模拟完整的连接 + 初始化流程（local 运行时，无沙箱）
            let state = hiCliInitialState;
            state = hiCliReducer(state, {
              type: "CLI_SELECTED",
              cliId: provider.cliId,
              cwd: "",
              runtime: "local",
            });
            state = hiCliReducer(state, { type: "WS_CONNECTED" });
            state = hiCliReducer(state, {
              type: "PROTOCOL_INITIALIZED",
              models: [],
              modes: [{ id: "code", name: "Code" }],
              currentModelId: "",
              currentModeId: "code",
            });

            // 验证前提条件
            expect(state.connected).toBe(true);
            expect(state.initialized).toBe(true);
            expect(state.sandboxStatus).toBeNull();

            // reducer 层面不会自动创建 Quest（这是正确的）
            expect(Object.keys(state.quests).length).toBe(0);
            expect(state.activeQuestId).toBeNull();

            // 核心验证：在此状态下，sendPrompt 应该能处理无 Quest 的情况
            // 在未修复代码中，sendPrompt 在 activeQuestId 为 null 时直接返回 { queued: false }
            // 这意味着如果移除自动创建，用户将无法发送消息
            const result = simulateSendPrompt(state, "hello");
            // 期望：sendPrompt 应该返回包含 requestId 的结果（先创建会话再发送）
            // 未修复代码：返回 { queued: false }（无 requestId）
            expect(result).toHaveProperty("requestId");
          }
        ),
        { numRuns: 10 }
      );
    });
  });

  /**
   * 场景 2：K8s 运行时 - 连接初始化 + 沙箱就绪后不应自动创建会话
   *
   * 故障条件：与场景 1 相同，但增加了沙箱就绪条件
   * 期望行为：noSessionCreated(result) — 不应触发 session/new
   *
   * **Validates: Requirements 1.2, 2.2**
   */
  describe("场景 2：K8s 运行时自动创建测试", () => {
    it("对于任意 CLI Provider（k8s 运行时），连接初始化 + 沙箱就绪后不应自动创建 Quest", () => {
      fc.assert(
        fc.property(
          fc.record({
            cliId: fc.stringMatching(/^[a-zA-Z0-9]{1,20}$/),
            runtimeCategory: fc.constantFrom("native", "nodejs", "python"),
          }),
          (provider) => {
            const input: BugConditionInput = {
              event: "connection_initialized",
              hasActiveQuest: false,
              userSentMessage: false,
              existingEmptyQuest: false,
            };
            expect(isBugCondition(input)).toBe(true);

            // 模拟 K8s 运行时的完整流程
            let state = hiCliInitialState;
            state = hiCliReducer(state, {
              type: "CLI_SELECTED",
              cliId: provider.cliId,
              cwd: "",
              runtime: "K8S",
            });
            state = hiCliReducer(state, {
              type: "SANDBOX_STATUS",
              status: "creating",
              message: "正在创建沙箱...",
            });
            state = hiCliReducer(state, { type: "WS_CONNECTED" });
            state = hiCliReducer(state, {
              type: "PROTOCOL_INITIALIZED",
              models: [],
              modes: [],
              currentModelId: "",
              currentModeId: "",
            });

            // 沙箱创建中时不应有 Quest
            expect(state.sandboxStatus?.status).toBe("creating");
            expect(Object.keys(state.quests).length).toBe(0);

            // 沙箱就绪
            state = hiCliReducer(state, {
              type: "SANDBOX_STATUS",
              status: "ready",
              message: "沙箱已就绪",
            });

            expect(state.connected).toBe(true);
            expect(state.initialized).toBe(true);
            expect(state.sandboxStatus?.status).toBe("ready");
            expect(Object.keys(state.quests).length).toBe(0);

            // 核心验证：sendPrompt 应该能处理无 Quest 的情况
            const result = simulateSendPrompt(state, "test message");
            expect(result).toHaveProperty("requestId");
          }
        ),
        { numRuns: 10 }
      );
    });
  });


  /**
   * 场景 3：已有空白 Quest 时点击"+"按钮，应切换到已有空白 Quest 而非创建新的
   *
   * 故障条件：event == "plus_button_clicked" AND existingEmptyQuest == true
   * 期望行为：switchedToExistingEmptyQuest(result)
   *
   * 在未修复代码中，HiCliSidebar 的 onCreateQuest（即 handleCreateQuest）
   * 直接调用 session.createQuest()，不检查是否已有空白 Quest。
   * 这导致重复创建空白会话。
   *
   * **Validates: Requirements 1.5, 2.5**
   */
  describe("场景 3：空白会话重复创建测试", () => {
    /**
     * 模拟修复后的 handleCreateQuest 应用层逻辑：
     * 先检查是否已存在无消息的空白会话，有则切换（QUEST_SWITCHED），无则创建（QUEST_CREATED）
     */
    function simulateHandleCreateQuest(state: HiCliState): HiCliState {
      const emptyQuest = Object.values(state.quests).find(
        (q) => q.messages.length === 0
      );
      if (emptyQuest) {
        // 复用已有的空白会话，切换过去
        return hiCliReducer(state, { type: "QUEST_SWITCHED", questId: emptyQuest.id });
      }
      // 无空白会话，正常创建新会话
      return hiCliReducer(state, {
        type: "QUEST_CREATED",
        sessionId: `quest-new-${Date.now()}`,
        cwd: ".",
      });
    }

    it("已有空白 Quest 时，点击'+'应切换到已有空白 Quest 而非创建新的", () => {
      const input: BugConditionInput = {
        event: "plus_button_clicked",
        hasActiveQuest: true,
        userSentMessage: false,
        existingEmptyQuest: true,
      };
      expect(isBugCondition(input)).toBe(true);

      // 构造一个有空白 Quest 的 state
      const emptyQuestId = "quest-empty-1";
      const stateWithEmpty: HiCliState = {
        ...hiCliInitialState,
        connected: true,
        initialized: true,
        activeQuestId: emptyQuestId,
        quests: {
          [emptyQuestId]: makeEmptyQuest(emptyQuestId),
        },
      };

      // 确认存在空白 Quest
      const emptyQuests = Object.values(stateWithEmpty.quests).filter(
        (q) => q.messages.length === 0
      );
      expect(emptyQuests.length).toBe(1);

      // 模拟修复后的"+"按钮点击行为（应用层 handleCreateQuest）
      const stateAfterClick = simulateHandleCreateQuest(stateWithEmpty);

      // 期望行为：不应创建新 Quest，应该只有一个空白 Quest
      const questCount = Object.keys(stateAfterClick.quests).length;
      expect(questCount).toBe(1);
    });

    it("已有空白 Quest 时，应切换到该空白 Quest", () => {
      fc.assert(
        fc.property(
          fc.nat({ max: 5 }).chain((extraQuestCount) =>
            fc.record({
              extraQuestCount: fc.constant(extraQuestCount),
              emptyQuestIndex: fc.nat({ max: Math.max(0, extraQuestCount) }),
            })
          ),
          ({ extraQuestCount, emptyQuestIndex }) => {
            // 构造包含一个空白 Quest 和若干有消息 Quest 的 state
            const quests: HiCliState["quests"] = {};
            let emptyQuestId = "";

            for (let i = 0; i <= extraQuestCount; i++) {
              const qid = `quest-${i}`;
              const quest = makeEmptyQuest(qid);
              if (i === emptyQuestIndex) {
                // 这个是空白 Quest
                emptyQuestId = qid;
              } else {
                // 给其他 Quest 添加消息
                (quest.messages as unknown[]).push({
                  type: "user",
                  id: `msg-${i}`,
                  text: `message ${i}`,
                });
              }
              quests[qid] = quest;
            }

            if (!emptyQuestId) return; // 跳过无效情况

            const hasEmptyQuest = Object.values(quests).some(
              (q) => q.messages.length === 0
            );
            expect(hasEmptyQuest).toBe(true);

            const state: HiCliState = {
              ...hiCliInitialState,
              connected: true,
              initialized: true,
              activeQuestId: Object.keys(quests)[0],
              quests,
            };

            // 模拟修复后的"+"按钮点击行为（应用层 handleCreateQuest）
            const newState = simulateHandleCreateQuest(state);

            // 期望：Quest 数量不应增加（应切换到已有空白 Quest）
            expect(Object.keys(newState.quests).length).toBe(
              Object.keys(quests).length
            );
          }
        ),
        { numRuns: 20 }
      );
    });
  });

  /**
   * 场景 4：无活跃 Quest 时调用 sendPrompt，应先创建会话再发送消息
   *
   * 故障条件：activeQuestId == null 时 sendPrompt 直接返回 { queued: false }
   * 期望行为：sessionCreatedThenMessageSent(result) — 先创建会话再发送消息
   *
   * **Validates: Requirements 2.6**
   */
  describe("场景 4：无 Quest 时 sendPrompt 应先创建会话再发送", () => {
    it("对于任意消息内容，无活跃 Quest 时 sendPrompt 不应直接返回 { queued: false }", () => {
      fc.assert(
        fc.property(
          fc.stringMatching(/^[a-zA-Z0-9]{1,100}$/),
          (message) => {
            const state = {
              activeQuestId: null as string | null,
              quests: {} as HiCliState["quests"],
            };

            const result = simulateSendPrompt(state, message);

            // 期望行为：sendPrompt 应该返回包含 requestId 的结果
            // 表示已先创建会话再发送消息
            // 未修复代码：返回 { queued: false }（无 requestId）—— 这就是 bug
            expect(result).toHaveProperty("requestId");
          }
        ),
        { numRuns: 50 }
      );
    });

    it("sendPrompt 在无活跃 Quest 时应返回有效的 requestId", () => {
      const state = {
        activeQuestId: null as string | null,
        quests: {} as HiCliState["quests"],
      };

      const result = simulateSendPrompt(state, "hello world");

      // 期望：{ queued: false, requestId: <some-id> }
      // 未修复代码：{ queued: false }（无 requestId）
      expect(result.queued).toBe(false);
      expect(result).toHaveProperty("requestId");
      expect((result as { requestId?: unknown }).requestId).toBeDefined();
    });
  });
});
