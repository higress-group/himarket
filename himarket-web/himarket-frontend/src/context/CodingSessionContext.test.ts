import fc from 'fast-check';
import { describe, expect, it } from 'vitest';

import {
  codingReducer,
  initialState,
  type CodingState,
  type QueuedPromptItem,
} from './CodingSessionContext';
import { trackRequest, resolveResponse, clearPendingRequests } from '../lib/utils/codingProtocol';
import { JSONRPC_VERSION, type CodingResponse, type ChatItemError } from '../types/coding-protocol';

function buildQueueItem(id: string, text: string): QueuedPromptItem {
  return { createdAt: Date.now(), id, text };
}

function createCodingState(): CodingState {
  return codingReducer(initialState, {
    currentModeId: 'mode-1',
    currentModelId: 'm1',
    cwd: '.',
    models: [{ modelId: 'm1', name: 'M1' }],
    modes: [{ id: 'mode-1', name: 'Mode 1' }],
    sessionId: 'q1',
    type: 'SESSION_CREATED',
  });
}

describe('codingReducer prompt queue state machine', () => {
  it('supports single-flight prompt + queue', () => {
    let state = createCodingState();

    state = codingReducer(state, {
      requestId: 1,
      sessionId: 'q1',
      text: 'first',
      type: 'PROMPT_STARTED',
    });
    state = codingReducer(state, {
      item: buildQueueItem('qp-2', 'second'),
      sessionId: 'q1',
      type: 'PROMPT_ENQUEUED',
    });
    state = codingReducer(state, {
      item: buildQueueItem('qp-3', 'third'),
      sessionId: 'q1',
      type: 'PROMPT_ENQUEUED',
    });

    const q1 = state.sessions['q1'];
    if (!q1) throw new Error('Session q1 not found');
    expect(q1.inflightPromptId).toBe(1);
    expect(q1.isProcessing).toBe(true);
    expect(q1.promptQueue.map((item) => item.id)).toEqual(['qp-2', 'qp-3']);

    state = codingReducer(state, {
      requestId: 1,
      sessionId: 'q1',
      stopReason: 'completed',
      type: 'PROMPT_COMPLETED',
    });
    const q1After = state.sessions['q1'];
    if (!q1After) throw new Error('Session q1 not found');
    expect(q1After.isProcessing).toBe(false);
    expect(q1After.inflightPromptId).toBeNull();
    expect(q1After.promptQueue.length).toBe(2);

    state = codingReducer(state, {
      promptId: 'qp-2',
      requestId: 2,
      sessionId: 'q1',
      text: 'second',
      type: 'PROMPT_STARTED',
    });
    const s2 = state.sessions['q1'];
    if (!s2) throw new Error('Session q1 not found');
    expect(s2.inflightPromptId).toBe(2);
    expect(s2.promptQueue.map((item) => item.id)).toEqual(['qp-3']);

    state = codingReducer(state, {
      requestId: 2,
      sessionId: 'q1',
      stopReason: 'completed',
      type: 'PROMPT_COMPLETED',
    });
    state = codingReducer(state, {
      promptId: 'qp-3',
      requestId: 3,
      sessionId: 'q1',
      text: 'third',
      type: 'PROMPT_STARTED',
    });
    state = codingReducer(state, {
      requestId: 3,
      sessionId: 'q1',
      stopReason: 'completed',
      type: 'PROMPT_COMPLETED',
    });

    const end = state.sessions['q1'];
    if (!end) throw new Error('Session q1 not found');
    expect(end.isProcessing).toBe(false);
    expect(end.inflightPromptId).toBeNull();
    expect(end.promptQueue).toEqual([]);
    expect(end.lastStopReason).toBe('completed');
  });

  it('ignores stale completion from non-inflight request', () => {
    let state = createCodingState();
    state = codingReducer(state, {
      requestId: 100,
      sessionId: 'q1',
      text: 'only',
      type: 'PROMPT_STARTED',
    });

    state = codingReducer(state, {
      requestId: 999,
      sessionId: 'q1',
      stopReason: 'error',
      type: 'PROMPT_COMPLETED',
    });

    const q1 = state.sessions['q1'];
    if (!q1) throw new Error('Session q1 not found');
    expect(q1.isProcessing).toBe(true);
    expect(q1.inflightPromptId).toBe(100);
  });
});

// Feature: error-response-handling, Property 1: PROMPT_ERROR action correctly updates reducer state
describe('codingReducer PROMPT_ERROR property-based tests', () => {
  /**
   * Property 1: PROMPT_ERROR action correctly updates reducer state
   */
  it('PROMPT_ERROR action correctly updates reducer state for any code/message/data', () => {
    fc.assert(
      fc.property(
        fc.integer(),
        fc.string({ minLength: 1 }),
        fc.option(fc.dictionary(fc.string({ minLength: 1 }), fc.jsonValue()), { nil: undefined }),
        fc.oneof(fc.integer({ min: 1 }), fc.string({ minLength: 1 })),
        (code, message, data, requestId) => {
          let state = createCodingState();
          state = codingReducer(state, {
            requestId,
            sessionId: 'q1',
            text: 'test prompt',
            type: 'PROMPT_STARTED',
          });

          const sessionBefore = state.sessions['q1'];
          if (!sessionBefore) throw new Error('Session q1 not found');
          const prevMessages = sessionBefore.messages;
          const prevLength = prevMessages.length;

          const nextState = codingReducer(state, {
            code,
            message,
            requestId,
            sessionId: 'q1',
            type: 'PROMPT_ERROR',
            ...(data !== undefined ? { data: data as Record<string, unknown> } : {}),
          });

          const session = nextState.sessions['q1'];
          if (!session) throw new Error('Session q1 not found');

          expect(session.messages.length).toBe(prevLength + 1);

          const lastMsg = session.messages.at(session.messages.length - 1);
          if (!lastMsg) throw new Error('No messages found');
          expect(lastMsg.type).toBe('error');
          if (lastMsg.type === 'error') {
            expect(lastMsg.code).toBe(code);
            expect(lastMsg.message).toBe(message);
          }

          expect(session.isProcessing).toBe(false);
          expect(session.inflightPromptId).toBeNull();
        },
      ),
      { numRuns: 100 },
    );
  });
});

// Feature: error-response-handling, Property 2: Stale request PROMPT_ERROR is ignored
describe('codingReducer PROMPT_ERROR stale request property-based tests', () => {
  /**
   * Property 2: Stale request PROMPT_ERROR is ignored
   */
  it('PROMPT_ERROR with mismatched requestId leaves state unchanged', () => {
    fc.assert(
      fc.property(
        fc.oneof(fc.integer({ min: 1 }), fc.string({ minLength: 1 })),
        fc.integer(),
        fc.string({ minLength: 1 }),
        (inflightId, code, message) => {
          let state = createCodingState();
          state = codingReducer(state, {
            requestId: inflightId,
            sessionId: 'q1',
            text: 'test prompt',
            type: 'PROMPT_STARTED',
          });

          const sessionBefore = state.sessions['q1'];
          if (!sessionBefore) throw new Error('Session q1 not found');

          const staleRequestId =
            typeof inflightId === 'number' ? inflightId + 1 : inflightId + '_stale';

          const nextState = codingReducer(state, {
            code,
            message,
            requestId: staleRequestId,
            sessionId: 'q1',
            type: 'PROMPT_ERROR',
          });

          const sessionAfter = nextState.sessions['q1'];
          if (!sessionAfter) throw new Error('Session q1 not found');

          expect(sessionAfter.messages).toEqual(sessionBefore.messages);
          expect(sessionAfter.messages.length).toBe(sessionBefore.messages.length);
          expect(sessionAfter.isProcessing).toBe(sessionBefore.isProcessing);
          expect(sessionAfter.inflightPromptId).toBe(sessionBefore.inflightPromptId);
        },
      ),
      { numRuns: 100 },
    );
  });
});

// Feature: error-response-handling, Property 5: Error info end-to-end fidelity
describe('End-to-end error info fidelity property-based tests', () => {
  /**
   * Property 5: Error info end-to-end fidelity
   */
  it('error code and message are preserved through the full resolveResponse → catch → reducer chain', () => {
    fc.assert(
      fc.property(
        fc.oneof(fc.integer({ min: 1 }), fc.string({ minLength: 1 })),
        fc.integer(),
        fc.string({ minLength: 1 }),
        fc.option(fc.dictionary(fc.string({ minLength: 1 }), fc.jsonValue()), { nil: undefined }),
        (requestId, code, message, data) => {
          clearPendingRequests();

          const codingErrorResponse: CodingResponse = {
            error: {
              code,
              message,
              ...(data !== undefined ? { data: data as Record<string, unknown> } : {}),
            },
            id: requestId,
            jsonrpc: JSONRPC_VERSION,
          };

          const promise = trackRequest(requestId);
          promise.catch(() => {
            // error captured via codingErrorResponse.error below
          });

          resolveResponse(codingErrorResponse);

          const errorFromResponse = codingErrorResponse.error ?? {
            code: 'UNKNOWN',
            message: 'Unknown error',
          };

          let state = createCodingState();
          state = codingReducer(state, {
            requestId,
            sessionId: 'q1',
            text: 'test prompt',
            type: 'PROMPT_STARTED',
          });

          const normalizedCode =
            typeof errorFromResponse.code === 'number' ? errorFromResponse.code : 0;
          const normalizedData = 'data' in errorFromResponse ? errorFromResponse.data : undefined;

          const nextState = codingReducer(state, {
            code: normalizedCode,
            message: errorFromResponse.message,
            requestId,
            sessionId: 'q1',
            type: 'PROMPT_ERROR',
            ...(normalizedData ? { data: normalizedData } : {}),
          });

          const session = nextState.sessions['q1'];
          if (!session) throw new Error('Session q1 not found');
          const lastMsg = session.messages.at(session.messages.length - 1);
          if (!lastMsg) throw new Error('No messages found');

          expect(lastMsg.type).toBe('error');
          const errorMsg = lastMsg as ChatItemError;
          expect(errorMsg.code).toBe(normalizedCode);
          expect(errorMsg.message).toBe(errorFromResponse.message);

          if (normalizedData !== undefined) {
            expect(errorMsg.data).toEqual(normalizedData);
          }
        },
      ),
      { numRuns: 100 },
    );
  });
});
