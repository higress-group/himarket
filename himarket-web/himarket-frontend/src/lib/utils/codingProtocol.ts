import {
  JSONRPC_VERSION,
  CODING_METHODS,
  type JsonRpcId,
  type CodingRequest,
  type CodingResponse,
  type CodingNotification,
  type CodingMessage,
  type SessionUpdate,
  type PermissionRequest,
  type ContentBlock,
  type Attachment,
} from "../../types/coding-protocol";

// ===== Request ID Management =====

let _nextId = 1;

export function nextId(): number {
  return _nextId++;
}

export function resetNextId(): void {
  _nextId = 1;
}

// ===== Request Builders =====

export function buildRequest(
  method: string,
  params?: Record<string, unknown>
): CodingRequest {
  return { jsonrpc: JSONRPC_VERSION, id: nextId(), method, params };
}

export function buildNotification(
  method: string,
  params?: Record<string, unknown>
): CodingNotification {
  return { jsonrpc: JSONRPC_VERSION, method, params };
}

export function buildResponse(id: JsonRpcId, result: unknown): CodingResponse {
  return { jsonrpc: JSONRPC_VERSION, id, result };
}

// ===== Shortcut Builders =====

export function buildInitialize(): CodingRequest {
  return buildRequest(CODING_METHODS.INITIALIZE, {
    protocolVersion: 1,
    clientCapabilities: {
      fs: { readTextFile: false, writeTextFile: false },
    },
  });
}

export function buildSessionNew(cwd: string): CodingRequest {
  return buildRequest(CODING_METHODS.SESSION_NEW, { cwd, mcpServers: [] });
}

export function buildSessionLoad(sessionId: string, cwd: string): CodingRequest {
  return buildRequest(CODING_METHODS.SESSION_LOAD, { sessionId, cwd, mcpServers: [] });
}

export function buildPrompt(
  sessionId: string,
  text: string,
  attachments?: Attachment[]
): CodingRequest {
  const prompt: ContentBlock[] = [];

  if (attachments && attachments.length > 0) {
    for (const att of attachments) {
      const uri = att.filePath.startsWith("file://")
        ? att.filePath
        : `file://${att.filePath}`;
      prompt.push({
        type: "resource_link",
        name: att.name,
        uri,
        mimeType: att.mimeType ?? null,
      });
    }
  }

  if (text) {
    prompt.push({ type: "text", text });
  }

  return buildRequest(CODING_METHODS.SESSION_PROMPT, { sessionId, prompt });
}

export function buildCancel(sessionId: string): CodingNotification {
  return buildNotification(CODING_METHODS.SESSION_CANCEL, { sessionId });
}

export function buildSetModel(sessionId: string, modelId: string): CodingRequest {
  return buildRequest(CODING_METHODS.SESSION_SET_MODEL, { sessionId, modelId });
}

export function buildSetMode(sessionId: string, modeId: string): CodingRequest {
  return buildRequest(CODING_METHODS.SESSION_SET_MODE, { sessionId, modeId });
}

// ===== Message Classification =====

export function isResponse(msg: CodingMessage): msg is CodingResponse {
  return (
    "id" in msg && ("result" in msg || "error" in msg) && !("method" in msg)
  );
}

export function isRequest(msg: CodingMessage): msg is CodingRequest {
  return "id" in msg && "method" in msg;
}

export function isNotification(msg: CodingMessage): msg is CodingNotification {
  return !("id" in msg) && "method" in msg;
}

// ===== Session Update Parsing =====

export function isSessionUpdateNotification(msg: CodingMessage): boolean {
  return isNotification(msg) && msg.method === CODING_METHODS.SESSION_UPDATE;
}

export function extractSessionUpdate(
  msg: CodingNotification
): SessionUpdate | null {
  if (msg.method !== CODING_METHODS.SESSION_UPDATE || !msg.params) return null;
  return msg.params as unknown as SessionUpdate;
}

export function isPermissionRequest(msg: CodingMessage): boolean {
  return isRequest(msg) && msg.method === CODING_METHODS.REQUEST_PERMISSION;
}

export function extractPermissionRequest(
  msg: CodingRequest
): PermissionRequest | null {
  if (msg.method !== CODING_METHODS.REQUEST_PERMISSION || !msg.params) return null;
  return msg.params as unknown as PermissionRequest;
}

export function isFileReadRequest(msg: CodingMessage): boolean {
  return isRequest(msg) && msg.method === CODING_METHODS.READ_TEXT_FILE;
}

export function isFileWriteRequest(msg: CodingMessage): boolean {
  return isRequest(msg) && msg.method === CODING_METHODS.WRITE_TEXT_FILE;
}

// ===== Pending Request Tracker =====

type PendingResolver = {
  resolve: (result: unknown) => void;
  reject: (error: { code: number; message: string }) => void;
};

const pendingRequests = new Map<JsonRpcId, PendingResolver>();

export function trackRequest(id: JsonRpcId): Promise<unknown> {
  return new Promise((resolve, reject) => {
    pendingRequests.set(id, { resolve, reject });
  });
}

export function resolveResponse(response: CodingResponse): boolean {
  const pending = pendingRequests.get(response.id);
  if (!pending) return false;
  pendingRequests.delete(response.id);
  if (response.error) {
    pending.reject(response.error);
  } else {
    pending.resolve(response.result);
  }
  return true;
}

/**
 * 清除所有 pending 请求，reject 它们以避免 promise 泄漏。
 * 在 WebSocket 断开或 CLI provider 切换时调用。
 */
export function clearPendingRequests(): void {
  for (const [, pending] of pendingRequests) {
    pending.reject({ code: -1, message: "Connection reset" });
  }
  pendingRequests.clear();
}
