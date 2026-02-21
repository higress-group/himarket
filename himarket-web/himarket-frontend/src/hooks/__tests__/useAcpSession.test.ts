import { renderHook } from "@testing-library/react";
import { describe, it, expect, vi, beforeEach } from "vitest";

// ===== Mocks =====

// Mock QuestSessionContext
const mockDispatch = vi.fn();
const mockState = {
  initialized: false,
  quests: {},
  activeQuestId: null,
  pendingPermission: null,
};
vi.mock("../../context/QuestSessionContext", () => ({
  useQuestDispatch: () => mockDispatch,
  useQuestState: () => mockState,
}));

// Mock useAcpWebSocket
const mockWsSend = vi.fn();
const mockWsConnect = vi.fn();
const mockWsDisconnect = vi.fn();
let mockWsStatus = "disconnected";
let mockWsAutoConnect: boolean | undefined;
vi.mock("../useAcpWebSocket", () => ({
  useAcpWebSocket: (opts: { url: string; onMessage: (d: string) => void; autoConnect?: boolean }) => {
    mockWsAutoConnect = opts.autoConnect;
    return {
      status: mockWsStatus,
      send: mockWsSend,
      connect: mockWsConnect,
      disconnect: mockWsDisconnect,
    };
  },
}));

// Mock acp utils
vi.mock("../../lib/utils/acp", () => ({
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

vi.mock("../../lib/utils/acpNormalize", () => ({
  normalizeIncomingMessage: (m: unknown) => m,
}));

vi.mock("../../lib/utils/workspaceApi", () => ({
  ARTIFACT_SCAN_FALLBACK_ENABLED: false,
  fetchWorkspaceChanges: vi.fn(),
}));

import { useAcpSession } from "../useAcpSession";

beforeEach(() => {
  vi.clearAllMocks();
  mockWsStatus = "disconnected";
  mockWsAutoConnect = undefined;
});

// ===== 测试 =====

describe("useAcpSession WebSocket 通信", () => {
  it("默认使用 WebSocket 通信", () => {
    renderHook(() => useAcpSession({ wsUrl: "ws://localhost/ws/acp" }));

    expect(mockWsAutoConnect).toBe(true);
  });

  it("wsUrl 为空时不自动连接", () => {
    renderHook(() => useAcpSession({ wsUrl: "" }));

    expect(mockWsAutoConnect).toBe(false);
  });

  it("autoConnect 为 false 时不自动连接", () => {
    renderHook(() =>
      useAcpSession({ wsUrl: "ws://localhost/ws/acp", autoConnect: false })
    );

    expect(mockWsAutoConnect).toBe(false);
  });

  it("runtimeError 始终为 null", () => {
    const { result } = renderHook(() =>
      useAcpSession({ wsUrl: "ws://localhost/ws/acp" })
    );

    expect(result.current.runtimeError).toBeNull();
  });
});
