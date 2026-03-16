// 日志聚合相关类型定义

import type { JsonRpcId } from './coding-protocol';

/**
 * 原始消息方向
 */
export type RawMessageDirection = 'client_to_agent' | 'agent_to_client';

/**
 * 原始消息记录
 * 未经聚合处理的 JSON-RPC 协议消息，用于调试面板展示。
 */
export interface RawMessage {
  /** 唯一标识 */
  id: string;
  /** 消息方向 */
  direction: RawMessageDirection;
  /** 时间戳 */
  timestamp: number;
  /** 原始数据 */
  data: unknown;
  /** JSON-RPC method 字段 */
  method?: string;
  /** JSON-RPC id 字段，与 himarket 的 JsonRpcId 类型对齐 */
  rpcId?: JsonRpcId;
}

/**
 * 聚合后的日志条目
 * 流式消息（如 agent_message_chunk、agent_thought_chunk）会被拼接为一条聚合日志，
 * 非流式消息（如 initialize、tool_call 等）则作为独立日志条目。
 */
export interface AggregatedLogEntry {
  /** 唯一标识 */
  id: string;
  /** 消息方向 */
  direction: RawMessageDirection;
  /** 首条消息时间戳 */
  timestamp: number;
  /** 末条消息时间戳 */
  endTimestamp: number;
  /** JSON-RPC method 字段 */
  method?: string;
  /** JSON-RPC id 字段，与 himarket 的 JsonRpcId 类型对齐 */
  rpcId?: JsonRpcId;
  /** 摘要文本 */
  summary: string;
  /** 完整数据（非流式为原始数据，流式为拼接后的完整内容） */
  data: unknown;
  /** 聚合的原始消息数量 */
  messageCount: number;
  /** 是否为聚合消息 */
  isAggregated: boolean;
}

/**
 * 流式消息聚合缓冲区
 * 用于在接收流式 chunk 时暂存数据，待流结束后拼接输出。
 */
export interface ChunkBuffer {
  /** 流式消息类型 */
  type: 'agent_message' | 'agent_thought';
  /** 所属会话 ID */
  sessionId: string;
  /** 首条 chunk 的时间戳 */
  startTimestamp: number;
  /** 已接收的 chunk 列表 */
  chunks: Array<{ timestamp: number; data: unknown }>;
  /** 文本累加器，存储拼接后的文本内容 */
  textAccumulator: string;
}
