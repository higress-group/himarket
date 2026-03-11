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

function createCodingState(): CodingState {
  return codingReducer(initialState, {
    type: "SESSION_CREATED",
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
    let state = createCodingState();

    state = codingReducer(state, {
      type: "PROMPT_STARTED",
      sessionId: "q1",
      requestId: 1,
      text: "first",
    });
    state = codingReducer(state, {
      type: "PROMPT_ENQUEUED",
      sessionId: "q1",
      item: buildQueueItem("qp-2", "second"),
    });
    state = codingReducer(state, {
      type: "PROMPT_ENQUEUED",
      sessionId: "q1",
      item: buildQueueItem("qp-3", "third"),
    });

    const q1 = state.sessions.q1;
    expect(q1.inflightPromptId).toBe(1);
    expect(q1.isProcessing).toBe(true);
    expect(q1.promptQueue.map(item => item.id)).toEqual(["qp-2", "qp-3"]);

    state = codingReducer(state, {
      type: "PROMPT_COMPLETED",
      sessionId: "q1",
      requestId: 1,
      stopReason: "completed",
    });
    expect(state.sessions.q1.isProcessing).toBe(false);
    expect(state.sessions.q1.inflightPromptId).toBeNull();
    expect(state.sessions.q1.promptQueue.length).toBe(2);

    state = codingReducer(state, {
      type: "PROMPT_STARTED",
      sessionId: "q1",
      requestId: 2,
      promptId: "qp-2",
      text: "second",
    });
    expect(state.sessions.q1.inflightPromptId).toBe(2);
    expect(state.sessions.q1.promptQueue.map(item => item.id)).toEqual(["qp-3"]);

    state = codingReducer(state, {
      type: "PROMPT_COMPLETED",
      sessionId: "q1",
      requestId: 2,
      stopReason: "completed",
    });
    state = codingReducer(state, {
      type: "PROMPT_STARTED",
      sessionId: "q1",
      requestId: 3,
      promptId: "qp-3",
      text: "third",
    });
    state = codingReducer(state, {
      type: "PROMPT_COMPLETED",
      sessionId: "q1",
      requestId: 3,
      stopReason: "completed",
    });

    const end = state.sessions.q1;
    expect(end.isProcessing).toBe(false);
    expect(end.inflightPromptId).toBeNull();
    expect(end.promptQueue).toEqual([]);
    expect(end.lastStopReason).toBe("completed");
  });

  it("ignores stale completion from non-inflight request", () => {
    let state = createCodingState();
    state = codingReducer(state, {
      type: "PROMPT_STARTED",
      sessionId: "q1",
      requestId: 100,
      text: "only",
    });

    state = codingReducer(state, {
      type: "PROMPT_COMPLETED",
      sessionId: "q1",
      requestId: 999,
      stopReason: "error",
    });

    const q1 = state.sessions.q1;
    expect(q1.isProcessing).toBe(true);
    expect(q1.inflightPromptId).toBe(100);
  });
});

// Feature: error-response-handling, Property 1: PROMPT_ERROR action correctly updates reducer state
import fc from "fast-check";

describe("codingReducer PROMPT_ERROR property-based tests", () => {
  /**
   * Property 1: PROMPT_ERROR action correctly updates reducer state
   */
  it("PROMPT_ERROR action correctly updates reducer state for any code/message/data", () => {
    fc.assert(
      fc.property(
        fc.integer(),
        fc.string({ minLength: 1 }),
        fc.option(
          fc.dictionary(fc.string({ minLength: 1 }), fc.jsonValue()),
          { nil: undefined }
        ),
        fc.oneof(fc.integer({ min: 1 }), fc.string({ minLength: 1 })),
        (code, message, data, requestId) => {
          let state = createCodingState();
          state = codingReducer(state, {
            type: "PROMPT_STARTED",
            sessionId: "q1",
            requestId,
            text: "test prompt",
          });

          const prevMessages = state.sessions.q1.messages;
          const prevLength = prevMessages.length;

          const nextState = codingReducer(state, {
            type: "PROMPT_ERROR",
            sessionId: "q1",
            requestId,
            code,
            message,
            ...(data !== undefined ? { data: data as Record<string, unknown> } : {}),
          });

          const session = nextState.sessions.q1;

          expect(session.messages.length).toBe(prevLength + 1);

          const lastMsg = session.messages[session.messages.length - 1];
          expect(lastMsg.type).toBe("error");
          if (lastMsg.type === "error") {
            expect(lastMsg.code).toBe(code);
            expect(lastMsg.message).toBe(message);
          }

          expect(session.isProcessing).toBe(false);
          expect(session.inflightPromptId).toBeNull();
        }
      ),
      { numRuns: 100 }
    );
  });
});

// Feature: error-response-handling, Property 2: Stale request PROMPT_ERROR is ignored
describe("codingReducer PROMPT_ERROR stale request property-based tests", () => {
  /**
   * Property 2: Stale request PROMPT_ERROR is ignored
   */
  it("PROMPT_ERROR with mismatched requestId leaves state unchanged", () => {
    fc.assert(
      fc.property(
        fc.oneof(fc.integer({ min: 1 }), fc.string({ minLength: 1 })),
        fc.integer(),
        fc.string({ minLength: 1 }),
        (inflightId, code, message) => {
          let state = createCodingState();
          state = codingReducer(state, {
            type: "PROMPT_STARTED",
            sessionId: "q1",
            requestId: inflightId,
            text: "test prompt",
          });

          const sessionBefore = state.sessions.q1;

          const staleRequestId =
            typeof inflightId === "number"
              ? inflightId + 1
              : inflightId + "_stale";

          const nextState = codingReducer(state, {
            type: "PROMPT_ERROR",
            sessionId: "q1",
            requestId: staleRequestId,
            code,
            message,
          });

          const sessionAfter = nextState.sessions.q1;

          expect(sessionAfter.messages).toEqual(sessionBefore.messages);
          expect(sessionAfter.messages.length).toBe(sessionBefore.messages.length);
          expect(sessionAfter.isProcessing).toBe(sessionBefore.isProcessing);
          expect(sessionAfter.inflightPromptId).toBe(
            sessionBefore.inflightPromptId
          );
        }
      ),
      { numRuns: 100 }
    );
  });
});


// Feature: error-response-handling, Property 5: Error info end-to-end fidelity
import { trackRequest, resolveResponse, clearPendingRequests } from "../lib/utils/codingProtocol";
import { JSONRPC_VERSION, type CodingResponse, type ChatItemError } from "../types/coding-protocol";

describe("End-to-end error info fidelity property-based tests", () => {
  /**
   * Property 5: Error info end-to-end fidelity
   */
  it("error code and message are preserved through the full resolveResponse → catch → reducer chain", () => {
    fc.assert(
      fc.property(
        fc.oneof(fc.integer({ min: 1 }), fc.string({ minLength: 1 })),
        fc.integer(),
        fc.string({ minLength: 1 }),
        fc.option(
          fc.dictionary(fc.string({ minLength: 1 }), fc.jsonValue()),
          { nil: undefined }
        ),
        (requestId, code, message, data) => {
          clearPendingRequests();

          const codingErrorResponse: CodingResponse = {
            jsonrpc: JSONRPC_VERSION,
            id: requestId,
            error: {
              code,
              message,
              ...(data !== undefined ? { data: data as Record<string, unknown> } : {}),
            },
          };

          const promise = trackRequest(requestId);
          promise.catch(() => {
            // error captured via codingErrorResponse.error below
          });

          resolveResponse(codingErrorResponse);

          const errorFromResponse = codingErrorResponse.error!;

          let state = createCodingState();
          state = codingReducer(state, {
            type: "PROMPT_STARTED",
            sessionId: "q1",
            requestId,
            text: "test prompt",
          });

          const nextState = codingReducer(state, {
            type: "PROMPT_ERROR",
            sessionId: "q1",
            requestId,
            code: errorFromResponse.code,
            message: errorFromResponse.message,
            ...(errorFromResponse.data ? { data: errorFromResponse.data } : {}),
          });

          const session = nextState.sessions.q1;
          const lastMsg = session.messages[session.messages.length - 1];

          expect(lastMsg.type).toBe("error");
          const errorMsg = lastMsg as ChatItemError;
          expect(errorMsg.code).toBe(codingErrorResponse.error!.code);
          expect(errorMsg.message).toBe(codingErrorResponse.error!.message);

          if (codingErrorResponse.error!.data !== undefined) {
            expect(errorMsg.data).toEqual(codingErrorResponse.error!.data);
          }
        }
      ),
      { numRuns: 100 }
    );
  });
});
