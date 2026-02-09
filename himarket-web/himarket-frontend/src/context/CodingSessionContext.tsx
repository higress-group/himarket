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
} from "../types/acp";

// ===== Quest Data =====

export interface QuestData {
  id: string;
  title: string;
  cwd: string;
  messages: ChatItem[];
  currentModelId: string;
  currentModeId: string;
  isProcessing: boolean;
  selectedToolCallId: string | null;
  createdAt: number;
}

// ===== App State =====

export interface CodingState {
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
    id: number;
    sessionId: string;
    request: PermissionRequest;
  } | null;
}

const initialState: CodingState = {
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

export type CodingAction =
  | { type: "WS_CONNECTED" }
  | { type: "WS_DISCONNECTED" }
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
  | { type: "USER_PROMPT_SENT"; text: string }
  | { type: "PROMPT_COMPLETED"; questId: string; stopReason: string }
  | { type: "SET_MODEL"; modelId: string }
  | { type: "SET_MODE"; modeId: string }
  | { type: "SELECT_TOOL_CALL"; toolCallId: string | null }
  | {
      type: "PERMISSION_REQUEST";
      id: number;
      sessionId: string;
      request: PermissionRequest;
    }
  | { type: "PERMISSION_RESOLVED" }
  | { type: "COMMANDS_UPDATED"; commands: Command[] };

// ===== Helpers =====

let _chatItemId = 0;
function chatItemId(): string {
  return `ci-${++_chatItemId}`;
}

function getActiveQuest(state: CodingState): QuestData | null {
  if (!state.activeQuestId) return null;
  return state.quests[state.activeQuestId] ?? null;
}

function updateActiveQuest(
  state: CodingState,
  updater: (q: QuestData) => QuestData
): CodingState {
  const quest = getActiveQuest(state);
  if (!quest) return state;
  return { ...state, quests: { ...state.quests, [quest.id]: updater(quest) } };
}

function updateQuestById(
  state: CodingState,
  questId: string,
  updater: (q: QuestData) => QuestData
): CodingState {
  const quest = state.quests[questId];
  if (!quest) return state;
  return { ...state, quests: { ...state.quests, [questId]: updater(quest) } };
}

// ===== Reducer =====

function codingReducer(state: CodingState, action: CodingAction): CodingState {
  switch (action.type) {
    case "WS_CONNECTED":
      return { ...state, connected: true };

    case "WS_DISCONNECTED":
      return { ...state, connected: false, initialized: false };

    case "PROTOCOL_INITIALIZED":
      return {
        ...state,
        initialized: true,
        models: action.models,
        modes: action.modes,
      };

    case "QUEST_CREATED": {
      const quest: QuestData = {
        id: action.sessionId,
        title: `Quest ${Object.keys(state.quests).length + 1}`,
        cwd: action.cwd,
        messages: [],
        currentModelId: action.currentModelId ?? state.models[0]?.modelId ?? "",
        currentModeId: action.currentModeId ?? state.modes[0]?.id ?? "",
        isProcessing: false,
        selectedToolCallId: null,
        createdAt: Date.now(),
      };
      const newModels =
        action.models && action.models.length > 0
          ? action.models
          : state.models;
      const newModes =
        action.modes && action.modes.length > 0 ? action.modes : state.modes;
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

    case "USER_PROMPT_SENT": {
      return updateActiveQuest(state, q => ({
        ...q,
        messages: [
          ...q.messages,
          { type: "user", id: chatItemId(), text: action.text } as ChatItem,
        ],
        isProcessing: true,
      }));
    }

    case "PROMPT_COMPLETED":
      return updateQuestById(state, action.questId, q => ({
        ...q,
        isProcessing: false,
      }));

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
      const text =
        "content" in update.update
          ? ((update.update as { content?: { text?: string } }).content?.text ??
            "")
          : "";
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
        return { ...q, messages: msgs };
      });
    }

    case "agent_thought_chunk": {
      const text =
        "content" in update.update
          ? ((update.update as { content?: { text?: string } }).content?.text ??
            "")
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
        kind: "read" | "edit" | "execute";
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
        msgs.push({
          type: "tool_call",
          id: chatItemId(),
          toolCallId: u.toolCallId,
          title: u.title,
          kind: u.kind,
          status: u.status,
          rawInput: u.rawInput,
          content: u.content,
          locations: u.locations,
        } as ChatItemToolCall);
        return { ...q, messages: msgs, selectedToolCallId: u.toolCallId };
      });
    }

    case "tool_call_update": {
      const u = update.update as {
        sessionUpdate: "tool_call_update";
        toolCallId: string;
        status: ToolCallStatus;
        content?: Array<{
          type: "content";
          content: { type: "text"; text: string };
        }>;
      };
      return updateQuestById(state, sessionId, q => {
        const msgs = q.messages.map(m => {
          if (m.type === "tool_call" && m.toolCallId === u.toolCallId) {
            const newContent = u.content
              ? u.content.map(c => ({
                  type: "content" as const,
                  content: c.content,
                }))
              : m.content;
            return {
              ...m,
              status: u.status,
              content: newContent ?? m.content,
            } as ChatItemToolCall;
          }
          return m;
        });
        return { ...q, messages: msgs };
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

export function useActiveQuest(): QuestData | null {
  const state = useCodingState();
  if (!state.activeQuestId) return null;
  return state.quests[state.activeQuestId] ?? null;
}
