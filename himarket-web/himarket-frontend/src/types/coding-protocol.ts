// ===== JSON-RPC 2.0 Base Types =====

export const JSONRPC_VERSION = "2.0" as const;
export type JsonRpcId = number | string;

export interface CodingRequest {
  jsonrpc: typeof JSONRPC_VERSION;
  id: JsonRpcId;
  method: string;
  params?: Record<string, unknown>;
}

export interface CodingResponse {
  jsonrpc: typeof JSONRPC_VERSION;
  id: JsonRpcId;
  result?: unknown;
  error?: { code: number; message: string; data?: Record<string, unknown> };
}

export interface CodingNotification {
  jsonrpc: typeof JSONRPC_VERSION;
  method: string;
  params?: Record<string, unknown>;
}

export type CodingMessage = CodingRequest | CodingResponse | CodingNotification;

// ===== ContentBlock =====

export interface TextContent {
  type: "text";
  text: string;
}

export interface ImageContent {
  type: "image";
  data: string;
  mimeType: string;
  uri?: string | null;
}

export interface AudioContent {
  type: "audio";
  data: string;
  mimeType: string;
}

export interface ResourceLink {
  type: "resource_link";
  name: string;
  uri: string;
  mimeType?: string | null;
  description?: string | null;
  title?: string | null;
  size?: number | null;
}

export interface TextResourceContents {
  uri: string;
  text: string;
  mimeType?: string | null;
}

export interface BlobResourceContents {
  uri: string;
  blob: string;
  mimeType?: string | null;
}

export interface EmbeddedResource {
  type: "resource";
  resource: TextResourceContents | BlobResourceContents;
}

export type ContentBlock =
  | TextContent
  | ImageContent
  | AudioContent
  | ResourceLink
  | EmbeddedResource;

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

// ===== Agent Info =====

export interface AgentInfo {
  name?: string;
  title?: string;
  version?: string;
}

export interface AuthMethod {
  id: string;
  name: string;
  description?: string;
  type?: string;
  args?: string[];
}

export interface AgentCapabilities {
  loadSession?: boolean;
  mcpCapabilities?: Record<string, unknown>;
  promptCapabilities?: Record<string, unknown>;
  [key: string]: unknown;
}

// ===== Initialize =====

export interface InitializeResult {
  protocolVersion: number;
  serverCapabilities: Record<string, unknown>;
  agentInfo?: AgentInfo;
  authMethods?: AuthMethod[];
  modes?: {
    currentModeId: string;
    availableModes: Mode[];
  };
  agentCapabilities?: AgentCapabilities;
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
export type ToolKind =
  | "read"
  | "edit"
  | "delete"
  | "move"
  | "search"
  | "execute"
  | "think"
  | "fetch"
  | "switch_mode"
  | "other"
  | "skill";

export interface ToolCallContentTextItem {
  type: "content";
  content?: ContentBlock;
}

export interface ToolCallContentDiffItem {
  type: "diff";
  path?: string;
  oldText?: string | null;
  newText?: string | null;
}

export interface ToolCallContentTerminalItem {
  type: "terminal";
  terminalId: string;
}

export type ToolCallContentItem =
  | ToolCallContentTextItem
  | ToolCallContentDiffItem
  | ToolCallContentTerminalItem;

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
    status?: ToolCallStatus | null;
    title?: string | null;
    kind?: ToolKind | null;
    rawInput?: Record<string, unknown> | null;
    content?: ToolCallContentItem[] | null;
    locations?: ToolCallLocationItem[] | null;
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
    kind?: ToolKind | string;
    content?: ToolCallContentItem[];
    locations?: ToolCallLocationItem[];
  };
}

// ===== Agent → Client Requests =====

export interface FileReadRequest extends CodingRequest {
  method: "fs/read_text_file";
  params: { path: string; sessionId?: string };
}

export interface FileWriteRequest extends CodingRequest {
  method: "fs/write_text_file";
  params: { path: string; content: string; sessionId?: string };
}

// ===== Coding Protocol Methods =====

export const CODING_METHODS = {
  INITIALIZE: "initialize",
  SESSION_NEW: "session/new",
  SESSION_LOAD: "session/load",
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

// ===== Attachment (client-side, for coding input) =====

export interface FilePathAttachment {
  id: string;
  kind: "file_path";
  name: string;
  filePath: string; // absolute file path on server
  mimeType?: string;
  previewUrl?: string; // Object URL for local image preview (transient)
}

export type Attachment = FilePathAttachment;

// ===== Chat Item Types (for UI rendering) =====

export interface ChatItemUser {
  type: "user";
  id: string;
  text: string;
  attachments?: Attachment[];
}

export type ChatItem =
  | ChatItemUser
  | ChatItemAgent
  | ChatItemThought
  | ChatItemToolCall
  | ChatItemPlan
  | ChatItemError;

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

export interface ChatItemError {
  type: "error";
  id: string;
  code: number;
  message: string;
  data?: Record<string, unknown>;
}
