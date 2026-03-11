import { renderHook } from "@testing-library/react";
import { describe, it, expect, vi, beforeEach } from "vitest";

// ===== Mocks =====

// Mock CodingSessionContext
const mockDispatch = vi.fn();
const mockState = {
  initialized: false,
  sessions: {},
  activeSessionId: null,
  pendingPermission: null,
};
vi.mock("../../context/CodingSessionContext", () => ({
  useCodingDispatch: () => mockDispatch,
  useCodingState: () => mockState,
}));

// Mock useCodingWebSocket
const mockWsSend = vi.fn();
const mockWsConnect = vi.fn();
const mockWsDisconnect = vi.fn();
let mockWsStatus = "disconnected";
let mockWsAutoConnect: boolean | undefined;
vi.mock("../useCodingWebSocket", () => ({
  useCodingWebSocket: (opts: { url: string; onMessage: (d: string) => void; autoConnect?: boolean }) => {
    mockWsAutoConnect = opts.autoConnect;
    return {
      status: mockWsStatus,
      send: mockWsSend,
      connect: mockWsConnect,
      disconnect: mockWsDisconnect,
    };
  },
}));

// Mock coding protocol utils
vi.mock("../../lib/utils/codingProtocol", () => ({
  buildInitialize: vi.fn(() => ({ id: 1, method: "initialize", jsonrpc: "2.0" })),
  buildSessionNew: vi.fn(),
  buildPrompt: vi.fn(),
  buildCancel: vi.fn(),
  buildSetModel: vi.fn(),
  buildSetMode: vi.fn(),
  buildResponse: vi.fn(),
  resolveResponse: vi.fn(),
  trackRequest: vi.fn(() => Promise.resolve({})),
  extractSessionUpdate: vi.fn(),
  extractPermissionRequest: vi.fn(),
  clearPendingRequests: vi.fn(),
  resetNextId: vi.fn(),
}));

vi.mock("../../lib/utils/codingNormalize", () => ({
  normalizeIncomingMessage: (m: unknown) => m,
}));

vi.mock("../../lib/utils/workspaceApi", () => ({
  ARTIFACT_SCAN_FALLBACK_ENABLED: false,
  fetchWorkspaceChanges: vi.fn(),
}));

import { useCodingSession } from "../useCodingSession";

beforeEach(() => {
  vi.clearAllMocks();
  mockWsStatus = "disconnected";
  mockWsAutoConnect = undefined;
});

// ===== Tests =====

describe("useCodingSession WebSocket communication", () => {
  it("defaults to WebSocket communication", () => {
    renderHook(() => useCodingSession({ wsUrl: "ws://localhost/ws/coding" }));

    expect(mockWsAutoConnect).toBe(true);
  });

  it("does not auto-connect when wsUrl is empty", () => {
    renderHook(() => useCodingSession({ wsUrl: "" }));

    expect(mockWsAutoConnect).toBe(false);
  });

  it("does not auto-connect when autoConnect is false", () => {
    renderHook(() =>
      useCodingSession({ wsUrl: "ws://localhost/ws/coding", autoConnect: false })
    );

    expect(mockWsAutoConnect).toBe(false);
  });

  it("runtimeError is always null", () => {
    const { result } = renderHook(() =>
      useCodingSession({ wsUrl: "ws://localhost/ws/coding" })
    );

    expect(result.current.runtimeError).toBeNull();
  });
});
