// ===== JSON-RPC 2.0 Base Types =====

export const JSONRPC_VERSION = "2.0" as const;

export interface AcpRequest {
  jsonrpc: typeof JSONRPC_VERSION;
  id: number;
  method: string;
  params?: Record<string, unknown>;
}

export interface AcpResponse {
  jsonrpc: typeof JSONRPC_VERSION;
  id: number;
  result?: unknown;
  error?: { code: number; message: string };
}

export interface AcpNotification {
  jsonrpc: typeof JSONRPC_VERSION;
  method: string;
  params?: Record<string, unknown>;
}

export type AcpMessage = AcpRequest | AcpResponse | AcpNotification;

// ===== ContentBlock =====

export interface TextContent {
  type: "text";
  text: string;
}

export interface ImageContent {
  type: "image";
  data?: string;
  mimeType?: string;
  uri?: string;
}

export type ContentBlock = TextContent | ImageContent;

// ===== Model / Mode =====

export interface Model {
  modelId: string;
  name: string;
}

export interface Mode {
  id: string;
  name: string;
  description?: string;
}

export interface Command {
  name: string;
  description: string;
  input?: { hint?: string } | null;
}

// ===== Initialize =====

export interface InitializeResult {
  protocolVersion: number;
  serverCapabilities: Record<string, unknown>;
}

// ===== Session =====

export interface SessionNewResult {
  sessionId: string;
  models?: {
    availableModels: Model[];
    currentModelId: string;
  };
  modes?: {
    availableModes: Mode[];
    currentModeId: string;
  };
}

export interface PromptResult {
  stopReason: string;
}

// ===== ToolCall Sub-types =====

export type ToolCallStatus = "pending" | "in_progress" | "completed" | "failed";
export type ToolKind = "read" | "edit" | "execute";

export interface ToolCallContentItem {
  type: "content" | "diff";
  content?: { type: "text"; text: string };
  path?: string;
  oldText?: string | null;
  newText?: string;
}

export interface ToolCallLocationItem {
  path: string;
}

// ===== Session Update Variants =====

interface BaseSessionUpdate {
  sessionId: string;
}

export interface AgentMessageChunkUpdate extends BaseSessionUpdate {
  update: {
    sessionUpdate: "agent_message_chunk";
    content: ContentBlock;
  };
}

export interface AgentThoughtChunkUpdate extends BaseSessionUpdate {
  update: {
    sessionUpdate: "agent_thought_chunk";
    content: TextContent;
  };
}

export interface UserMessageChunkUpdate extends BaseSessionUpdate {
  update: {
    sessionUpdate: "user_message_chunk";
    content: ContentBlock;
  };
}

export interface ToolCallUpdateNotification extends BaseSessionUpdate {
  update: {
    sessionUpdate: "tool_call";
    toolCallId: string;
    status: ToolCallStatus;
    title: string;
    kind: ToolKind;
    rawInput?: Record<string, unknown>;
    content?: ToolCallContentItem[];
    locations?: ToolCallLocationItem[];
  };
}

export interface ToolCallStatusUpdate extends BaseSessionUpdate {
  update: {
    sessionUpdate: "tool_call_update";
    toolCallId: string;
    status: ToolCallStatus;
    content?: Array<{
      type: "content";
      content: { type: "text"; text: string };
    }>;
  };
}

export interface PlanUpdateNotification extends BaseSessionUpdate {
  update: {
    sessionUpdate: "plan";
    entries: Array<{
      content: string;
      status: "pending" | "in_progress" | "completed";
      priority?: "low" | "medium" | "high";
    }>;
  };
}

export interface AvailableCommandsUpdateNotification extends BaseSessionUpdate {
  update: {
    sessionUpdate: "available_commands_update";
    availableCommands: Command[];
  };
}

export interface CurrentModeUpdateNotification extends BaseSessionUpdate {
  update: {
    sessionUpdate: "current_mode_update";
    mode: string;
    description?: string;
  };
}

export interface ConfigOptionUpdateNotification extends BaseSessionUpdate {
  update: {
    sessionUpdate: "config_option_update";
    key: string;
    value: unknown;
  };
}

export interface SessionInfoUpdateNotification extends BaseSessionUpdate {
  update: {
    sessionUpdate: "session_info_update";
    title?: string;
  };
}

export interface UsageUpdateNotification extends BaseSessionUpdate {
  update: {
    sessionUpdate: "usage_update";
    usage: {
      size: number;
      used: number;
      cost?: { amount: number; currency: string };
    };
  };
}

export type SessionUpdate =
  | AgentMessageChunkUpdate
  | AgentThoughtChunkUpdate
  | UserMessageChunkUpdate
  | ToolCallUpdateNotification
  | ToolCallStatusUpdate
  | PlanUpdateNotification
  | AvailableCommandsUpdateNotification
  | CurrentModeUpdateNotification
  | ConfigOptionUpdateNotification
  | SessionInfoUpdateNotification
  | UsageUpdateNotification;

// ===== Permission =====

export interface PermissionOption {
  optionId: string;
  name: string;
  kind: "allow_once" | "allow_always" | "reject_once" | "reject_always";
}

export interface PermissionRequest {
  sessionId: string;
  options: PermissionOption[];
  toolCall: {
    toolCallId: string;
    rawInput?: Record<string, unknown>;
    status?: string;
    title?: string;
    kind?: string;
    content?: ToolCallContentItem[];
    locations?: ToolCallLocationItem[];
  };
}

// ===== Agent → Client Requests =====

export interface FileReadRequest extends AcpRequest {
  method: "fs/read_text_file";
  params: { path: string; sessionId?: string };
}

export interface FileWriteRequest extends AcpRequest {
  method: "fs/write_text_file";
  params: { path: string; content: string; sessionId?: string };
}

// ===== ACP Protocol Methods =====

export const ACP_METHODS = {
  INITIALIZE: "initialize",
  SESSION_NEW: "session/new",
  SESSION_PROMPT: "session/prompt",
  SESSION_CANCEL: "session/cancel",
  SESSION_SET_MODEL: "session/set_model",
  SESSION_SET_MODE: "session/set_mode",
  SESSION_SET_CONFIG: "session/set_config_option",
  SESSION_UPDATE: "session/update",
  REQUEST_PERMISSION: "session/request_permission",
  READ_TEXT_FILE: "fs/read_text_file",
  WRITE_TEXT_FILE: "fs/write_text_file",
  TERMINAL_CREATE: "terminal/create",
  TERMINAL_OUTPUT: "terminal/output",
} as const;

// ===== Chat Item Types (for UI rendering) =====

export interface ChatItemUser {
  type: "user";
  id: string;
  text: string;
}

export interface ChatItemAgent {
  type: "agent";
  id: string;
  text: string;
  complete: boolean;
}

export interface ChatItemThought {
  type: "thought";
  id: string;
  text: string;
}

export interface ChatItemToolCall {
  type: "tool_call";
  id: string;
  toolCallId: string;
  title: string;
  kind: ToolKind;
  status: ToolCallStatus;
  rawInput?: Record<string, unknown>;
  content?: ToolCallContentItem[];
  locations?: ToolCallLocationItem[];
}

export interface ChatItemPlan {
  type: "plan";
  id: string;
  entries: Array<{
    content: string;
    status: "pending" | "in_progress" | "completed";
    priority?: "low" | "medium" | "high";
  }>;
}

export type ChatItem =
  | ChatItemUser
  | ChatItemAgent
  | ChatItemThought
  | ChatItemToolCall
  | ChatItemPlan;
