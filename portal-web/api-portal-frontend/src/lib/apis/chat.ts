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
  return request.put<RespI<ISession>, RespI<ISession>>(
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
