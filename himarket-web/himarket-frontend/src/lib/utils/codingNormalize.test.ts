import { describe, expect, it } from "vitest";
import { normalizeIncomingMessage } from "./codingNormalize";

describe("normalizeIncomingMessage", () => {
  it("normalizes tool kinds, preserves empty diff newText, and keeps string id", () => {
    const normalized = normalizeIncomingMessage({
      jsonrpc: "2.0",
      id: "42",
      method: "session/update",
      params: {
        sessionId: "sess-1",
        update: {
          sessionUpdate: "tool_call_update",
          toolCallId: "tc-1",
          kind: "search",
          content: [
            {
              type: "diff",
              path: "/tmp/a.txt",
              oldText: "old",
              newText: "",
            },
            {
              type: "terminal",
              terminalId: "term-1",
            },
          ],
        },
      },
    });

    expect(normalized.id).toBe("42");
    const params = normalized.params as {
      update: { kind: string; content: Array<Record<string, unknown>> };
    };
    expect(params.update.kind).toBe("search");
    expect(params.update.content[0].newText).toBe("");
    expect(params.update.content[1].type).toBe("terminal");
    expect(params.update.content[1].terminalId).toBe("term-1");
  });

  it("falls back unknown kind to other in permission request", () => {
    const normalized = normalizeIncomingMessage({
      jsonrpc: "2.0",
      id: 7,
      method: "session/request_permission",
      params: {
        sessionId: "sess-2",
        toolCall: {
          toolCallId: "tc-2",
          title: "do something",
          kind: "mystery_kind",
        },
        options: [],
      },
    });

    const params = normalized.params as {
      toolCall: { kind: string };
    };
    expect(params.toolCall.kind).toBe("other");
  });
});
