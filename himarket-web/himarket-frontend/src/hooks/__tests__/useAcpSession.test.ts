import { renderHook, act } from "@testing-library/react";
import { describe, it, expect, vi, beforeEach, type Mock } from "vitest";

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

// Mock WebContainerAdapter
const mockWcStart = vi.fn().mockResolvedValue(undefined);
const mockWcSend = vi.fn();
const mockWcOnMessage = vi.fn();
const mockWcOnExit = vi.fn();
const mockWcClose = vi.fn().mockResolvedValue(undefined);
vi.mock("../../lib/runtime/WebContainerAdapter", () => ({
  WebContainerAdapter: vi.fn().mockImplementation(() => ({
    start: mockWcStart,
    send: mockWcSend,
    onMessage: mockWcOnMessage,
    onExit: mockWcOnExit,
    close: mockWcClose,
  })),
}));

// Mock FileSyncService
const mockSyncFlush = vi.fn().mockResolvedValue(undefined);
const mockSyncStop = vi.fn();
const mockSyncStartAutoSync = vi.fn();
vi.mock("../../lib/runtime/FileSyncService", () => ({
  FileSyncService: vi.fn().mockImplementation(() => ({
    flush: mockSyncFlush,
    stop: mockSyncStop,
    startAutoSync: mockSyncStartAutoSync,
  })),
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
import { WebContainerAdapter } from "../../lib/runtime/WebContainerAdapter";
import { FileSyncService } from "../../lib/runtime/FileSyncService";

beforeEach(() => {
  vi.clearAllMocks();
  mockWsStatus = "disconnected";
  mockWsAutoConnect = undefined;
});

// ===== 测试 =====

describe("useAcpSession 运行时分支逻辑", () => {
  describe("Local/AgentRun 模式（默认 WebSocket 通信）", () => {
    it("不传 runtimeType 时使用 WebSocket 通信", () => {
      renderHook(() => useAcpSession({ wsUrl: "ws://localhost/ws/acp" }));

      expect(mockWsAutoConnect).toBe(true);
      expect(WebContainerAdapter).not.toHaveBeenCalled();
      expect(FileSyncService).not.toHaveBeenCalled();
    });

    it("runtimeType 为 local 时使用 WebSocket 通信", () => {
      renderHook(() =>
        useAcpSession({ wsUrl: "ws://localhost/ws/acp", runtimeType: "local" })
      );

      expect(mockWsAutoConnect).toBe(true);
      expect(WebContainerAdapter).not.toHaveBeenCalled();
    });

    it("runtimeType 为 agentrun 时使用 WebSocket 通信", () => {
      renderHook(() =>
        useAcpSession({ wsUrl: "ws://localhost/ws/acp", runtimeType: "agentrun" })
      );

      expect(mockWsAutoConnect).toBe(true);
      expect(WebContainerAdapter).not.toHaveBeenCalled();
    });
  });

  describe("WebContainer 模式（直接通信）", () => {
    it("runtimeType 为 webcontainer 时不建立 WebSocket 连接", () => {
      renderHook(() =>
        useAcpSession({
          wsUrl: "ws://localhost/ws/acp?provider=claude-code",
          runtimeType: "webcontainer",
        })
      );

      expect(mockWsAutoConnect).toBe(false);
    });

    it("runtimeType 为 webcontainer 时创建 WebContainerAdapter", async () => {
      renderHook(() =>
        useAcpSession({
          wsUrl: "ws://localhost/ws/acp?provider=claude-code",
          runtimeType: "webcontainer",
        })
      );

      expect(WebContainerAdapter).toHaveBeenCalledTimes(1);
      expect(mockWcStart).toHaveBeenCalledWith("npx", ["claude-code"]);
    });

    it("runtimeType 为 webcontainer 时创建 FileSyncService 并启动自动同步", () => {
      renderHook(() =>
        useAcpSession({
          wsUrl: "ws://localhost/ws/acp?provider=claude-code",
          runtimeType: "webcontainer",
        })
      );

      expect(FileSyncService).toHaveBeenCalledTimes(1);
      expect(mockSyncStartAutoSync).toHaveBeenCalledTimes(1);
    });

    it("WebContainer 模式下注册消息回调到 adapter", () => {
      renderHook(() =>
        useAcpSession({
          wsUrl: "ws://localhost/ws/acp?provider=claude-code",
          runtimeType: "webcontainer",
        })
      );

      expect(mockWcOnMessage).toHaveBeenCalledTimes(1);
      expect(typeof mockWcOnMessage.mock.calls[0][0]).toBe("function");
    });

    it("WebContainer 启动成功后 status 变为 connected", async () => {
      let resolveStart: () => void;
      const startPromise = new Promise<void>((resolve) => { resolveStart = resolve; });
      mockWcStart.mockReturnValueOnce(startPromise);

      const { result } = renderHook(() =>
        useAcpSession({
          wsUrl: "ws://localhost/ws/acp?provider=claude-code",
          runtimeType: "webcontainer",
        })
      );

      // 启动中应为 connecting
      expect(result.current.status).toBe("connecting");

      // 让 start promise resolve
      await act(async () => {
        resolveStart!();
        await startPromise;
      });

      expect(result.current.status).toBe("connected");
    });

    it("WebContainer 启动失败后 status 保持 disconnected", async () => {
      mockWcStart.mockRejectedValueOnce(new Error("boot failed"));

      const { result } = renderHook(() =>
        useAcpSession({
          wsUrl: "ws://localhost/ws/acp?provider=claude-code",
          runtimeType: "webcontainer",
        })
      );

      await act(async () => {
        await vi.waitFor(() => {
          // start 被调用后失败，status 应回到 disconnected
          expect(mockWcStart).toHaveBeenCalled();
        });
      });

      expect(result.current.status).toBe("disconnected");
    });

    it("WebContainer 模式下 send 不走 WebSocket", async () => {
      let resolveStart: () => void;
      const startPromise = new Promise<void>((resolve) => { resolveStart = resolve; });
      mockWcStart.mockReturnValueOnce(startPromise);

      const { result } = renderHook(() =>
        useAcpSession({
          wsUrl: "ws://localhost/ws/acp?provider=claude-code",
          runtimeType: "webcontainer",
        })
      );

      await act(async () => {
        resolveStart!();
        await startPromise;
      });

      expect(result.current.status).toBe("connected");
      // WebSocket send 不应被调用
      expect(mockWsSend).not.toHaveBeenCalled();
    });

    it("卸载时清理 WebContainer 和 FileSyncService", () => {
      const { unmount } = renderHook(() =>
        useAcpSession({
          wsUrl: "ws://localhost/ws/acp?provider=claude-code",
          runtimeType: "webcontainer",
        })
      );

      unmount();

      expect(mockSyncFlush).toHaveBeenCalled();
      expect(mockSyncStop).toHaveBeenCalled();
      expect(mockWcClose).toHaveBeenCalled();
    });
  });

  describe("运行时切换", () => {
    it("从 local 切换到 webcontainer 时创建 WebContainerAdapter", () => {
      const { rerender } = renderHook(
        (props: { runtimeType?: "local" | "agentrun" | "webcontainer" }) =>
          useAcpSession({
            wsUrl: "ws://localhost/ws/acp?provider=claude-code",
            runtimeType: props.runtimeType,
          }),
        { initialProps: { runtimeType: "local" as const } }
      );

      expect(WebContainerAdapter).not.toHaveBeenCalled();

      rerender({ runtimeType: "webcontainer" });

      expect(WebContainerAdapter).toHaveBeenCalled();
    });
  });

  describe("WebContainer 异常检测 (Req 8.4)", () => {
    it("WebContainer 模式下应注册 onExit 回调", () => {
      renderHook(() =>
        useAcpSession({
          wsUrl: "ws://localhost/ws/acp?provider=claude-code",
          runtimeType: "webcontainer",
        })
      );

      expect(mockWcOnExit).toHaveBeenCalledTimes(1);
      expect(typeof mockWcOnExit.mock.calls[0][0]).toBe("function");
    });

    it("进程异常退出时 status 应变为 disconnected 且 runtimeError 非空", async () => {
      let resolveStart: () => void;
      const startPromise = new Promise<void>((resolve) => {
        resolveStart = resolve;
      });
      mockWcStart.mockReturnValueOnce(startPromise);

      const { result } = renderHook(() =>
        useAcpSession({
          wsUrl: "ws://localhost/ws/acp?provider=claude-code",
          runtimeType: "webcontainer",
        })
      );

      // 先让 start 成功
      await act(async () => {
        resolveStart!();
        await startPromise;
      });

      expect(result.current.status).toBe("connected");
      expect(result.current.runtimeError).toBeNull();

      // 模拟进程异常退出：调用 onExit 回调
      const onExitCallback = mockWcOnExit.mock.calls[0][0] as (
        code: number
      ) => void;
      act(() => {
        onExitCallback(1);
      });

      expect(result.current.status).toBe("disconnected");
      expect(result.current.runtimeError).toContain("异常退出");
      expect(result.current.runtimeError).toContain("exit code: 1");
    });

    it("进程正常退出时 runtimeError 应包含正常退出信息", async () => {
      let resolveStart: () => void;
      const startPromise = new Promise<void>((resolve) => {
        resolveStart = resolve;
      });
      mockWcStart.mockReturnValueOnce(startPromise);

      const { result } = renderHook(() =>
        useAcpSession({
          wsUrl: "ws://localhost/ws/acp?provider=claude-code",
          runtimeType: "webcontainer",
        })
      );

      await act(async () => {
        resolveStart!();
        await startPromise;
      });

      const onExitCallback = mockWcOnExit.mock.calls[0][0] as (
        code: number
      ) => void;
      act(() => {
        onExitCallback(0);
      });

      expect(result.current.status).toBe("disconnected");
      expect(result.current.runtimeError).toContain("正常退出");
    });

    it("WebContainer 启动失败时 runtimeError 应包含错误信息", async () => {
      mockWcStart.mockRejectedValueOnce(new Error("boot failed"));

      const { result } = renderHook(() =>
        useAcpSession({
          wsUrl: "ws://localhost/ws/acp?provider=claude-code",
          runtimeType: "webcontainer",
        })
      );

      await act(async () => {
        await vi.waitFor(() => {
          expect(mockWcStart).toHaveBeenCalled();
        });
      });

      expect(result.current.status).toBe("disconnected");
      expect(result.current.runtimeError).toBe("boot failed");
    });

    it("Local 模式下 runtimeError 应为 null", () => {
      const { result } = renderHook(() =>
        useAcpSession({
          wsUrl: "ws://localhost/ws/acp",
          runtimeType: "local",
        })
      );

      expect(result.current.runtimeError).toBeNull();
    });

    it("reconnect 应清理旧实例并重新创建 WebContainer", async () => {
      vi.useFakeTimers();

      let resolveStart: () => void;
      const startPromise = new Promise<void>((resolve) => {
        resolveStart = resolve;
      });
      mockWcStart.mockReturnValueOnce(startPromise);

      const { result } = renderHook(() =>
        useAcpSession({
          wsUrl: "ws://localhost/ws/acp?provider=claude-code",
          runtimeType: "webcontainer",
        })
      );

      await act(async () => {
        resolveStart!();
        await startPromise;
      });

      expect(result.current.status).toBe("connected");

      // 模拟进程退出
      const onExitCallback = mockWcOnExit.mock.calls[0][0] as (
        code: number
      ) => void;
      act(() => {
        onExitCallback(1);
      });

      expect(result.current.status).toBe("disconnected");

      // 为 reconnect 后的 start 准备新的 promise
      let resolveStart2: () => void;
      const startPromise2 = new Promise<void>((resolve) => {
        resolveStart2 = resolve;
      });
      mockWcStart.mockReturnValueOnce(startPromise2);

      // 调用 reconnect
      act(() => {
        result.current.reconnect();
      });

      // 应先清理旧实例
      expect(mockWcClose).toHaveBeenCalled();
      expect(mockSyncFlush).toHaveBeenCalled();
      expect(mockSyncStop).toHaveBeenCalled();

      // setTimeout 触发重新连接
      act(() => {
        vi.runAllTimers();
      });

      // 应创建新的 WebContainerAdapter
      expect(WebContainerAdapter).toHaveBeenCalledTimes(2);

      await act(async () => {
        resolveStart2!();
        await startPromise2;
      });

      expect(result.current.status).toBe("connected");
      expect(result.current.runtimeError).toBeNull();

      vi.useRealTimers();
    });
  });
});
