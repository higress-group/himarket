import { describe, expect, it } from "vitest";
import {
  codingReducer,
  initialState,
  type CodingState,
  type QueuedPromptItem,
} from "./CodingSessionContext";

function buildQueueItem(id: string, text: string): QueuedPromptItem {
  return { id, text, createdAt: Date.now() };
}

function createQuestState(): CodingState {
  return codingReducer(initialState, {
    type: "QUEST_CREATED",
    sessionId: "q1",
    cwd: ".",
    models: [{ modelId: "m1", name: "M1" }],
    modes: [{ id: "mode-1", name: "Mode 1" }],
    currentModelId: "m1",
    currentModeId: "mode-1",
  });
}

describe("codingReducer prompt queue state machine", () => {
  it("supports single-flight prompt + queue", () => {
    let state = createQuestState();

    state = codingReducer(state, {
      type: "PROMPT_STARTED",
      questId: "q1",
      requestId: 1,
      text: "first",
    });
    state = codingReducer(state, {
      type: "PROMPT_ENQUEUED",
      questId: "q1",
      item: buildQueueItem("qp-2", "second"),
    });
    state = codingReducer(state, {
      type: "PROMPT_ENQUEUED",
      questId: "q1",
      item: buildQueueItem("qp-3", "third"),
    });

    const q1 = state.quests.q1;
    expect(q1.inflightPromptId).toBe(1);
    expect(q1.isProcessing).toBe(true);
    expect(q1.promptQueue.map(item => item.id)).toEqual(["qp-2", "qp-3"]);

    state = codingReducer(state, {
      type: "PROMPT_COMPLETED",
      questId: "q1",
      requestId: 1,
      stopReason: "completed",
    });
    expect(state.quests.q1.isProcessing).toBe(false);
    expect(state.quests.q1.inflightPromptId).toBeNull();
    expect(state.quests.q1.promptQueue.length).toBe(2);

    state = codingReducer(state, {
      type: "PROMPT_STARTED",
      questId: "q1",
      requestId: 2,
      promptId: "qp-2",
      text: "second",
    });
    expect(state.quests.q1.inflightPromptId).toBe(2);
    expect(state.quests.q1.promptQueue.map(item => item.id)).toEqual(["qp-3"]);

    state = codingReducer(state, {
      type: "PROMPT_COMPLETED",
      questId: "q1",
      requestId: 2,
      stopReason: "completed",
    });
    state = codingReducer(state, {
      type: "PROMPT_STARTED",
      questId: "q1",
      requestId: 3,
      promptId: "qp-3",
      text: "third",
    });
    state = codingReducer(state, {
      type: "PROMPT_COMPLETED",
      questId: "q1",
      requestId: 3,
      stopReason: "completed",
    });

    const end = state.quests.q1;
    expect(end.isProcessing).toBe(false);
    expect(end.inflightPromptId).toBeNull();
    expect(end.promptQueue).toEqual([]);
    expect(end.lastStopReason).toBe("completed");
  });

  it("ignores stale completion from non-inflight request", () => {
    let state = createQuestState();
    state = codingReducer(state, {
      type: "PROMPT_STARTED",
      questId: "q1",
      requestId: 100,
      text: "only",
    });

    state = codingReducer(state, {
      type: "PROMPT_COMPLETED",
      questId: "q1",
      requestId: 999,
      stopReason: "error",
    });

    const q1 = state.quests.q1;
    expect(q1.isProcessing).toBe(true);
    expect(q1.inflightPromptId).toBe(100);
  });
});
