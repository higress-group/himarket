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
} from "../types/acp";
import type { Artifact } from "../types/artifact";
import type { OpenFile, TerminalSession } from "../types/coding";
import {
  detectArtifacts,
  detectArtifactsFromPaths,
} from "../lib/utils/artifactDetector";

// ===== Quest Data =====

export interface QuestData {
  id: string;
  title: string;
  cwd: string;
  messages: ChatItem[];
  availableModels: Model[];
  availableModes: Mode[];
  currentModelId: string;
  currentModeId: string;
  isProcessing: boolean;
  inflightPromptId: JsonRpcId | null;
  promptQueue: QueuedPromptItem[];
  lastStopReason: string | null;
  lastCompletedAt: number | null;
  selectedToolCallId: string | null;
  artifacts: Artifact[];
  activeArtifactId: string | null;
  lastArtifactScanAt: number;
  createdAt: number;
  // Coding IDE state
  openFiles: OpenFile[];
  activeFilePath: string | null;
  terminals: TerminalSession[];
  previewPort: number | null;
}

export interface QueuedPromptItem {
  id: string;
  text: string;
  attachments?: Attachment[];
  createdAt: number;
}

// ===== App State =====

export interface QuestState {
  connected: boolean;
  initialized: boolean;
  quests: Record<string, QuestData>;
  activeQuestId: string | null;
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
}

export const initialState: QuestState = {
  connected: false,
  initialized: false,
  quests: {},
  activeQuestId: null,
  models: [],
  modes: [],
  commands: [],
  usage: null,
  pendingPermission: null,
};

// ===== Actions =====

export type QuestAction =
  | { type: "WS_CONNECTED" }
  | { type: "WS_DISCONNECTED" }
  | { type: "RESET_STATE" }
  | {
      type: "PROTOCOL_INITIALIZED";
      models: Model[];
      modes: Mode[];
      currentModelId: string;
      currentModeId: string;
    }
  | {
      type: "QUEST_CREATED";
      sessionId: string;
      cwd: string;
      models?: Model[];
      modes?: Mode[];
      currentModelId?: string;
      currentModeId?: string;
    }
  | { type: "QUEST_SWITCHED"; questId: string }
  | { type: "QUEST_CLOSED"; questId: string }
  | { type: "QUEST_TITLE_UPDATED"; questId: string; title: string }
  | { type: "SESSION_UPDATE"; sessionId: string; update: SessionUpdate }
  | { type: "PROMPT_ENQUEUED"; questId: string; item: QueuedPromptItem }
  | { type: "PROMPT_DEQUEUED"; questId: string; promptId: string }
  | {
      type: "PROMPT_STARTED";
      questId: string;
      requestId: JsonRpcId;
      text: string;
      attachments?: Attachment[];
      promptId?: string;
    }
  | {
      type: "PROMPT_COMPLETED";
      questId: string;
      requestId?: JsonRpcId;
      stopReason: string;
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
      questId: string;
      toolCallId: string;
      paths: string[];
    }
  | { type: "SET_ARTIFACT_SCAN_CURSOR"; questId: string; cursor: number }
  // Coding IDE actions
  | { type: "FILE_OPENED"; questId: string; file: OpenFile }
  | { type: "FILE_CLOSED"; questId: string; path: string }
  | { type: "ACTIVE_FILE_CHANGED"; questId: string; path: string | null }
  | { type: "TERMINAL_CREATED"; questId: string; terminalId: string }
  | { type: "TERMINAL_DATA"; questId: string; terminalId: string; data: string }
  | { type: "PREVIEW_PORT_DETECTED"; questId: string; port: number };

// ===== Helpers =====

let _chatItemId = 0;
function chatItemId(): string {
  return `ci-${++_chatItemId}`;
}

function getActiveQuest(state: QuestState): QuestData | null {
  if (!state.activeQuestId) return null;
  return state.quests[state.activeQuestId] ?? null;
}

function updateActiveQuest(
  state: QuestState,
  updater: (q: QuestData) => QuestData
): QuestState {
  const quest = getActiveQuest(state);
  if (!quest) return state;
  return { ...state, quests: { ...state.quests, [quest.id]: updater(quest) } };
}

function updateQuestById(
  state: QuestState,
  questId: string,
  updater: (q: QuestData) => QuestData
): QuestState {
  const quest = state.quests[questId];
  if (!quest) return state;
  return { ...state, quests: { ...state.quests, [questId]: updater(quest) } };
}

// ===== Artifact Helpers =====

function upsertDetectedArtifacts(
  q: QuestData,
  detected: Artifact[]
): QuestData {
  if (detected.length === 0) return q;

  let artifacts = q.artifacts;
  let activeArtifactId = q.activeArtifactId;

  for (const artifact of detected) {
    const existingIdx = artifacts.findIndex(a => a.path === artifact.path);

    if (existingIdx >= 0) {
      artifacts = artifacts.map((a, i) =>
        i === existingIdx
          ? {
              ...a,
              content: artifact.content,
              updatedAt: artifact.updatedAt,
              toolCallId: artifact.toolCallId,
            }
          : a
      );
      activeArtifactId = artifacts[existingIdx].id;
    } else {
      artifacts = [...artifacts, artifact];
      activeArtifactId = artifact.id;
    }
  }

  return { ...q, artifacts, activeArtifactId };
}

function applyArtifactDetection(
  q: QuestData,
  toolCall: ChatItemToolCall
): QuestData {
  const detected = detectArtifacts(toolCall);
  return upsertDetectedArtifacts(q, detected);
}

function applyArtifactPaths(
  q: QuestData,
  paths: string[],
  toolCallId: string
): QuestData {
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

export function questReducer(
  state: QuestState,
  action: QuestAction
): QuestState {
  switch (action.type) {
    case "WS_CONNECTED":
      return { ...state, connected: true };

    case "WS_DISCONNECTED":
      return { ...state, connected: false, initialized: false };

    case "RESET_STATE":
      return { ...initialState };

    case "PROTOCOL_INITIALIZED":
      return {
        ...state,
        initialized: true,
        models: action.models,
        modes: action.modes,
      };

    case "QUEST_CREATED": {
      const newModels =
        action.models && action.models.length > 0
          ? action.models
          : state.models;
      const newModes =
        action.modes && action.modes.length > 0 ? action.modes : state.modes;
      const quest: QuestData = {
        id: action.sessionId,
        title: `Quest ${Object.keys(state.quests).length + 1}`,
        cwd: action.cwd,
        messages: [],
        availableModels: newModels,
        availableModes: newModes,
        currentModelId: action.currentModelId ?? newModels[0]?.modelId ?? "",
        currentModeId: action.currentModeId ?? newModes[0]?.id ?? "",
        isProcessing: false,
        inflightPromptId: null,
        promptQueue: [],
        lastStopReason: null,
        lastCompletedAt: null,
        selectedToolCallId: null,
        artifacts: [],
        activeArtifactId: null,
        lastArtifactScanAt: Date.now(),
        createdAt: Date.now(),
        openFiles: [],
        activeFilePath: null,
        terminals: [],
        previewPort: null,
      };
      return {
        ...state,
        quests: { ...state.quests, [action.sessionId]: quest },
        activeQuestId: action.sessionId,
        models: newModels,
        modes: newModes,
      };
    }

    case "QUEST_SWITCHED":
      return state.quests[action.questId]
        ? { ...state, activeQuestId: action.questId }
        : state;

    case "QUEST_CLOSED": {
      // eslint-disable-next-line @typescript-eslint/no-unused-vars
      const { [action.questId]: _removed, ...rest } = state.quests;
      const newActive =
        state.activeQuestId === action.questId
          ? (Object.keys(rest)[0] ?? null)
          : state.activeQuestId;
      return { ...state, quests: rest, activeQuestId: newActive };
    }

    case "QUEST_TITLE_UPDATED":
      return updateQuestById(state, action.questId, q => ({
        ...q,
        title: action.title,
      }));

    case "PROMPT_ENQUEUED":
      return updateQuestById(state, action.questId, q => ({
        ...q,
        promptQueue: [...q.promptQueue, action.item],
      }));

    case "PROMPT_DEQUEUED":
      return updateQuestById(state, action.questId, q => ({
        ...q,
        promptQueue: q.promptQueue.filter(item => item.id !== action.promptId),
      }));

    case "PROMPT_STARTED":
      return updateQuestById(state, action.questId, q => ({
        ...q,
        promptQueue: action.promptId
          ? q.promptQueue.filter(item => item.id !== action.promptId)
          : q.promptQueue,
        messages: [
          ...q.messages,
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
      }));

    case "PROMPT_COMPLETED":
      return updateQuestById(state, action.questId, q => {
        if (
          action.requestId !== undefined &&
          q.inflightPromptId !== null &&
          q.inflightPromptId !== action.requestId
        ) {
          return q;
        }
        return {
          ...q,
          isProcessing: false,
          inflightPromptId: null,
          lastStopReason: action.stopReason,
          lastCompletedAt: Date.now(),
        };
      });

    case "SET_MODEL":
      return updateActiveQuest(state, q => ({
        ...q,
        currentModelId: action.modelId,
      }));

    case "SET_MODE":
      return updateActiveQuest(state, q => ({
        ...q,
        currentModeId: action.modeId,
      }));

    case "SELECT_TOOL_CALL":
      return updateActiveQuest(state, q => ({
        ...q,
        selectedToolCallId: action.toolCallId,
      }));

    case "SELECT_ARTIFACT":
      return updateActiveQuest(state, q => ({
        ...q,
        activeArtifactId: action.artifactId,
      }));

    case "UPDATE_ARTIFACT_CONTENT":
      return updateActiveQuest(state, q => ({
        ...q,
        artifacts: q.artifacts.map(a =>
          a.id === action.artifactId
            ? { ...a, content: action.content, updatedAt: Date.now() }
            : a
        ),
      }));

    case "UPSERT_ARTIFACTS_FROM_PATHS":
      return updateQuestById(state, action.questId, q =>
        applyArtifactPaths(q, action.paths, action.toolCallId)
      );

    case "SET_ARTIFACT_SCAN_CURSOR":
      return updateQuestById(state, action.questId, q => ({
        ...q,
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

    // ===== Coding IDE Actions =====

    case "FILE_OPENED":
      return updateQuestById(state, action.questId, q => {
        const exists = q.openFiles.some(f => f.path === action.file.path);
        return {
          ...q,
          openFiles: exists
            ? q.openFiles.map(f =>
                f.path === action.file.path ? action.file : f
              )
            : [...q.openFiles, action.file],
          activeFilePath: action.file.path,
        };
      });

    case "FILE_CLOSED":
      return updateQuestById(state, action.questId, q => {
        const newFiles = q.openFiles.filter(f => f.path !== action.path);
        let newActive = q.activeFilePath;
        if (q.activeFilePath === action.path) {
          newActive =
            newFiles.length > 0 ? newFiles[newFiles.length - 1].path : null;
        }
        return { ...q, openFiles: newFiles, activeFilePath: newActive };
      });

    case "ACTIVE_FILE_CHANGED":
      return updateQuestById(state, action.questId, q => ({
        ...q,
        activeFilePath: action.path,
      }));

    case "TERMINAL_CREATED":
      return updateQuestById(state, action.questId, q => {
        const exists = q.terminals.some(t => t.id === action.terminalId);
        if (exists) return q;
        return {
          ...q,
          terminals: [...q.terminals, { id: action.terminalId, lines: [] }],
        };
      });

    case "TERMINAL_DATA":
      return updateQuestById(state, action.questId, q => {
        const hasTerminal = q.terminals.some(t => t.id === action.terminalId);
        const terminals = hasTerminal
          ? q.terminals.map(t =>
              t.id === action.terminalId
                ? { ...t, lines: [...t.lines, action.data] }
                : t
            )
          : [
              ...q.terminals,
              { id: action.terminalId, lines: [action.data] },
            ];
        return { ...q, terminals };
      });

    case "PREVIEW_PORT_DETECTED":
      return updateQuestById(state, action.questId, q => ({
        ...q,
        previewPort: action.port,
      }));

    default:
      return state;
  }
}

function handleSessionUpdate(
  state: QuestState,
  sessionId: string,
  update: SessionUpdate
): QuestState {
  const variant = update.update.sessionUpdate;

  switch (variant) {
    case "agent_message_chunk": {
      const contentBlock =
        "content" in update.update
          ? (update.update as { content?: ContentBlock }).content
          : undefined;
      const text = extractTextFromContentBlock(contentBlock);
      return updateQuestById(state, sessionId, q => {
        const msgs = [...q.messages];
        const last = msgs[msgs.length - 1];
        if (last && last.type === "agent") {
          msgs[msgs.length - 1] = {
            ...last,
            text: last.text + text,
          } as ChatItemAgent;
        } else {
          msgs.push({ type: "agent", id: chatItemId(), text, complete: false });
        }

        let updated = { ...q, messages: msgs };

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
      return updateQuestById(state, sessionId, q => {
        const msgs = [...q.messages];
        const last = msgs[msgs.length - 1];
        if (last && last.type === "thought") {
          msgs[msgs.length - 1] = {
            ...last,
            text: last.text + text,
          } as ChatItemThought;
        } else {
          msgs.push({ type: "thought", id: chatItemId(), text });
        }
        return { ...q, messages: msgs };
      });
    }

    case "user_message_chunk":
      return state;

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
      return updateQuestById(state, sessionId, q => {
        const msgs = [...q.messages];
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

        let updated: QuestData = {
          ...q,
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
      return updateQuestById(state, sessionId, q => {
        const hasLocationsField = hasOwn(u, "locations");
        const hasContentField = hasOwn(u, "content");
        const hasRawInputField = hasOwn(u, "rawInput");
        const hasKindField = hasOwn(u, "kind");
        const hasTitleField = hasOwn(u, "title");
        const hasStatusField = hasOwn(u, "status");

        const msgs = q.messages.map(m => {
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

        let updated = { ...q, messages: msgs };
        const reachedTerminal =
          mergedToolCall?.status === "completed" ||
          mergedToolCall?.status === "failed";
        if ((reachedTerminal || hasLocationsField) && mergedToolCall) {
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
      return updateQuestById(state, sessionId, q => {
        const msgs = [...q.messages];
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
        return { ...q, messages: msgs };
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
      return updateQuestById(state, sessionId, q => ({
        ...q,
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
        return updateQuestById(state, sessionId, q => ({
          ...q,
          title: u.title!,
        }));
      }
      return state;
    }

    case "usage_update": {
      const u = update.update as {
        sessionUpdate: "usage_update";
        usage: QuestState["usage"];
      };
      return { ...state, usage: u.usage };
    }

    default:
      return state;
  }
}

// ===== Context =====

const QuestStateContext = createContext<QuestState>(initialState);
const QuestDispatchContext = createContext<Dispatch<QuestAction>>(() => {});

export function QuestSessionProvider({ children }: { children: ReactNode }) {
  const [state, dispatch] = useReducer(questReducer, initialState);
  return (
    <QuestStateContext.Provider value={state}>
      <QuestDispatchContext.Provider value={dispatch}>
        {children}
      </QuestDispatchContext.Provider>
    </QuestStateContext.Provider>
  );
}

export function useQuestState(): QuestState {
  return useContext(QuestStateContext);
}

export function useQuestDispatch(): Dispatch<QuestAction> {
  return useContext(QuestDispatchContext);
}

export function useActiveQuest(): QuestData | null {
  const state = useQuestState();
  if (!state.activeQuestId) return null;
  return state.quests[state.activeQuestId] ?? null;
}

export function useActiveArtifacts(): Artifact[] {
  const quest = useActiveQuest();
  return quest?.artifacts ?? [];
}
