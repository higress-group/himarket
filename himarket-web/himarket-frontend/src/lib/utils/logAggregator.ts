// 日志聚合器：将流式 chunk 消息拼接为完整日志条目，非流式消息立即输出

import type { RawMessageDirection } from '../../types/log';
import type { AggregatedLogEntry, ChunkBuffer } from '../../types/log';
import {
  isNotification,
  isSessionUpdateNotification,
  extractSessionUpdate,
} from './codingProtocol';
import type { CodingMessage, CodingNotification } from '../../types/coding-protocol';

/** 日志条目 ID 计数器 */
let entryCounter = 0;

/** 生成唯一日志条目 ID */
function generateEntryId(): string {
  return `log-${++entryCounter}`;
}

/**
 * 重置 ID 计数器（仅用于测试）
 */
export function _resetEntryCounter(): void {
  entryCounter = 0;
}

/**
 * 从协议消息中提取 method 字段
 */
function extractMethod(data: unknown): string | undefined {
  if (typeof data === 'object' && data !== null && 'method' in data) {
    return (data as { method: string }).method;
  }
  return undefined;
}

/**
 * 从 ACP 消息中提取 JSON-RPC id 字段
 */
function extractRpcId(data: unknown): number | undefined {
  if (typeof data === 'object' && data !== null && 'id' in data) {
    const id = (data as { id: unknown }).id;
    if (typeof id === 'number') return id;
  }
  return undefined;
}

/**
 * 生成消息摘要文本
 */
function generateSummary(data: unknown, method?: string): string {
  if (method) return method;
  if (typeof data === 'object' && data !== null) {
    if ('result' in data) return 'response';
    if ('error' in data) return 'error response';
  }
  return 'unknown';
}

/**
 * 判断 session update 是否为流式 chunk 类型
 * 返回 chunk 类型和文本内容，如果不是流式 chunk 则返回 null
 */
function extractChunkInfo(
  data: unknown
): { type: 'agent_message' | 'agent_thought'; sessionId: string; text: string } | null {
  // data 必须是 session/update 通知
  const msg = data as CodingMessage;
  if (!isNotification(msg) || !isSessionUpdateNotification(msg)) return null;

  const update = extractSessionUpdate(msg as CodingNotification);
  if (!update) return null;

  const sessionUpdate = update.update.sessionUpdate;

  if (sessionUpdate === 'agent_message_chunk') {
    const content = (update as { update: { content?: { type: string; text?: string } } }).update
      .content;
    const text = content?.type === 'text' && content.text ? content.text : '';
    return { type: 'agent_message', sessionId: update.sessionId, text };
  }

  if (sessionUpdate === 'agent_thought_chunk') {
    const content = (update as { update: { content?: { text?: string } } }).update.content;
    const text = content?.text ?? '';
    return { type: 'agent_thought', sessionId: update.sessionId, text };
  }

  return null;
}

/**
 * LogAggregator - 日志聚合器
 *
 * 负责将 ACP 协议的原始消息聚合为用户友好的日志条目：
 * - 流式消息（agent_message_chunk、agent_thought_chunk）：缓冲后拼接为一条聚合日志
 * - 非流式消息：立即输出为独立日志条目
 *
 * 使用方式：
 * ```ts
 * const aggregator = new LogAggregator();
 * aggregator.onEntry = (entry) => { console.log(entry); };
 * aggregator.processMessage('agent_to_client', messageData);
 * ```
 */
export class LogAggregator {
  /** 日志条目回调，每当产生一条聚合日志时触发 */
  onEntry: ((entry: AggregatedLogEntry) => void) | null = null;

  /** chunk 缓冲区，按 "type:sessionId" 为 key 存储 */
  private buffers: Map<string, ChunkBuffer> = new Map();

  /**
   * 处理一条协议消息
   * @param direction 消息方向
   * @param data 原始消息数据
   */
  processMessage(direction: RawMessageDirection, data: unknown): void {
    const now = Date.now();
    const chunkInfo = extractChunkInfo(data);

    if (chunkInfo) {
      // 流式 chunk 消息：进入缓冲区
      this.bufferChunk(direction, data, chunkInfo, now);
    } else {
      // 非流式消息：先 flush 所有缓冲区，再立即输出
      this.flushAllBuffers(direction);
      this.emitImmediate(direction, data, now);
    }
  }

  /**
   * 强制 flush 所有待处理的缓冲区
   */
  flush(): void {
    for (const [key, buffer] of this.buffers) {
      this.emitAggregated(buffer);
      this.buffers.delete(key);
    }
  }

  /**
   * 获取当前缓冲区数量（用于测试）
   */
  get pendingBufferCount(): number {
    return this.buffers.size;
  }

  /**
   * 将 chunk 加入缓冲区
   */
  private bufferChunk(
    _direction: RawMessageDirection,
    data: unknown,
    chunkInfo: { type: 'agent_message' | 'agent_thought'; sessionId: string; text: string },
    timestamp: number
  ): void {
    const bufferKey = `${chunkInfo.type}:${chunkInfo.sessionId}`;

    let buffer = this.buffers.get(bufferKey);
    if (!buffer) {
      buffer = {
        type: chunkInfo.type,
        sessionId: chunkInfo.sessionId,
        startTimestamp: timestamp,
        chunks: [],
        textAccumulator: '',
      };
      this.buffers.set(bufferKey, buffer);
    }

    buffer.chunks.push({ timestamp, data });
    buffer.textAccumulator += chunkInfo.text;
  }

  /**
   * flush 所有缓冲区并输出聚合日志
   */
  private flushAllBuffers(_direction: RawMessageDirection): void {
    for (const [key, buffer] of this.buffers) {
      this.emitAggregated(buffer);
      this.buffers.delete(key);
    }
  }

  /**
   * 输出一条聚合日志条目（由多个 chunk 拼接而成）
   */
  private emitAggregated(buffer: ChunkBuffer): void {
    if (buffer.chunks.length === 0) return;

    const lastChunk = buffer.chunks[buffer.chunks.length - 1];
    const method =
      buffer.type === 'agent_message'
        ? 'session/update [agent_message_chunk]'
        : 'session/update [agent_thought_chunk]';

    const entry: AggregatedLogEntry = {
      id: generateEntryId(),
      direction: 'agent_to_client',
      timestamp: buffer.startTimestamp,
      endTimestamp: lastChunk.timestamp,
      method,
      summary: buffer.textAccumulator.slice(0, 100) + (buffer.textAccumulator.length > 100 ? '...' : ''),
      data: {
        type: buffer.type,
        sessionId: buffer.sessionId,
        fullText: buffer.textAccumulator,
        chunkCount: buffer.chunks.length,
      },
      messageCount: buffer.chunks.length,
      isAggregated: true,
    };

    this.onEntry?.(entry);
  }

  /**
   * 立即输出一条非流式日志条目
   */
  private emitImmediate(direction: RawMessageDirection, data: unknown, timestamp: number): void {
    const method = extractMethod(data);
    const rpcId = extractRpcId(data);

    const entry: AggregatedLogEntry = {
      id: generateEntryId(),
      direction,
      timestamp,
      endTimestamp: timestamp,
      method,
      rpcId,
      summary: generateSummary(data, method),
      data,
      messageCount: 1,
      isAggregated: false,
    };

    this.onEntry?.(entry);
  }
}
