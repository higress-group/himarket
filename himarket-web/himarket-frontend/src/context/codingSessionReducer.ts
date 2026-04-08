import {
  detectArtifacts,
  detectArtifactsFromPaths,
  normalizePath,
} from '../lib/utils/artifactDetector';

import type { CodingSessionData, CodingState, CodingAction } from './codingSessionTypes';
import type { Artifact } from '../types/artifact';
import type {
  ChatItem,
  ChatItemAgent,
  ChatItemThought,
  ChatItemToolCall,
  ChatItemPlan,
  ChatItemError,
  Command,
  SessionUpdate,
  ToolCallContentItem,
  ToolCallStatus,
  ToolKind,
  ContentBlock,
} from '../types/coding-protocol';

export const initialState: CodingState = {
  activeSessionId: null,
  agentSupportsLoadSession: false,
  commands: [],
  connected: false,
  currentModeId: '',
  initialized: false,
  initProgress: null,
  models: [],
  modes: [],
  pendingPermission: null,
  sandboxStatus: null,
  sessions: {},
  usage: null,
  workspaceCwd: null,
};

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
  updater: (s: CodingSessionData) => CodingSessionData,
): CodingState {
  const session = getActiveSession(state);
  if (!session) return state;
  return { ...state, sessions: { ...state.sessions, [session.id]: updater(session) } };
}

function updateSessionById(
  state: CodingState,
  sessionId: string,
  updater: (s: CodingSessionData) => CodingSessionData,
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
  if (!cwd || filePath.startsWith('/')) return filePath;
  const base = cwd.endsWith('/') ? cwd : cwd + '/';
  return base + filePath;
}

function upsertDetectedArtifacts(q: CodingSessionData, detected: Artifact[]): CodingSessionData {
  if (detected.length === 0) return q;

  let artifacts = q.artifacts;
  let activeArtifactId = q.activeArtifactId;

  for (const artifact of detected) {
    const resolvedPath = resolveArtifactPath(normalizePath(artifact.path), q.cwd);
    const normalizedArtifact = { ...artifact, path: resolvedPath };
    const existingIdx = artifacts.findIndex(
      (a) => normalizePath(a.path) === normalizedArtifact.path,
    );

    if (existingIdx >= 0) {
      artifacts = artifacts.map((a, i) =>
        i === existingIdx
          ? {
              ...a,
              content: normalizedArtifact.content,
              path: normalizedArtifact.path,
              toolCallId: normalizedArtifact.toolCallId,
              updatedAt: normalizedArtifact.updatedAt,
            }
          : a,
      );
      const updated = artifacts[existingIdx];
      if (updated) activeArtifactId = updated.id;
    } else {
      artifacts = [...artifacts, normalizedArtifact];
      activeArtifactId = normalizedArtifact.id;
    }
  }

  return { ...q, activeArtifactId, artifacts };
}

function applyArtifactDetection(
  q: CodingSessionData,
  toolCall: ChatItemToolCall,
): CodingSessionData {
  const detected = detectArtifacts(toolCall);
  return upsertDetectedArtifacts(q, detected);
}

function applyArtifactPaths(
  q: CodingSessionData,
  paths: string[],
  toolCallId: string,
): CodingSessionData {
  const detected = detectArtifactsFromPaths(paths, toolCallId);
  return upsertDetectedArtifacts(q, detected);
}

function hasOwn(obj: object, key: string): boolean {
  return Object.prototype.hasOwnProperty.call(obj, key);
}

function extractTextFromContentBlock(content: ContentBlock | undefined): string {
  if (!content) return '';
  if (content.type === 'text') {
    return typeof content.text === 'string' ? content.text : '';
  }
  if (content.type === 'resource_link') {
    const name = typeof content.name === 'string' ? content.name : 'resource';
    return `[resource] ${name}`;
  }
  if (content.type === 'image') return '[image]';
  if (content.type === 'audio') return '[audio]';
  if (content.type === 'resource') return '[embedded resource]';
  return '';
}

// ===== Reducer =====

export function codingReducer(state: CodingState, action: CodingAction): CodingState {
  switch (action.type) {
    case 'WS_CONNECTED':
      return { ...state, connected: true };

    case 'WS_DISCONNECTED':
      return {
        ...state,
        connected: false,
        initialized: false,
        initProgress: null,
        sandboxStatus: null,
      };

    case 'RESET_STATE':
      return { ...initialState };

    case 'PROTOCOL_INITIALIZED':
      return {
        ...state,
        agentSupportsLoadSession: action.agentSupportsLoadSession ?? false,
        currentModeId: action.currentModeId || state.currentModeId,
        initialized: true,
        models: action.models,
        modes: action.modes,
      };

    case 'SESSION_CREATED': {
      const newModels = action.models && action.models.length > 0 ? action.models : state.models;
      const newModes = action.modes && action.modes.length > 0 ? action.modes : state.modes;
      const session: CodingSessionData = {
        activeArtifactId: null,
        activeFilePath: null,
        artifacts: [],
        availableModels: newModels,
        availableModes: newModes,
        createdAt: Date.now(),
        currentModeId: action.currentModeId ?? newModes[0]?.id ?? '',
        currentModelId: action.currentModelId ?? newModels[0]?.modelId ?? '',
        cwd: action.cwd,
        id: action.sessionId,
        inflightPromptId: null,
        isLoading: false,
        isProcessing: false,
        lastArtifactScanAt: Date.now(),
        lastCompletedAt: null,
        lastStopReason: null,
        messages: [],
        needsHistoryInjection: false,
        openFiles: [],
        promptQueue: [],
        selectedToolCallId: null,
        terminals: [],
        title: `Session ${Object.keys(state.sessions).length + 1}`,
      };
      return {
        ...state,
        activeSessionId: action.sessionId,
        models: newModels,
        modes: newModes,
        sessions: { ...state.sessions, [action.sessionId]: session },
      };
    }

    case 'SESSION_LOADING': {
      const loadingSession: CodingSessionData = {
        activeArtifactId: null,
        activeFilePath: null,
        artifacts: [],
        availableModels: state.models,
        availableModes: state.modes,
        createdAt: Date.now(),
        currentModeId: state.modes[0]?.id ?? '',
        currentModelId: state.models[0]?.modelId ?? '',
        cwd: action.cwd,
        id: action.sessionId,
        inflightPromptId: null,
        isLoading: true,
        isProcessing: true,
        lastArtifactScanAt: Date.now(),
        lastCompletedAt: null,
        lastStopReason: null,
        messages: [],
        needsHistoryInjection: false,
        openFiles: [],
        platformSessionId: action.platformSessionId,
        promptQueue: [],
        selectedToolCallId: null,
        terminals: [],
        title: action.title ?? 'Loading...',
      };
      return {
        ...state,
        activeSessionId: action.sessionId,
        sessions: { ...state.sessions, [action.sessionId]: loadingSession },
      };
    }

    case 'SESSION_LOADED':
      return updateSessionById(state, action.sessionId, (s) => ({
        ...s,
        isLoading: false,
        isProcessing: false,
        needsHistoryInjection: action.needsHistoryInjection ?? false,
      }));

    case 'SESSION_MIGRATED': {
      const oldSession = state.sessions[action.oldSessionId];
      if (!oldSession) return state;
      const { [action.oldSessionId]: _, ...rest } = state.sessions;
      const migrated = { ...oldSession, id: action.newSessionId };
      return {
        ...state,
        activeSessionId:
          state.activeSessionId === action.oldSessionId
            ? action.newSessionId
            : state.activeSessionId,
        sessions: { ...rest, [action.newSessionId]: migrated },
      };
    }

    case 'SESSION_SWITCHED':
      return state.sessions[action.sessionId]
        ? { ...state, activeSessionId: action.sessionId }
        : state;

    case 'SESSION_CLOSED': {
      const { [action.sessionId]: _removed, ...rest } = state.sessions;
      const newActive =
        state.activeSessionId === action.sessionId
          ? (Object.keys(rest)[0] ?? null)
          : state.activeSessionId;
      return { ...state, activeSessionId: newActive, sessions: rest };
    }

    case 'SESSION_TITLE_UPDATED':
      return updateSessionById(state, action.sessionId, (s) => ({
        ...s,
        title: action.title,
      }));

    case 'PROMPT_ENQUEUED':
      return updateSessionById(state, action.sessionId, (s) => ({
        ...s,
        promptQueue: [...s.promptQueue, action.item],
      }));

    case 'PROMPT_DEQUEUED':
      return updateSessionById(state, action.sessionId, (s) => ({
        ...s,
        promptQueue: s.promptQueue.filter((item) => item.id !== action.promptId),
      }));

    case 'PROMPT_STARTED':
      return updateSessionById(state, action.sessionId, (s) => {
        // Use the first user message as the session title when it's still "Session N"
        const isDefaultTitle = /^Session \d+$/.test(s.title);
        const hasNoUserMessages = !s.messages.some((m) => m.type === 'user');
        const newTitle = isDefaultTitle && hasNoUserMessages ? action.text.slice(0, 50) : s.title;
        return {
          ...s,
          inflightPromptId: action.requestId,
          isProcessing: true,
          messages: [
            ...s.messages,
            {
              id: chatItemId(),
              text: action.text,
              type: 'user',
              ...(action.attachments && action.attachments.length > 0
                ? { attachments: action.attachments }
                : {}),
            } as ChatItem,
          ],
          promptQueue: action.promptId
            ? s.promptQueue.filter((item) => item.id !== action.promptId)
            : s.promptQueue,
          title: newTitle,
        };
      });

    case 'PROMPT_COMPLETED':
      return updateSessionById(state, action.sessionId, (s) => {
        if (
          action.requestId !== undefined &&
          s.inflightPromptId !== null &&
          s.inflightPromptId !== action.requestId
        ) {
          return s;
        }
        return {
          ...s,
          inflightPromptId: null,
          isProcessing: false,
          lastCompletedAt: Date.now(),
          lastStopReason: action.stopReason,
        };
      });

    case 'PROMPT_ERROR':
      return updateSessionById(state, action.sessionId, (s) => {
        if (
          action.requestId !== undefined &&
          s.inflightPromptId !== null &&
          s.inflightPromptId !== action.requestId
        ) {
          return s;
        }
        const errorItem: ChatItemError = {
          code: action.code,
          id: chatItemId(),
          message: action.message,
          type: 'error',
          ...(action.data ? { data: action.data } : {}),
        };
        return {
          ...s,
          inflightPromptId: null,
          isProcessing: false,
          lastCompletedAt: Date.now(),
          lastStopReason: 'error',
          messages: [...s.messages, errorItem],
        };
      });

    case 'SET_MODEL':
      return updateActiveSession(state, (s) => ({
        ...s,
        currentModelId: action.modelId,
      }));

    case 'SET_MODE':
      return updateActiveSession(state, (s) => ({
        ...s,
        currentModeId: action.modeId,
      }));

    case 'SELECT_TOOL_CALL':
      return updateActiveSession(state, (s) => ({
        ...s,
        selectedToolCallId: action.toolCallId,
      }));

    case 'SELECT_ARTIFACT':
      return updateActiveSession(state, (s) => ({
        ...s,
        activeArtifactId: action.artifactId,
      }));

    case 'UPDATE_ARTIFACT_CONTENT':
      return updateActiveSession(state, (s) => ({
        ...s,
        artifacts: s.artifacts.map((a) =>
          a.id === action.artifactId ? { ...a, content: action.content, updatedAt: Date.now() } : a,
        ),
      }));

    case 'UPSERT_ARTIFACTS_FROM_PATHS':
      return updateSessionById(state, action.sessionId, (s) =>
        applyArtifactPaths(s, action.paths, action.toolCallId),
      );

    case 'SET_ARTIFACT_SCAN_CURSOR':
      return updateSessionById(state, action.sessionId, (s) => ({
        ...s,
        lastArtifactScanAt: action.cursor,
      }));

    case 'PERMISSION_REQUEST':
      return {
        ...state,
        pendingPermission: {
          id: action.id,
          request: action.request,
          sessionId: action.sessionId,
        },
      };

    case 'PERMISSION_RESOLVED':
      return { ...state, pendingPermission: null };

    case 'COMMANDS_UPDATED':
      return { ...state, commands: action.commands };

    case 'SESSION_UPDATE':
      return handleSessionUpdate(state, action.sessionId, action.update);

    case 'HISTORY_INJECTED':
      return updateSessionById(state, action.sessionId, (s) => ({
        ...s,
        needsHistoryInjection: false,
      }));

    // ===== Coding IDE Actions =====

    case 'FILE_OPENED':
      return updateSessionById(state, action.sessionId, (s) => {
        const exists = s.openFiles.some((f) => f.path === action.file.path);
        return {
          ...s,
          activeFilePath: action.file.path,
          openFiles: exists
            ? s.openFiles.map((f) => (f.path === action.file.path ? action.file : f))
            : [...s.openFiles, action.file],
        };
      });

    case 'FILE_CLOSED':
      return updateSessionById(state, action.sessionId, (s) => {
        const newFiles = s.openFiles.filter((f) => f.path !== action.path);
        let newActive = s.activeFilePath;
        if (s.activeFilePath === action.path) {
          const lastFile = newFiles.at(-1);
          newActive = lastFile ? lastFile.path : null;
        }
        return { ...s, activeFilePath: newActive, openFiles: newFiles };
      });

    case 'ACTIVE_FILE_CHANGED':
      return updateSessionById(state, action.sessionId, (s) => ({
        ...s,
        activeFilePath: action.path,
      }));

    case 'TERMINAL_CREATED':
      return updateSessionById(state, action.sessionId, (s) => {
        const exists = s.terminals.some((t) => t.id === action.terminalId);
        if (exists) return s;
        return {
          ...s,
          terminals: [...s.terminals, { id: action.terminalId, lines: [] }],
        };
      });

    case 'TERMINAL_DATA':
      return updateSessionById(state, action.sessionId, (s) => {
        const hasTerminal = s.terminals.some((t) => t.id === action.terminalId);
        const terminals = hasTerminal
          ? s.terminals.map((t) =>
              t.id === action.terminalId ? { ...t, lines: [...t.lines, action.data] } : t,
            )
          : [...s.terminals, { id: action.terminalId, lines: [action.data] }];
        return { ...s, terminals };
      });

    case 'SANDBOX_STATUS':
      return {
        ...state,
        sandboxStatus: {
          message: action.message,
          sandboxHost: action.sandboxHost ?? state.sandboxStatus?.sandboxHost,
          status: action.status,
        },
      };

    case 'INIT_PROGRESS':
      return {
        ...state,
        initProgress: {
          completedPhases: action.completedPhases,
          message: action.message,
          phase: action.phase as
            | 'sandbox-acquire'
            | 'filesystem-ready'
            | 'config-injection'
            | 'sidecar-connect'
            | 'cli-ready',
          progress: action.progress,
          status: action.status,
          totalPhases: action.totalPhases,
        },
      };

    case 'WORKSPACE_INFO':
      return { ...state, workspaceCwd: action.cwd };

    case 'SET_PLATFORM_SESSION_ID':
      return updateSessionById(state, action.sessionId, (s) => ({
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
  update: SessionUpdate,
): CodingState {
  const variant = update.update.sessionUpdate;

  switch (variant) {
    case 'agent_message_chunk': {
      const contentBlock =
        'content' in update.update
          ? (update.update as { content?: ContentBlock }).content
          : undefined;
      const text = extractTextFromContentBlock(contentBlock);
      return updateSessionById(state, sessionId, (s) => {
        const msgs = [...s.messages];
        const last = msgs[msgs.length - 1];
        if (last && last.type === 'agent') {
          msgs[msgs.length - 1] = {
            ...last,
            text: last.text + text,
          } as ChatItemAgent;
        } else {
          msgs.push({ complete: false, id: chatItemId(), text, type: 'agent' });
        }

        let updated = { ...s, messages: msgs };

        // Detect artifacts from resource_link content blocks
        if (contentBlock?.type === 'resource_link' && typeof contentBlock.uri === 'string') {
          let filePath = contentBlock.uri;
          if (filePath.startsWith('file://')) filePath = filePath.slice(7);
          if (filePath && !filePath.startsWith('http')) {
            updated = applyArtifactPaths(updated, [filePath], 'resource-link');
          }
        }

        return updated;
      });
    }

    case 'agent_thought_chunk': {
      const text =
        'content' in update.update
          ? extractTextFromContentBlock((update.update as { content?: ContentBlock }).content)
          : '';
      return updateSessionById(state, sessionId, (s) => {
        const msgs = [...s.messages];
        const last = msgs[msgs.length - 1];
        if (last && last.type === 'thought') {
          msgs[msgs.length - 1] = {
            ...last,
            text: last.text + text,
          } as ChatItemThought;
        } else {
          msgs.push({ id: chatItemId(), text, type: 'thought' });
        }
        return { ...s, messages: msgs };
      });
    }

    case 'user_message_chunk': {
      // session/load 回放历史时，sidecar 会重播 user_message_chunk；
      // 仅当会话处于 isLoading 状态时才追加（正常对话中用户消息已由 PROMPT_STARTED 添加）。
      const session = state.sessions[sessionId];
      if (!session || !session.isLoading) return state;

      const userContent =
        'content' in update.update
          ? (update.update as { content?: ContentBlock }).content
          : undefined;
      const userText = extractTextFromContentBlock(userContent);
      if (!userText) return state;

      return updateSessionById(state, sessionId, (s) => ({
        ...s,
        messages: [...s.messages, { id: chatItemId(), text: userText, type: 'user' as const }],
      }));
    }

    case 'tool_call': {
      const u = update.update as {
        sessionUpdate: 'tool_call';
        toolCallId: string;
        status: ToolCallStatus;
        title: string;
        kind: ToolKind;
        rawInput?: Record<string, unknown>;
        content?: ToolCallContentItem[];
        locations?: { path: string }[];
      };
      return updateSessionById(state, sessionId, (s) => {
        const msgs = [...s.messages];
        const last = msgs[msgs.length - 1];
        if (last && last.type === 'agent') {
          msgs[msgs.length - 1] = { ...last, complete: true } as ChatItemAgent;
        }

        // Deduplicate: if a tool_call with the same toolCallId already exists, update it
        const existingIdx = msgs.findIndex(
          (m) => m.type === 'tool_call' && (m as ChatItemToolCall).toolCallId === u.toolCallId,
        );

        const existingMsg = existingIdx >= 0 ? msgs.at(existingIdx) : undefined;
        const toolCallItem: ChatItemToolCall = {
          content: u.content,
          id: existingMsg?.id ?? chatItemId(),
          kind: u.kind,
          locations: u.locations,
          rawInput: u.rawInput,
          status: u.status,
          title: u.title,
          toolCallId: u.toolCallId,
          type: 'tool_call',
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
        if (u.status === 'completed' || u.status === 'failed') {
          updated = applyArtifactDetection(updated, toolCallItem);
        }
        return updated;
      });
    }

    case 'tool_call_update': {
      const u = update.update as {
        sessionUpdate: 'tool_call_update';
        toolCallId: string;
        status?: ToolCallStatus | null;
        title?: string | null;
        kind?: ToolKind | null;
        rawInput?: Record<string, unknown> | null;
        content?: ToolCallContentItem[] | null;
        locations?: { path: string }[] | null;
      };
      return updateSessionById(state, sessionId, (s) => {
        const hasLocationsField = hasOwn(u, 'locations');
        const hasContentField = hasOwn(u, 'content');
        const hasRawInputField = hasOwn(u, 'rawInput');
        const hasKindField = hasOwn(u, 'kind');
        const hasTitleField = hasOwn(u, 'title');
        const hasStatusField = hasOwn(u, 'status');

        const msgs = s.messages.map((m) => {
          if (m.type === 'tool_call' && m.toolCallId === u.toolCallId) {
            const tc: ChatItemToolCall = { ...m };

            if (hasStatusField && typeof u.status === 'string' && u.status.length > 0) {
              tc.status = u.status as ToolCallStatus;
            }
            if (hasTitleField && typeof u.title === 'string' && u.title) {
              tc.title = u.title;
            }
            if (hasKindField && typeof u.kind === 'string' && u.kind) {
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
          (m) => m.type === 'tool_call' && m.toolCallId === u.toolCallId,
        ) as ChatItemToolCall | undefined;

        let updated = { ...s, messages: msgs };
        const reachedTerminal =
          mergedToolCall?.status === 'completed' || mergedToolCall?.status === 'failed';
        if (reachedTerminal && mergedToolCall) {
          updated = applyArtifactDetection(updated, mergedToolCall);
        }
        return updated;
      });
    }

    case 'plan': {
      const u = update.update as {
        sessionUpdate: 'plan';
        entries: ChatItemPlan['entries'];
      };
      return updateSessionById(state, sessionId, (s) => {
        const msgs = [...s.messages];
        const planIdx = msgs.findIndex((m) => m.type === 'plan');
        const existingPlan = planIdx >= 0 ? msgs.at(planIdx) : undefined;
        const planItem: ChatItemPlan = {
          entries: u.entries,
          id: existingPlan?.id ?? chatItemId(),
          type: 'plan',
        };
        if (planIdx >= 0) {
          msgs[planIdx] = planItem;
        } else {
          msgs.push(planItem);
        }
        return { ...s, messages: msgs };
      });
    }

    case 'available_commands_update': {
      const u = update.update as {
        sessionUpdate: 'available_commands_update';
        availableCommands: Command[];
      };
      return { ...state, commands: u.availableCommands };
    }

    case 'current_mode_update': {
      const u = update.update as {
        sessionUpdate: 'current_mode_update';
        mode: string;
      };
      return updateSessionById(state, sessionId, (s) => ({
        ...s,
        currentModeId: u.mode,
      }));
    }

    case 'config_option_update':
      return state;

    case 'session_info_update': {
      const u = update.update as {
        sessionUpdate: 'session_info_update';
        title?: string;
      };
      if (u.title) {
        const title = u.title;
        return updateSessionById(state, sessionId, (s) => ({
          ...s,
          title,
        }));
      }
      return state;
    }

    case 'usage_update': {
      const u = update.update as {
        sessionUpdate: 'usage_update';
        usage: CodingState['usage'];
      };
      return { ...state, usage: u.usage };
    }

    default:
      return state;
  }
}
