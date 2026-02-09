import {
  JSONRPC_VERSION,
  ACP_METHODS,
  type AcpRequest,
  type AcpResponse,
  type AcpNotification,
  type AcpMessage,
  type SessionUpdate,
  type PermissionRequest,
  type ContentBlock,
} from "../../types/acp";

// ===== Request ID Management =====

let _nextId = 1;

export function nextId(): number {
  return _nextId++;
}

// ===== Request Builders =====

export function buildRequest(
  method: string,
  params?: Record<string, unknown>
): AcpRequest {
  return { jsonrpc: JSONRPC_VERSION, id: nextId(), method, params };
}

export function buildNotification(
  method: string,
  params?: Record<string, unknown>
): AcpNotification {
  return { jsonrpc: JSONRPC_VERSION, method, params };
}

export function buildResponse(id: number, result: unknown): AcpResponse {
  return { jsonrpc: JSONRPC_VERSION, id, result };
}

// ===== Shortcut Builders =====

export function buildInitialize(): AcpRequest {
  return buildRequest(ACP_METHODS.INITIALIZE, {
    protocolVersion: 1,
    clientCapabilities: {
      fs: { readTextFile: true, writeTextFile: true },
    },
  });
}

export function buildSessionNew(cwd: string): AcpRequest {
  return buildRequest(ACP_METHODS.SESSION_NEW, { cwd, mcpServers: [] });
}

export function buildPrompt(sessionId: string, text: string): AcpRequest {
  const prompt: ContentBlock[] = [{ type: "text", text }];
  return buildRequest(ACP_METHODS.SESSION_PROMPT, { sessionId, prompt });
}

export function buildCancel(sessionId: string): AcpNotification {
  return buildNotification(ACP_METHODS.SESSION_CANCEL, { sessionId });
}

export function buildSetModel(sessionId: string, modelId: string): AcpRequest {
  return buildRequest(ACP_METHODS.SESSION_SET_MODEL, { sessionId, modelId });
}

export function buildSetMode(sessionId: string, modeId: string): AcpRequest {
  return buildRequest(ACP_METHODS.SESSION_SET_MODE, { sessionId, modeId });
}

// ===== Message Classification =====

export function isResponse(msg: AcpMessage): msg is AcpResponse {
  return (
    "id" in msg && ("result" in msg || "error" in msg) && !("method" in msg)
  );
}

export function isRequest(msg: AcpMessage): msg is AcpRequest {
  return "id" in msg && "method" in msg;
}

export function isNotification(msg: AcpMessage): msg is AcpNotification {
  return !("id" in msg) && "method" in msg;
}

// ===== Session Update Parsing =====

export function isSessionUpdateNotification(msg: AcpMessage): boolean {
  return isNotification(msg) && msg.method === ACP_METHODS.SESSION_UPDATE;
}

export function extractSessionUpdate(
  msg: AcpNotification
): SessionUpdate | null {
  if (msg.method !== ACP_METHODS.SESSION_UPDATE || !msg.params) return null;
  return msg.params as unknown as SessionUpdate;
}

export function isPermissionRequest(msg: AcpMessage): boolean {
  return isRequest(msg) && msg.method === ACP_METHODS.REQUEST_PERMISSION;
}

export function extractPermissionRequest(
  msg: AcpRequest
): PermissionRequest | null {
  if (msg.method !== ACP_METHODS.REQUEST_PERMISSION || !msg.params) return null;
  return msg.params as unknown as PermissionRequest;
}

export function isFileReadRequest(msg: AcpMessage): boolean {
  return isRequest(msg) && msg.method === ACP_METHODS.READ_TEXT_FILE;
}

export function isFileWriteRequest(msg: AcpMessage): boolean {
  return isRequest(msg) && msg.method === ACP_METHODS.WRITE_TEXT_FILE;
}

// ===== Pending Request Tracker =====

type PendingResolver = {
  resolve: (result: unknown) => void;
  reject: (error: { code: number; message: string }) => void;
};

const pendingRequests = new Map<number, PendingResolver>();

export function trackRequest(id: number): Promise<unknown> {
  return new Promise((resolve, reject) => {
    pendingRequests.set(id, { resolve, reject });
  });
}

export function resolveResponse(response: AcpResponse): boolean {
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
