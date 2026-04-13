/**
 * MCP 协议类型常量。
 * 与后端 McpProtocolType 枚举保持一致。
 *
 * 标准值（数据库存储 & API 传输）：
 *   stdio / sse / streamableHttp
 *
 * 前端表单 value 使用简写：
 *   stdio / sse / http
 */
export const McpProtocol = {
  STDIO: 'stdio',
  SSE: 'sse',
  STREAMABLE_HTTP: 'streamableHttp',
  /** 前端表单简写，对应后端 streamableHttp */
  HTTP: 'http',
} as const;

export type McpProtocolValue = (typeof McpProtocol)[keyof typeof McpProtocol];

/** 判断协议是否为 StreamableHTTP（兼容各种写法） */
export function isStreamableHttp(protocol?: string): boolean {
  if (!protocol) return false;
  const lower = protocol.toLowerCase();
  return lower.includes('http');
}

/** 判断协议是否为 SSE */
export function isSse(protocol?: string): boolean {
  if (!protocol) return false;
  return protocol.toLowerCase() === 'sse';
}

/** 判断协议是否为 Stdio */
export function isStdio(protocol?: string): boolean {
  if (!protocol) return false;
  return protocol.toLowerCase() === 'stdio';
}
