import { describe, expect, it } from 'vitest';

import { normalizeIncomingMessage } from './codingNormalize';

describe('normalizeIncomingMessage', () => {
  it('normalizes tool kinds, preserves empty diff newText, and keeps string id', () => {
    const normalized = normalizeIncomingMessage({
      id: '42',
      jsonrpc: '2.0',
      method: 'session/update',
      params: {
        sessionId: 'sess-1',
        update: {
          content: [
            {
              newText: '',
              oldText: 'old',
              path: '/tmp/a.txt',
              type: 'diff',
            },
            {
              terminalId: 'term-1',
              type: 'terminal',
            },
          ],
          kind: 'search',
          sessionUpdate: 'tool_call_update',
          toolCallId: 'tc-1',
        },
      },
    });

    expect(normalized.id).toBe('42');
    const params = normalized.params as {
      update: { kind: string; content: Array<Record<string, unknown>> };
    };
    expect(params.update.kind).toBe('search');
    const content0 = params.update.content.at(0);
    const content1 = params.update.content.at(1);
    expect(content0?.newText).toBe('');
    expect(content1?.type).toBe('terminal');
    expect(content1?.terminalId).toBe('term-1');
  });

  it('falls back unknown kind to other in permission request', () => {
    const normalized = normalizeIncomingMessage({
      id: 7,
      jsonrpc: '2.0',
      method: 'session/request_permission',
      params: {
        options: [],
        sessionId: 'sess-2',
        toolCall: {
          kind: 'mystery_kind',
          title: 'do something',
          toolCallId: 'tc-2',
        },
      },
    });

    const params = normalized.params as {
      toolCall: { kind: string };
    };
    expect(params.toolCall.kind).toBe('other');
  });
});
