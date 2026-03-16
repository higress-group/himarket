import {
  createContext,
  useContext,
  useReducer,
  type Dispatch,
  type ReactNode,
} from "react";
import type {
  ChatItem,
  ChatItemAgent,
  ChatItemThought,
  ChatItemToolCall,
  ChatItemPlan,
  ChatItemError,
  Model,
  Mode,
  Command,
  PermissionRequest,
  SessionUpdate,
  ToolCallContentItem,
  ToolCallStatus,
  Attachment,
  JsonRpcId,
  ToolKind,
  ContentBlock,
} from "../types/coding-protocol";
import type { Artifact } from "../types/artifact";
import type { OpenFile, TerminalSession } from "../types/coding";
import {
  detectArtifacts,
  detectArtifactsFromPaths,
  normalizePath,
} from "../lib/utils/artifactDetector";

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
    status: "creating" | "ready" | "error";
    message: string;
    sandboxHost?: string;
  } | null;
  /** 沙箱初始化进度：5阶段详细进度信息 */
  initProgress: {
    phase: "sandbox-acquire" | "filesystem-ready" | "config-injection" | "sidecar-connect" | "cli-ready";
    status: "executing" | "completed";
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

export const initialState: CodingState = {
  connected: false,
  initialized: false,
  sessions: {},
  activeSessionId: null,
  models: [],
  modes: [],
  commands: [],
  usage: null,
  pendingPermission: null,
  sandboxStatus: null,
  initProgress: null,
  currentModeId: "",
  workspaceCwd: null,
  agentSupportsLoadSession: false,
};

// ===== Actions =====

export type CodingAction =
  | { type: "WS_CONNECTED" }
  | { type: "WS_DISCONNECTED" }
  | { type: "RESET_STATE" }
  | {
      type: "PROTOCOL_INITIALIZED";
      models: Model[];
      modes: Mode[];
      currentModelId: string;
      currentModeId: string;
      agentSupportsLoadSession?: boolean;
    }
  | {
      type: "SESSION_CREATED";
      sessionId: string;
      cwd: string;
      models?: Model[];
      modes?: Mode[];
      currentModelId?: string;
      currentModeId?: string;
    }
  | { type: "SESSION_SWITCHED"; sessionId: string }
  | { type: "SESSION_CLOSED"; sessionId: string }
  | { type: "SESSION_TITLE_UPDATED"; sessionId: string; title: string }
  | {
      type: "SESSION_LOADING";
      sessionId: string;
      cwd: string;
      title?: string;
      platformSessionId?: string;
    }
  | { type: "SESSION_LOADED"; sessionId: string; needsHistoryInjection?: boolean }
  | { type: "SESSION_MIGRATED"; oldSessionId: string; newSessionId: string }
  | { type: "SESSION_UPDATE"; sessionId: string; update: SessionUpdate }
  | { type: "HISTORY_INJECTED"; sessionId: string }
  | { type: "PROMPT_ENQUEUED"; sessionId: string; item: QueuedPromptItem }
  | { type: "PROMPT_DEQUEUED"; sessionId: string; promptId: string }
  | {
      type: "PROMPT_STARTED";
      sessionId: string;
      requestId: JsonRpcId;
      text: string;
      attachments?: Attachment[];
      promptId?: string;
    }
  | {
      type: "PROMPT_COMPLETED";
      sessionId: string;
      requestId?: JsonRpcId;
      stopReason: string;
    }
  | {
      type: "PROMPT_ERROR";
      sessionId: string;
      requestId: JsonRpcId;
      code: number;
      message: string;
      data?: Record<string, unknown>;
    }
  | { type: "SET_MODEL"; modelId: string }
  | { type: "SET_MODE"; modeId: string }
  | { type: "SELECT_TOOL_CALL"; toolCallId: string | null }
  | {
      type: "PERMISSION_REQUEST";
      id: JsonRpcId;
      sessionId: string;
      request: PermissionRequest;
    }
  | { type: "PERMISSION_RESOLVED" }
  | { type: "COMMANDS_UPDATED"; commands: Command[] }
  | { type: "SELECT_ARTIFACT"; artifactId: string | null }
  | { type: "UPDATE_ARTIFACT_CONTENT"; artifactId: string; content: string }
  | {
      type: "UPSERT_ARTIFACTS_FROM_PATHS";
      sessionId: string;
      toolCallId: string;
      paths: string[];
    }
  | { type: "SET_ARTIFACT_SCAN_CURSOR"; sessionId: string; cursor: number }
  // Coding IDE actions
  | { type: "FILE_OPENED"; sessionId: string; file: OpenFile }
  | { type: "FILE_CLOSED"; sessionId: string; path: string }
  | { type: "ACTIVE_FILE_CHANGED"; sessionId: string; path: string | null }
  | { type: "TERMINAL_CREATED"; sessionId: string; terminalId: string }
  | { type: "TERMINAL_DATA"; sessionId: string; terminalId: string; data: string }
  | { type: "SANDBOX_STATUS"; status: "creating" | "ready" | "error"; message: string; sandboxHost?: string }
  | {
      type: "INIT_PROGRESS";
      phase: string;
      status: "executing" | "completed";
      message: string;
      progress: number;
      totalPhases: number;
      completedPhases: number;
    }
  | { type: "WORKSPACE_INFO"; cwd: string }
  | { type: "SET_PLATFORM_SESSION_ID"; sessionId: string; platformSessionId: string };

// ===== Helpers =====

let _chatItemId = 0;
function chatItemId(): string {
  return `ci-${++_chatItemId}`;
}

function getActiveSession(state: CodingState): CodingSessionData | null {
  if (!state.activeSessionId) return null;
  return state.sessions[state.activeSessionId] ?? null;
}

function updateActiveSession(
  state: CodingState,
  updater: (s: CodingSessionData) => CodingSessionData
): CodingState {
  const session = getActiveSession(state);
  if (!session) return state;
  return { ...state, sessions: { ...state.sessions, [session.id]: updater(session) } };
}

function updateSessionById(
  state: CodingState,
  sessionId: string,
  updater: (s: CodingSessionData) => CodingSessionData
): CodingState {
  const session = state.sessions[sessionId];
  if (!session) return state;
  return { ...state, sessions: { ...state.sessions, [sessionId]: updater(session) } };
}

// ===== Artifact Helpers =====

/**
 * 将产物路径解析为绝对路径。
 * 沙箱内 agent 的 cwd 为 /workspace/{userId}，工具调用中报告的路径可能是
 * 相对路径（如 "skills/foo.html"），需要补全为 /workspace/{userId}/skills/foo.html。
 */
function resolveArtifactPath(filePath: string, cwd: string): string {
  if (!cwd || filePath.startsWith("/")) return filePath;
  const base = cwd.endsWith("/") ? cwd : cwd + "/";
  return base + filePath;
}

function upsertDetectedArtifacts(
  q: CodingSessionData,
  detected: Artifact[]
): CodingSessionData {
  if (detected.length === 0) return q;

  let artifacts = q.artifacts;
  let activeArtifactId = q.activeArtifactId;

  for (const artifact of detected) {
    const resolvedPath = resolveArtifactPath(normalizePath(artifact.path), q.cwd);
    const normalizedArtifact = { ...artifact, path: resolvedPath };
    const existingIdx = artifacts.findIndex(a => normalizePath(a.path) === normalizedArtifact.path);

    if (existingIdx >= 0) {
      artifacts = artifacts.map((a, i) =>
        i === existingIdx
          ? {
              ...a,
              path: normalizedArtifact.path,
              content: normalizedArtifact.content,
              updatedAt: normalizedArtifact.updatedAt,
              toolCallId: normalizedArtifact.toolCallId,
            }
          : a
      );
      activeArtifactId = artifacts[existingIdx].id;
    } else {
      artifacts = [...artifacts, normalizedArtifact];
      activeArtifactId = normalizedArtifact.id;
    }
  }

  return { ...q, artifacts, activeArtifactId };
}

function applyArtifactDetection(
  q: CodingSessionData,
  toolCall: ChatItemToolCall
): CodingSessionData {
  const detected = detectArtifacts(toolCall);
  return upsertDetectedArtifacts(q, detected);
}

function applyArtifactPaths(
  q: CodingSessionData,
  paths: string[],
  toolCallId: string
): CodingSessionData {
  const detected = detectArtifactsFromPaths(paths, toolCallId);
  return upsertDetectedArtifacts(q, detected);
}

function hasOwn(obj: object, key: string): boolean {
  return Object.prototype.hasOwnProperty.call(obj, key);
}

function extractTextFromContentBlock(
  content: ContentBlock | undefined
): string {
  if (!content) return "";
  if (content.type === "text") {
    return typeof content.text === "string" ? content.text : "";
  }
  if (content.type === "resource_link") {
    const name = typeof content.name === "string" ? content.name : "resource";
    return `[resource] ${name}`;
  }
  if (content.type === "image") return "[image]";
  if (content.type === "audio") return "[audio]";
  if (content.type === "resource") return "[embedded resource]";
  return "";
}

// ===== Reducer =====

export function codingReducer(
  state: CodingState,
  action: CodingAction
): CodingState {
  switch (action.type) {
    case "WS_CONNECTED":
      return { ...state, connected: true };

    case "WS_DISCONNECTED":
      return { ...state, connected: false, initialized: false, sandboxStatus: null, initProgress: null };

    case "RESET_STATE":
      return { ...initialState };

    case "PROTOCOL_INITIALIZED":
      return {
        ...state,
        initialized: true,
        models: action.models,
        modes: action.modes,
        currentModeId: action.currentModeId || state.currentModeId,
        agentSupportsLoadSession: action.agentSupportsLoadSession ?? false,
      };

    case "SESSION_CREATED": {
      const newModels =
        action.models && action.models.length > 0
          ? action.models
          : state.models;
      const newModes =
        action.modes && action.modes.length > 0 ? action.modes : state.modes;
      const session: CodingSessionData = {
        id: action.sessionId,
        title: `Session ${Object.keys(state.sessions).length + 1}`,
        cwd: action.cwd,
        messages: [],
        availableModels: newModels,
        availableModes: newModes,
        currentModelId: action.currentModelId ?? newModels[0]?.modelId ?? "",
        currentModeId: action.currentModeId ?? newModes[0]?.id ?? "",
        isProcessing: false,
        isLoading: false,
        inflightPromptId: null,
        promptQueue: [],
        lastStopReason: null,
        lastCompletedAt: null,
        selectedToolCallId: null,
        artifacts: [],
        activeArtifactId: null,
        lastArtifactScanAt: Date.now(),
        createdAt: Date.now(),
        needsHistoryInjection: false,
        openFiles: [],
        activeFilePath: null,
        terminals: [],
      };
      return {
        ...state,
        sessions: { ...state.sessions, [action.sessionId]: session },
        activeSessionId: action.sessionId,
        models: newModels,
        modes: newModes,
      };
    }

    case "SESSION_LOADING": {
      const loadingSession: CodingSessionData = {
        id: action.sessionId,
        platformSessionId: action.platformSessionId,
        title: action.title ?? "Loading...",
        cwd: action.cwd,
        messages: [],
        availableModels: state.models,
        availableModes: state.modes,
        currentModelId: state.models[0]?.modelId ?? "",
        currentModeId: state.modes[0]?.id ?? "",
        isProcessing: true,
        isLoading: true,
        inflightPromptId: null,
        promptQueue: [],
        lastStopReason: null,
        lastCompletedAt: null,
        selectedToolCallId: null,
        artifacts: [],
        activeArtifactId: null,
        lastArtifactScanAt: Date.now(),
        createdAt: Date.now(),
        needsHistoryInjection: false,
        openFiles: [],
        activeFilePath: null,
        terminals: [],
      };
      return {
        ...state,
        sessions: { ...state.sessions, [action.sessionId]: loadingSession },
        activeSessionId: action.sessionId,
      };
    }

    case "SESSION_LOADED":
      return updateSessionById(state, action.sessionId, s => ({
        ...s,
        isLoading: false,
        isProcessing: false,
        needsHistoryInjection: action.needsHistoryInjection ?? false,
      }));

    case "SESSION_MIGRATED": {
      const oldSession = state.sessions[action.oldSessionId];
      if (!oldSession) return state;
      const { [action.oldSessionId]: _, ...rest } = state.sessions;
      const migrated = { ...oldSession, id: action.newSessionId };
      return {
        ...state,
        sessions: { ...rest, [action.newSessionId]: migrated },
        activeSessionId:
          state.activeSessionId === action.oldSessionId
            ? action.newSessionId
            : state.activeSessionId,
      };
    }

    case "SESSION_SWITCHED":
      return state.sessions[action.sessionId]
        ? { ...state, activeSessionId: action.sessionId }
        : state;

    case "SESSION_CLOSED": {
      // eslint-disable-next-line @typescript-eslint/no-unused-vars
      const { [action.sessionId]: _removed, ...rest } = state.sessions;
      const newActive =
        state.activeSessionId === action.sessionId
          ? (Object.keys(rest)[0] ?? null)
          : state.activeSessionId;
      return { ...state, sessions: rest, activeSessionId: newActive };
    }

    case "SESSION_TITLE_UPDATED":
      return updateSessionById(state, action.sessionId, s => ({
        ...s,
        title: action.title,
      }));

    case "PROMPT_ENQUEUED":
      return updateSessionById(state, action.sessionId, s => ({
        ...s,
        promptQueue: [...s.promptQueue, action.item],
      }));

    case "PROMPT_DEQUEUED":
      return updateSessionById(state, action.sessionId, s => ({
        ...s,
        promptQueue: s.promptQueue.filter(item => item.id !== action.promptId),
      }));

    case "PROMPT_STARTED":
      return updateSessionById(state, action.sessionId, s => {
        // Use the first user message as the session title when it's still "Session N"
        const isDefaultTitle = /^Session \d+$/.test(s.title);
        const hasNoUserMessages = !s.messages.some(m => m.type === "user");
        const newTitle =
          isDefaultTitle && hasNoUserMessages
            ? action.text.slice(0, 50)
            : s.title;
        return {
          ...s,
          title: newTitle,
          promptQueue: action.promptId
            ? s.promptQueue.filter(item => item.id !== action.promptId)
            : s.promptQueue,
          messages: [
            ...s.messages,
            {
              type: "user",
              id: chatItemId(),
              text: action.text,
              ...(action.attachments && action.attachments.length > 0
                ? { attachments: action.attachments }
                : {}),
            } as ChatItem,
          ],
          isProcessing: true,
          inflightPromptId: action.requestId,
        };
      });

    case "PROMPT_COMPLETED":
      return updateSessionById(state, action.sessionId, s => {
        if (
          action.requestId !== undefined &&
          s.inflightPromptId !== null &&
          s.inflightPromptId !== action.requestId
        ) {
          return s;
        }
        return {
          ...s,
          isProcessing: false,
          inflightPromptId: null,
          lastStopReason: action.stopReason,
          lastCompletedAt: Date.now(),
        };
      });

    case "PROMPT_ERROR":
      return updateSessionById(state, action.sessionId, s => {
        if (
          action.requestId !== undefined &&
          s.inflightPromptId !== null &&
          s.inflightPromptId !== action.requestId
        ) {
          return s;
        }
        const errorItem: ChatItemError = {
          type: "error",
          id: chatItemId(),
          code: action.code,
          message: action.message,
          ...(action.data ? { data: action.data } : {}),
        };
        return {
          ...s,
          messages: [...s.messages, errorItem],
          isProcessing: false,
          inflightPromptId: null,
          lastStopReason: "error",
          lastCompletedAt: Date.now(),
        };
      });

    case "SET_MODEL":
      return updateActiveSession(state, s => ({
        ...s,
        currentModelId: action.modelId,
      }));

    case "SET_MODE":
      return updateActiveSession(state, s => ({
        ...s,
        currentModeId: action.modeId,
      }));

    case "SELECT_TOOL_CALL":
      return updateActiveSession(state, s => ({
        ...s,
        selectedToolCallId: action.toolCallId,
      }));

    case "SELECT_ARTIFACT":
      return updateActiveSession(state, s => ({
        ...s,
        activeArtifactId: action.artifactId,
      }));

    case "UPDATE_ARTIFACT_CONTENT":
      return updateActiveSession(state, s => ({
        ...s,
        artifacts: s.artifacts.map(a =>
          a.id === action.artifactId
            ? { ...a, content: action.content, updatedAt: Date.now() }
            : a
        ),
      }));

    case "UPSERT_ARTIFACTS_FROM_PATHS":
      return updateSessionById(state, action.sessionId, s =>
        applyArtifactPaths(s, action.paths, action.toolCallId)
      );

    case "SET_ARTIFACT_SCAN_CURSOR":
      return updateSessionById(state, action.sessionId, s => ({
        ...s,
        lastArtifactScanAt: action.cursor,
      }));

    case "PERMISSION_REQUEST":
      return {
        ...state,
        pendingPermission: {
          id: action.id,
          sessionId: action.sessionId,
          request: action.request,
        },
      };

    case "PERMISSION_RESOLVED":
      return { ...state, pendingPermission: null };

    case "COMMANDS_UPDATED":
      return { ...state, commands: action.commands };

    case "SESSION_UPDATE":
      return handleSessionUpdate(state, action.sessionId, action.update);

    case "HISTORY_INJECTED":
      return updateSessionById(state, action.sessionId, s => ({
        ...s,
        needsHistoryInjection: false,
      }));

    // ===== Coding IDE Actions =====

    case "FILE_OPENED":
      return updateSessionById(state, action.sessionId, s => {
        const exists = s.openFiles.some(f => f.path === action.file.path);
        return {
          ...s,
          openFiles: exists
            ? s.openFiles.map(f =>
                f.path === action.file.path ? action.file : f
              )
            : [...s.openFiles, action.file],
          activeFilePath: action.file.path,
        };
      });

    case "FILE_CLOSED":
      return updateSessionById(state, action.sessionId, s => {
        const newFiles = s.openFiles.filter(f => f.path !== action.path);
        let newActive = s.activeFilePath;
        if (s.activeFilePath === action.path) {
          newActive =
            newFiles.length > 0 ? newFiles[newFiles.length - 1].path : null;
        }
        return { ...s, openFiles: newFiles, activeFilePath: newActive };
      });

    case "ACTIVE_FILE_CHANGED":
      return updateSessionById(state, action.sessionId, s => ({
        ...s,
        activeFilePath: action.path,
      }));

    case "TERMINAL_CREATED":
      return updateSessionById(state, action.sessionId, s => {
        const exists = s.terminals.some(t => t.id === action.terminalId);
        if (exists) return s;
        return {
          ...s,
          terminals: [...s.terminals, { id: action.terminalId, lines: [] }],
        };
      });

    case "TERMINAL_DATA":
      return updateSessionById(state, action.sessionId, s => {
        const hasTerminal = s.terminals.some(t => t.id === action.terminalId);
        const terminals = hasTerminal
          ? s.terminals.map(t =>
              t.id === action.terminalId
                ? { ...t, lines: [...t.lines, action.data] }
                : t
            )
          : [
              ...s.terminals,
              { id: action.terminalId, lines: [action.data] },
            ];
        return { ...s, terminals };
      });

    case "SANDBOX_STATUS":
      return {
        ...state,
        sandboxStatus: {
          status: action.status,
          message: action.message,
          sandboxHost: action.sandboxHost ?? state.sandboxStatus?.sandboxHost,
        },
      };

    case "INIT_PROGRESS":
      return {
        ...state,
        initProgress: {
          phase: action.phase as "sandbox-acquire" | "filesystem-ready" | "config-injection" | "sidecar-connect" | "cli-ready",
          status: action.status,
          message: action.message,
          progress: action.progress,
          totalPhases: action.totalPhases,
          completedPhases: action.completedPhases,
        },
      };

    case "WORKSPACE_INFO":
      return { ...state, workspaceCwd: action.cwd };

    case "SET_PLATFORM_SESSION_ID":
      return updateSessionById(state, action.sessionId, s => ({
        ...s,
        platformSessionId: action.platformSessionId,
      }));

    default:
      return state;
  }
}

function handleSessionUpdate(
  state: CodingState,
  sessionId: string,
  update: SessionUpdate
): CodingState {
  const variant = update.update.sessionUpdate;

  switch (variant) {
    case "agent_message_chunk": {
      const contentBlock =
        "content" in update.update
          ? (update.update as { content?: ContentBlock }).content
          : undefined;
      const text = extractTextFromContentBlock(contentBlock);
      return updateSessionById(state, sessionId, s => {
        const msgs = [...s.messages];
        const last = msgs[msgs.length - 1];
        if (last && last.type === "agent") {
          msgs[msgs.length - 1] = {
            ...last,
            text: last.text + text,
          } as ChatItemAgent;
        } else {
          msgs.push({ type: "agent", id: chatItemId(), text, complete: false });
        }

        let updated = { ...s, messages: msgs };

        // Detect artifacts from resource_link content blocks
        if (
          contentBlock?.type === "resource_link" &&
          typeof contentBlock.uri === "string"
        ) {
          let filePath = contentBlock.uri;
          if (filePath.startsWith("file://")) filePath = filePath.slice(7);
          if (filePath && !filePath.startsWith("http")) {
            updated = applyArtifactPaths(updated, [filePath], "resource-link");
          }
        }

        return updated;
      });
    }

    case "agent_thought_chunk": {
      const text =
        "content" in update.update
          ? extractTextFromContentBlock(
              (update.update as { content?: ContentBlock }).content
            )
          : "";
      return updateSessionById(state, sessionId, s => {
        const msgs = [...s.messages];
        const last = msgs[msgs.length - 1];
        if (last && last.type === "thought") {
          msgs[msgs.length - 1] = {
            ...last,
            text: last.text + text,
          } as ChatItemThought;
        } else {
          msgs.push({ type: "thought", id: chatItemId(), text });
        }
        return { ...s, messages: msgs };
      });
    }

    case "user_message_chunk": {
      // session/load 回放历史时，sidecar 会重播 user_message_chunk；
      // 仅当会话处于 isLoading 状态时才追加（正常对话中用户消息已由 PROMPT_STARTED 添加）。
      const session = state.sessions[sessionId];
      if (!session || !session.isLoading) return state;

      const userContent =
        "content" in update.update
          ? (update.update as { content?: ContentBlock }).content
          : undefined;
      const userText = extractTextFromContentBlock(userContent);
      if (!userText) return state;

      return updateSessionById(state, sessionId, s => ({
        ...s,
        messages: [...s.messages, { type: "user" as const, id: chatItemId(), text: userText }],
      }));
    }

    case "tool_call": {
      const u = update.update as {
        sessionUpdate: "tool_call";
        toolCallId: string;
        status: ToolCallStatus;
        title: string;
        kind: ToolKind;
        rawInput?: Record<string, unknown>;
        content?: ToolCallContentItem[];
        locations?: { path: string }[];
      };
      return updateSessionById(state, sessionId, s => {
        const msgs = [...s.messages];
        const last = msgs[msgs.length - 1];
        if (last && last.type === "agent") {
          msgs[msgs.length - 1] = { ...last, complete: true } as ChatItemAgent;
        }

        // Deduplicate: if a tool_call with the same toolCallId already exists, update it
        const existingIdx = msgs.findIndex(
          m =>
            m.type === "tool_call" &&
            (m as ChatItemToolCall).toolCallId === u.toolCallId
        );

        const toolCallItem: ChatItemToolCall = {
          type: "tool_call",
          id: existingIdx >= 0 ? msgs[existingIdx].id : chatItemId(),
          toolCallId: u.toolCallId,
          title: u.title,
          kind: u.kind,
          status: u.status,
          rawInput: u.rawInput,
          content: u.content,
          locations: u.locations,
        };

        if (existingIdx >= 0) {
          msgs[existingIdx] = toolCallItem;
        } else {
          msgs.push(toolCallItem);
        }

        let updated: CodingSessionData = {
          ...s,
          messages: msgs,
          selectedToolCallId: u.toolCallId,
        };
        if (u.status === "completed" || u.status === "failed") {
          updated = applyArtifactDetection(updated, toolCallItem);
        }
        return updated;
      });
    }

    case "tool_call_update": {
      const u = update.update as {
        sessionUpdate: "tool_call_update";
        toolCallId: string;
        status?: ToolCallStatus | null;
        title?: string | null;
        kind?: ToolKind | null;
        rawInput?: Record<string, unknown> | null;
        content?: ToolCallContentItem[] | null;
        locations?: { path: string }[] | null;
      };
      return updateSessionById(state, sessionId, s => {
        const hasLocationsField = hasOwn(u, "locations");
        const hasContentField = hasOwn(u, "content");
        const hasRawInputField = hasOwn(u, "rawInput");
        const hasKindField = hasOwn(u, "kind");
        const hasTitleField = hasOwn(u, "title");
        const hasStatusField = hasOwn(u, "status");

        const msgs = s.messages.map(m => {
          if (m.type === "tool_call" && m.toolCallId === u.toolCallId) {
            const tc: ChatItemToolCall = { ...m };

            if (
              hasStatusField &&
              typeof u.status === "string" &&
              u.status.length > 0
            ) {
              tc.status = u.status as ToolCallStatus;
            }
            if (hasTitleField && typeof u.title === "string" && u.title) {
              tc.title = u.title;
            }
            if (hasKindField && typeof u.kind === "string" && u.kind) {
              tc.kind = u.kind as ToolKind;
            }
            if (hasRawInputField) {
              tc.rawInput = u.rawInput ?? undefined;
            }
            if (hasContentField) {
              tc.content = u.content ?? undefined;
            }
            if (hasLocationsField) {
              tc.locations = u.locations ?? undefined;
            }

            return tc;
          }
          return m;
        });

        const mergedToolCall = msgs.find(
          m => m.type === "tool_call" && m.toolCallId === u.toolCallId
        ) as ChatItemToolCall | undefined;

        let updated = { ...s, messages: msgs };
        const reachedTerminal =
          mergedToolCall?.status === "completed" ||
          mergedToolCall?.status === "failed";
        if (reachedTerminal && mergedToolCall) {
          updated = applyArtifactDetection(updated, mergedToolCall);
        }
        return updated;
      });
    }

    case "plan": {
      const u = update.update as {
        sessionUpdate: "plan";
        entries: ChatItemPlan["entries"];
      };
      return updateSessionById(state, sessionId, s => {
        const msgs = [...s.messages];
        const planIdx = msgs.findIndex(m => m.type === "plan");
        const planItem: ChatItemPlan = {
          type: "plan",
          id: planIdx >= 0 ? msgs[planIdx].id : chatItemId(),
          entries: u.entries,
        };
        if (planIdx >= 0) {
          msgs[planIdx] = planItem;
        } else {
          msgs.push(planItem);
        }
        return { ...s, messages: msgs };
      });
    }

    case "available_commands_update": {
      const u = update.update as {
        sessionUpdate: "available_commands_update";
        availableCommands: Command[];
      };
      return { ...state, commands: u.availableCommands };
    }

    case "current_mode_update": {
      const u = update.update as {
        sessionUpdate: "current_mode_update";
        mode: string;
      };
      return updateSessionById(state, sessionId, s => ({
        ...s,
        currentModeId: u.mode,
      }));
    }

    case "config_option_update":
      return state;

    case "session_info_update": {
      const u = update.update as {
        sessionUpdate: "session_info_update";
        title?: string;
      };
      if (u.title) {
        return updateSessionById(state, sessionId, s => ({
          ...s,
          title: u.title!,
        }));
      }
      return state;
    }

    case "usage_update": {
      const u = update.update as {
        sessionUpdate: "usage_update";
        usage: CodingState["usage"];
      };
      return { ...state, usage: u.usage };
    }

    default:
      return state;
  }
}

// ===== Context =====

const CodingStateContext = createContext<CodingState>(initialState);
const CodingDispatchContext = createContext<Dispatch<CodingAction>>(() => {});

export { CodingStateContext, CodingDispatchContext };

export function CodingSessionProvider({ children }: { children: ReactNode }) {
  const [state, dispatch] = useReducer(codingReducer, initialState);
  return (
    <CodingStateContext.Provider value={state}>
      <CodingDispatchContext.Provider value={dispatch}>
        {children}
      </CodingDispatchContext.Provider>
    </CodingStateContext.Provider>
  );
}

export function useCodingState(): CodingState {
  return useContext(CodingStateContext);
}

export function useCodingDispatch(): Dispatch<CodingAction> {
  return useContext(CodingDispatchContext);
}

export function useActiveCodingSession(): CodingSessionData | null {
  const state = useCodingState();
  if (!state.activeSessionId) return null;
  return state.sessions[state.activeSessionId] ?? null;
}

export function useActiveArtifacts(): Artifact[] {
  const session = useActiveCodingSession();
  return session?.artifacts ?? [];
}
