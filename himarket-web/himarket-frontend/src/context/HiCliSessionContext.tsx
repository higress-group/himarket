import {
  createContext,
  useContext,
  useReducer,
  type Dispatch,
  type ReactNode,
} from "react";
import type { RawMessage, AggregatedLogEntry } from "../types/log";
import type { AgentInfo, AuthMethod, AgentCapabilities } from "../types/acp";
import type { RuntimeType } from "../types/runtime";
import {
  type QuestState,
  type QuestAction,
  initialState as questInitialState,
  questReducer,
  QuestStateContext,
  QuestDispatchContext,
} from "./QuestSessionContext";

// ===== HiCli Extended State =====

export interface HiCliState extends QuestState {
  /** 原始消息列表（调试面板用） */
  rawMessages: RawMessage[];
  /** 聚合日志列表（调试面板用） */
  aggregatedLogs: AggregatedLogEntry[];
  /** Agent 元数据信息 */
  agentInfo: AgentInfo | null;
  /** Agent 支持的认证方式 */
  authMethods: AuthMethod[];
  /** Agent 能力配置 */
  agentCapabilities: AgentCapabilities | null;
  /** Mode 数据来源标注 */
  modesSource: "initialize" | "session_new" | null;
  /** 当前选中的 CLI 工具 ID */
  selectedCliId: string | null;
  /** 当前工作目录 */
  cwd: string;
  /** 当前运行时类型 */
  runtimeType: RuntimeType | null;
}

export const hiCliInitialState: HiCliState = {
  ...questInitialState,
  rawMessages: [],
  aggregatedLogs: [],
  agentInfo: null,
  authMethods: [],
  agentCapabilities: null,
  modesSource: null,
  selectedCliId: null,
  cwd: "",
  runtimeType: null,
};

// ===== HiCli Actions =====

export type HiCliAction =
  | QuestAction
  | { type: "RAW_MESSAGE"; message: RawMessage }
  | { type: "AGGREGATED_LOG"; entry: AggregatedLogEntry }
  | { type: "CLI_SELECTED"; cliId: string; cwd: string; runtime?: string }
  | { type: "WORKSPACE_INFO"; cwd: string }
  | {
      type: "DEBUG_PROTOCOL_INITIALIZED";
      agentInfo?: AgentInfo;
      authMethods?: AuthMethod[];
      agentCapabilities?: AgentCapabilities;
      modesSource?: "initialize" | "session_new" | null;
    };

// ===== Reducer =====

export function hiCliReducer(
  state: HiCliState,
  action: HiCliAction
): HiCliState {
  switch (action.type) {
    case "RAW_MESSAGE":
      return {
        ...state,
        rawMessages: [...state.rawMessages, action.message],
      };

    case "AGGREGATED_LOG":
      return {
        ...state,
        aggregatedLogs: [...state.aggregatedLogs, action.entry],
      };

    case "CLI_SELECTED":
      return {
        ...state,
        selectedCliId: action.cliId,
        cwd: action.cwd,
        runtimeType: (action.runtime as RuntimeType) ?? "local",
        // 切换 CLI 时清空调试状态
        rawMessages: [],
        aggregatedLogs: [],
        agentInfo: null,
        authMethods: [],
        agentCapabilities: null,
        modesSource: null,
      };

    case "WORKSPACE_INFO":
      return {
        ...state,
        cwd: action.cwd,
      };

    case "DEBUG_PROTOCOL_INITIALIZED":
      return {
        ...state,
        agentInfo: action.agentInfo ?? state.agentInfo,
        authMethods: action.authMethods ?? state.authMethods,
        agentCapabilities: action.agentCapabilities ?? state.agentCapabilities,
        modesSource: action.modesSource ?? state.modesSource,
      };

    case "RESET_STATE":
      return { ...hiCliInitialState };

    default:
      // 委托给 questReducer 处理共享 action
      return {
        ...questReducer(state, action as QuestAction),
        rawMessages: state.rawMessages,
        aggregatedLogs: state.aggregatedLogs,
        agentInfo: state.agentInfo,
        authMethods: state.authMethods,
        agentCapabilities: state.agentCapabilities,
        modesSource: state.modesSource,
        selectedCliId: state.selectedCliId,
        cwd: state.cwd,
        runtimeType: state.runtimeType,
      };
  }
}

// ===== Context =====

const HiCliStateContext = createContext<HiCliState>(hiCliInitialState);
const HiCliDispatchContext = createContext<Dispatch<HiCliAction>>(() => {});

export function HiCliSessionProvider({ children }: { children: ReactNode }) {
  const [state, dispatch] = useReducer(hiCliReducer, hiCliInitialState);
  return (
    <HiCliStateContext.Provider value={state}>
      <HiCliDispatchContext.Provider value={dispatch}>
        {/* 同时提供 QuestStateContext/QuestDispatchContext，让复用组件（ChatStream、QuestInput 等）能正常读取 quest 状态 */}
        <QuestStateContext.Provider value={state as QuestState}>
          <QuestDispatchContext.Provider value={dispatch as Dispatch<QuestAction>}>
            {children}
          </QuestDispatchContext.Provider>
        </QuestStateContext.Provider>
      </HiCliDispatchContext.Provider>
    </HiCliStateContext.Provider>
  );
}

export function useHiCliState(): HiCliState {
  return useContext(HiCliStateContext);
}

export function useHiCliDispatch(): Dispatch<HiCliAction> {
  return useContext(HiCliDispatchContext);
}
