/**
 * HiCoding 会话持久化相关接口
 */

import request, { type RespI } from "../request";

// ============ 类型定义 ============

export interface ICodingSession {
  id: number;
  sessionId: string;
  cliSessionId: string;
  title: string;
  providerKey: string;
  modelProductId?: string;
  modelName?: string;
  cwd: string;
  createdAt: string;
  updatedAt: string;
}

interface CreateCodingSessionData {
  cliSessionId: string;
  title?: string;
  providerKey: string;
  modelProductId?: string;
  modelName?: string;
  cwd: string;
}

interface UpdateCodingSessionData {
  title?: string;
  cliSessionId?: string;
}

interface GetCodingSessionsResp {
  content: ICodingSession[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
  first: boolean;
  last: boolean;
}

// ============ API 函数 ============

/**
 * 创建 Coding 会话
 */
export function createCodingSession(data: CreateCodingSessionData) {
  return request.post<RespI<ICodingSession>, RespI<ICodingSession>>(
    "/coding-sessions",
    data
  );
}

/**
 * 获取 Coding 会话列表
 */
export function getCodingSessions(params?: { page?: number; size?: number }) {
  return request.get<
    RespI<GetCodingSessionsResp>,
    RespI<GetCodingSessionsResp>
  >("/coding-sessions", {
    params: {
      page: params?.page ?? 0,
      size: params?.size ?? 20,
    },
  });
}

/**
 * 更新 Coding 会话
 */
export function updateCodingSession(
  sessionId: string,
  data: UpdateCodingSessionData
) {
  return request.patch<RespI<ICodingSession>, RespI<ICodingSession>>(
    `/coding-sessions/${sessionId}`,
    data
  );
}

/**
 * 删除 Coding 会话
 */
export function deleteCodingSession(sessionId: string) {
  return request.delete<RespI<void>, RespI<void>>(
    `/coding-sessions/${sessionId}`
  );
}
