import { renderHook } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';

// ===== Mocks =====

// Mock CodingSessionContext
const mockDispatch = vi.fn();
const mockState = {
  activeSessionId: null,
  initialized: false,
  pendingPermission: null,
  sessions: {},
};
vi.mock('../../context/CodingSessionContext', () => ({
  useCodingDispatch: () => mockDispatch,
  useCodingState: () => mockState,
}));

// Mock useCodingWebSocket
const mockWsSend = vi.fn();
const mockWsConnect = vi.fn();
const mockWsDisconnect = vi.fn();
let mockWsStatus = 'disconnected';
let mockWsAutoConnect: boolean | undefined;
vi.mock('../useCodingWebSocket', () => ({
  useCodingWebSocket: (opts: {
    url: string;
    onMessage: (d: string) => void;
    autoConnect?: boolean;
  }) => {
    mockWsAutoConnect = opts.autoConnect;
    return {
      connect: mockWsConnect,
      disconnect: mockWsDisconnect,
      send: mockWsSend,
      status: mockWsStatus,
    };
  },
}));

// Mock coding protocol utils
vi.mock('../../lib/utils/codingProtocol', () => ({
  buildCancel: vi.fn(),
  buildInitialize: vi.fn(() => ({ id: 1, jsonrpc: '2.0', method: 'initialize' })),
  buildPrompt: vi.fn(),
  buildResponse: vi.fn(),
  buildSessionNew: vi.fn(),
  buildSetMode: vi.fn(),
  buildSetModel: vi.fn(),
  clearPendingRequests: vi.fn(),
  extractPermissionRequest: vi.fn(),
  extractSessionUpdate: vi.fn(),
  resetNextId: vi.fn(),
  resolveResponse: vi.fn(),
  trackRequest: vi.fn(() => Promise.resolve({})),
}));

vi.mock('../../lib/utils/codingNormalize', () => ({
  normalizeIncomingMessage: (m: unknown) => m,
}));

vi.mock('../../lib/utils/workspaceApi', () => ({
  ARTIFACT_SCAN_FALLBACK_ENABLED: false,
  fetchWorkspaceChanges: vi.fn(),
}));

import { useCodingSession } from '../useCodingSession';

beforeEach(() => {
  vi.clearAllMocks();
  mockWsStatus = 'disconnected';
  mockWsAutoConnect = undefined;
});

// ===== Tests =====

describe('useCodingSession WebSocket communication', () => {
  it('defaults to WebSocket communication', () => {
    renderHook(() => useCodingSession({ wsUrl: 'ws://localhost/ws/coding' }));

    expect(mockWsAutoConnect).toBe(true);
  });

  it('does not auto-connect when wsUrl is empty', () => {
    renderHook(() => useCodingSession({ wsUrl: '' }));

    expect(mockWsAutoConnect).toBe(false);
  });

  it('does not auto-connect when autoConnect is false', () => {
    renderHook(() => useCodingSession({ autoConnect: false, wsUrl: 'ws://localhost/ws/coding' }));

    expect(mockWsAutoConnect).toBe(false);
  });

  it('runtimeError is always null', () => {
    const { result } = renderHook(() => useCodingSession({ wsUrl: 'ws://localhost/ws/coding' }));

    expect(result.current.runtimeError).toBeNull();
  });
});
