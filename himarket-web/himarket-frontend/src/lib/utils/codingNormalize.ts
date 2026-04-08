import type {
  JsonRpcId,
  ToolKind,
  ToolCallContentItem,
  ToolCallLocationItem,
} from '../../types/coding-protocol';

type ToolCallTextContent = Extract<ToolCallContentItem, { type: 'content' }>['content'];

const TOOL_KINDS: ReadonlySet<ToolKind> = new Set([
  'read',
  'edit',
  'delete',
  'move',
  'search',
  'execute',
  'think',
  'fetch',
  'switch_mode',
  'other',
  'skill',
]);

function hasOwn(obj: object, key: string): boolean {
  return Object.prototype.hasOwnProperty.call(obj, key);
}

function asObject(value: unknown): Record<string, unknown> | null {
  if (typeof value !== 'object' || value === null) return null;
  return value as Record<string, unknown>;
}

function normalizeJsonRpcId(id: unknown): JsonRpcId | undefined {
  if (typeof id === 'number' || typeof id === 'string') return id;
  return undefined;
}

export function normalizeToolKind(kind: unknown, title?: unknown): ToolKind {
  if (typeof kind === 'string') {
    if (TOOL_KINDS.has(kind as ToolKind)) return kind as ToolKind;
    if (/^skill\s+/i.test(kind)) return 'skill';
  }
  if (typeof title === 'string' && /^skill\s+/i.test(title)) {
    return 'skill';
  }
  return 'other';
}

function normalizeLocations(locations: unknown): ToolCallLocationItem[] | undefined {
  if (!Array.isArray(locations)) return undefined;
  const result: ToolCallLocationItem[] = [];
  for (const loc of locations) {
    const obj = asObject(loc);
    if (!obj) continue;
    if (typeof obj.path === 'string' && obj.path) {
      result.push({ path: obj.path });
    }
  }
  return result.length > 0 ? result : undefined;
}

function normalizeContentItem(item: unknown): ToolCallContentItem | null {
  const obj = asObject(item);
  if (!obj || typeof obj.type !== 'string') return null;

  if (obj.type === 'terminal') {
    if (typeof obj.terminalId !== 'string' || !obj.terminalId) return null;
    return { terminalId: obj.terminalId, type: 'terminal' };
  }

  if (obj.type === 'diff') {
    const normalized: ToolCallContentItem = { type: 'diff' };
    if (typeof obj.path === 'string') {
      normalized.path = obj.path;
    }
    if (hasOwn(obj, 'oldText')) {
      if (obj.oldText === null || typeof obj.oldText === 'string') {
        normalized.oldText = obj.oldText;
      }
    }
    if (hasOwn(obj, 'newText')) {
      if (obj.newText === null || typeof obj.newText === 'string') {
        normalized.newText = obj.newText;
      }
    }
    return normalized;
  }

  if (obj.type === 'content') {
    const normalized: ToolCallContentItem = { type: 'content' };
    const contentObj = asObject(obj.content);
    if (contentObj) {
      if (contentObj.type === 'text' && typeof contentObj.text !== 'string') {
        normalized.content = { ...contentObj, text: '' } as ToolCallTextContent;
      } else {
        normalized.content = contentObj as unknown as ToolCallTextContent;
      }
    }
    return normalized;
  }

  return null;
}

function normalizeContent(content: unknown): ToolCallContentItem[] | undefined {
  if (!Array.isArray(content)) return undefined;
  const result: ToolCallContentItem[] = [];
  for (const item of content) {
    const normalized = normalizeContentItem(item);
    if (normalized) result.push(normalized);
  }
  return result.length > 0 ? result : undefined;
}

function normalizeToolCallUpdate(update: Record<string, unknown>): void {
  update.kind = normalizeToolKind(update.kind, update.title);

  const content = normalizeContent(update.content);
  if (content) {
    update.content = content;
  } else if (hasOwn(update, 'content')) {
    update.content = [];
  }

  const locations = normalizeLocations(update.locations);
  if (locations) {
    update.locations = locations;
  } else if (hasOwn(update, 'locations')) {
    update.locations = [];
  }
}

function normalizeSessionUpdateParams(params: Record<string, unknown>): void {
  const updateEnvelope = asObject(params.update);
  if (!updateEnvelope || typeof updateEnvelope.sessionUpdate !== 'string') return;

  if (
    updateEnvelope.sessionUpdate === 'tool_call' ||
    updateEnvelope.sessionUpdate === 'tool_call_update'
  ) {
    normalizeToolCallUpdate(updateEnvelope);
  }
}

function normalizePermissionParams(params: Record<string, unknown>): void {
  const toolCall = asObject(params.toolCall);
  if (!toolCall) return;
  toolCall.kind = normalizeToolKind(toolCall.kind, toolCall.title);

  const content = normalizeContent(toolCall.content);
  if (content) {
    toolCall.content = content;
  }

  const locations = normalizeLocations(toolCall.locations);
  if (locations) {
    toolCall.locations = locations;
  }
}

export function normalizeIncomingMessage(
  message: Record<string, unknown>,
): Record<string, unknown> {
  const normalized: Record<string, unknown> = { ...message };
  const normalizedId = normalizeJsonRpcId(normalized.id);
  if (normalizedId !== undefined) {
    normalized.id = normalizedId;
  }

  if (typeof normalized.method !== 'string') return normalized;
  const params = asObject(normalized.params);
  if (!params) return normalized;
  const normalizedParams: Record<string, unknown> = { ...params };

  if (normalized.method === 'session/update') {
    normalizeSessionUpdateParams(normalizedParams);
  } else if (normalized.method === 'session/request_permission') {
    normalizePermissionParams(normalizedParams);
  }

  normalized.params = normalizedParams;
  return normalized;
}
