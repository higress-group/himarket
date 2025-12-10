/**
 * 聊天和会话相关接口
 */

import request, { type RespI } from "../request";

// ============ 类型定义 ============

export interface ISession {
  sessionId: string;
  name: string;
  products: string[];
  talkType: string;
  status: string;
  createAt: string;
  updateAt: string;
}

export interface IAttachment {
  type: "IMAGE" | "VIDEO";
  attachmentId: string;
}

export interface IChatMessage {
  productId: string;
  sessionId: string;
  conversationId: string;
  questionId: string;
  question: string;
  attachments?: IAttachment[];
  stream?: boolean;
  needMemory?: boolean;
  enableThinking?: boolean;
  searchType?: string;
}

export interface IAnswerResult {
  answerId: string;
  productId: string;
  content: string;
  usage?: {
    elapsed_time?: number;
    prompt_tokens?: number;
    completion_tokens?: number;
  };
}

export interface IAnswer {
  results: IAnswerResult[];
}

export interface IQuestion {
  questionId: string;
  content: string;
  attachments: IAttachment[];
  answers: IAnswer[];
}

export interface IConversation {
  conversationId: string;
  questions: IQuestion[];
}

// ============ MCP 工具调用相关类型 ============

export interface IToolMeta {
  toolName: string;           // 工具名称
  toolNameCn?: string | null; // 工具中文名称
  mcpName: string;            // MCP server 名称
  mcpNameCn?: string | null;  // MCP server 中文名称
}

export interface IToolCall {
  toolMeta: IToolMeta;
  inputSchema: string;        // 工具入参定义 (JSON string)
  input: string;              // 工具入参 (JSON string)
  id: string;                 // 工具调用唯一 ID
  type: string;               // 通常为 "function"
  name: string;               // 工具函数名
  arguments: string;          // 工具参数 (JSON string)
}

export interface IToolResponse {
  toolMeta: IToolMeta;
  output: string;             // 工具调用输出
  id: string;                 // 工具调用唯一 ID
  name: string;               // 工具函数名
  responseData: string;       // 响应数据
}

export interface IChatUsage {
  elapsed_time?: number | null;
  first_byte_timeout?: number | null;
  prompt_tokens?: number;
  completion_tokens?: number;
  total_tokens?: number;
  prompt_tokens_details?: {
    cached_tokens?: number;
  } | null;
}

// ============ V2 版本数据结构 ============

export interface IAnswerV2 {
  sequence: number;
  content: string;
  usage?: {
    elapsed_time?: number;
    first_byte_timeout?: number;
    prompt_tokens?: number;
    completion_tokens?: number;
    total_tokens?: number;
    prompt_tokens_details?: {
      cached_tokens?: number;
    };
  };
}

export interface IQuestionV2 {
  questionId: string;
  content: string;
  createdAt: string;
  attachments: IAttachment[];
  answers: IAnswerV2[];
}

export interface IConversationV2 {
  conversationId: string;
  questions: IQuestionV2[];
}

export interface IProductConversations {
  productId: string;
  conversations: IConversationV2[];
}

interface CreateSessionData {
  talkType: string;
  name: string;
  products: string[];
}

interface UpdateSessionData {
  name: string;
}

interface GetSessionsParams {
  page?: number;
  size?: number;
}

interface GetSessionsResp {
  content: ISession[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
  first: boolean;
  last: boolean;
}


interface SendChatMessageResp {
  // 根据实际响应结构定义
  [key: string]: any;
}

// ============ API 函数 ============

/**
 * 创建会话
 */
export function createSession(data: CreateSessionData) {
  return request.post<RespI<ISession>, RespI<ISession>>(
    '/sessions',
    data
  );
}

/**
 * 获取会话列表
 */
export function getSessions(params?: GetSessionsParams) {
  return request.get<RespI<GetSessionsResp>, RespI<GetSessionsResp>>(
    '/sessions',
    {
      params: {
        page: params?.page || 0,
        size: params?.size || 20,
      },
    }
  );
}

/**
 * 更新会话名称
 */
export function updateSession(sessionId: string, data: UpdateSessionData) {
  return request.patch<RespI<ISession>, RespI<ISession>>(
    `/sessions/${sessionId}`,
    data
  );
}

/**
 * 删除会话
 */
export function deleteSession(sessionId: string) {
  return request.delete<RespI<void>, RespI<void>>(
    `/sessions/${sessionId}`
  );
}

/**
 * 发送聊天消息（非流式）
 */
export function sendChatMessage(message: IChatMessage) {
  return request.post<RespI<SendChatMessageResp>, RespI<SendChatMessageResp>>(
    '/chats',
    message
  );
}

/**
 * 获取聊天消息流式接口的完整 URL（用于 SSE）
 */
export function getChatMessageStreamUrl(): string {
  const baseURL = (request.defaults.baseURL || '') as string;
  return `${baseURL}/chats`;
}

/**
 * 获取会话的历史聊天记录
 */
export function getConversations(sessionId: string) {
  return request.get<RespI<IConversation[]>, RespI<IConversation[]>>(
    `/sessions/${sessionId}/conversations`
  );
}

/**
 * 获取会话的历史聊天记录（V2版本 - 支持多模型对比）
 */
export function getConversationsV2(sessionId: string) {
  return request.get<RespI<IProductConversations[]>, RespI<IProductConversations[]>>(
    `/sessions/${sessionId}/conversations/v2`
  );
}
