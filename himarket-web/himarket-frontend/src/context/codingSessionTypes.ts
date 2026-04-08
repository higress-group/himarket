import type { Artifact } from '../types/artifact';
import type { OpenFile, TerminalSession } from '../types/coding';
import type {
  ChatItem,
  Model,
  Mode,
  Command,
  PermissionRequest,
  SessionUpdate,
  Attachment,
  JsonRpcId,
} from '../types/coding-protocol';

// ===== Session Data =====

export interface CodingSessionData {
  id: string;
  /** 后端平台生成的 sessionId，用于 REST API 调用（CRUD） */
  platformSessionId?: string;
  title: string;
  cwd: string;
  messages: ChatItem[];
  availableModels: Model[];
  availableModes: Mode[];
  currentModelId: string;
  currentModeId: string;
  isProcessing: boolean;
  isLoading: boolean;
  inflightPromptId: JsonRpcId | null;
  promptQueue: QueuedPromptItem[];
  lastStopReason: string | null;
  lastCompletedAt: number | null;
  selectedToolCallId: string | null;
  artifacts: Artifact[];
  activeArtifactId: string | null;
  lastArtifactScanAt: number;
  createdAt: number;
  /**
   * 会话恢复后需要注入历史上下文。
   * 当 session/load fallback 到 session/new 时设为 true，
   * 第一条 prompt 发送后清除。
   */
  needsHistoryInjection: boolean;
  // Coding IDE state
  openFiles: OpenFile[];
  activeFilePath: string | null;
  terminals: TerminalSession[];
}

export interface QueuedPromptItem {
  id: string;
  text: string;
  attachments?: Attachment[];
  createdAt: number;
}

// ===== App State =====

export interface CodingState {
  connected: boolean;
  initialized: boolean;
  sessions: Record<string, CodingSessionData>;
  activeSessionId: string | null;
  models: Model[];
  modes: Mode[];
  commands: Command[];
  usage: {
    size: number;
    used: number;
    cost?: { amount: number; currency: string };
  } | null;
  pendingPermission: {
    id: JsonRpcId;
    sessionId: string;
    request: PermissionRequest;
  } | null;
  /** 沙箱状态：K8s Pod 异步创建时的进度信息 */
  sandboxStatus: {
    status: 'creating' | 'ready' | 'error';
    message: string;
    sandboxHost?: string;
  } | null;
  /** 沙箱初始化进度：5阶段详细进度信息 */
  initProgress: {
    phase:
      | 'sandbox-acquire'
      | 'filesystem-ready'
      | 'config-injection'
      | 'sidecar-connect'
      | 'cli-ready';
    status: 'executing' | 'completed';
    message: string;
    progress: number;
    totalPhases: number;
    completedPhases: number;
  } | null;
  /** 全局当前 mode ID（由 PROTOCOL_INITIALIZED 设置，无活跃 Session 时用于 TopBar 回退） */
  currentModeId: string;
  /** 后端通过 workspace/info 通知推送的实际工作目录（如 /workspace/{userId}） */
  workspaceCwd: string | null;
  /** Agent 是否支持 session/load（由 PROTOCOL_INITIALIZED 从 agentCapabilities 设置） */
  agentSupportsLoadSession: boolean;
}

// ===== Actions =====

export type CodingAction =
  | { type: 'WS_CONNECTED' }
  | { type: 'WS_DISCONNECTED' }
  | { type: 'RESET_STATE' }
  | {
      type: 'PROTOCOL_INITIALIZED';
      models: Model[];
      modes: Mode[];
      currentModelId: string;
      currentModeId: string;
      agentSupportsLoadSession?: boolean;
    }
  | {
      type: 'SESSION_CREATED';
      sessionId: string;
      cwd: string;
      models?: Model[];
      modes?: Mode[];
      currentModelId?: string;
      currentModeId?: string;
    }
  | { type: 'SESSION_SWITCHED'; sessionId: string }
  | { type: 'SESSION_CLOSED'; sessionId: string }
  | { type: 'SESSION_TITLE_UPDATED'; sessionId: string; title: string }
  | {
      type: 'SESSION_LOADING';
      sessionId: string;
      cwd: string;
      title?: string;
      platformSessionId?: string;
    }
  | { type: 'SESSION_LOADED'; sessionId: string; needsHistoryInjection?: boolean }
  | { type: 'SESSION_MIGRATED'; oldSessionId: string; newSessionId: string }
  | { type: 'SESSION_UPDATE'; sessionId: string; update: SessionUpdate }
  | { type: 'HISTORY_INJECTED'; sessionId: string }
  | { type: 'PROMPT_ENQUEUED'; sessionId: string; item: QueuedPromptItem }
  | { type: 'PROMPT_DEQUEUED'; sessionId: string; promptId: string }
  | {
      type: 'PROMPT_STARTED';
      sessionId: string;
      requestId: JsonRpcId;
      text: string;
      attachments?: Attachment[];
      promptId?: string;
    }
  | {
      type: 'PROMPT_COMPLETED';
      sessionId: string;
      requestId?: JsonRpcId;
      stopReason: string;
    }
  | {
      type: 'PROMPT_ERROR';
      sessionId: string;
      requestId: JsonRpcId;
      code: number;
      message: string;
      data?: Record<string, unknown>;
    }
  | { type: 'SET_MODEL'; modelId: string }
  | { type: 'SET_MODE'; modeId: string }
  | { type: 'SELECT_TOOL_CALL'; toolCallId: string | null }
  | {
      type: 'PERMISSION_REQUEST';
      id: JsonRpcId;
      sessionId: string;
      request: PermissionRequest;
    }
  | { type: 'PERMISSION_RESOLVED' }
  | { type: 'COMMANDS_UPDATED'; commands: Command[] }
  | { type: 'SELECT_ARTIFACT'; artifactId: string | null }
  | { type: 'UPDATE_ARTIFACT_CONTENT'; artifactId: string; content: string }
  | {
      type: 'UPSERT_ARTIFACTS_FROM_PATHS';
      sessionId: string;
      toolCallId: string;
      paths: string[];
    }
  | { type: 'SET_ARTIFACT_SCAN_CURSOR'; sessionId: string; cursor: number }
  // Coding IDE actions
  | { type: 'FILE_OPENED'; sessionId: string; file: OpenFile }
  | { type: 'FILE_CLOSED'; sessionId: string; path: string }
  | { type: 'ACTIVE_FILE_CHANGED'; sessionId: string; path: string | null }
  | { type: 'TERMINAL_CREATED'; sessionId: string; terminalId: string }
  | { type: 'TERMINAL_DATA'; sessionId: string; terminalId: string; data: string }
  | {
      type: 'SANDBOX_STATUS';
      status: 'creating' | 'ready' | 'error';
      message: string;
      sandboxHost?: string;
    }
  | {
      type: 'INIT_PROGRESS';
      phase: string;
      status: 'executing' | 'completed';
      message: string;
      progress: number;
      totalPhases: number;
      completedPhases: number;
    }
  | { type: 'WORKSPACE_INFO'; cwd: string }
  | { type: 'SET_PLATFORM_SESSION_ID'; sessionId: string; platformSessionId: string };
