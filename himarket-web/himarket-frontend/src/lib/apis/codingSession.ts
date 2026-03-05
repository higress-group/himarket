/**
 * CodingSession 会话持久化接口
 */

import request, { type RespI } from "../request";
import type { CodingConfig } from "../../types/coding";

// ============ 类型定义 ============

export interface CreateCodingSessionParam {
  title?: string;
  config?: CodingConfig;
}

export interface UpdateCodingSessionParam {
  title?: string;
  config?: CodingConfig;
  sessionData?: object;
}

export interface CodingSessionResult {
  sessionId: string;
  title: string | null;
  config: CodingConfig | null;
  sessionData: object | null;
  createdAt: string;
  updatedAt: string;
}

// ============ API 函数 ============

/**
 * 创建新的 CodingSession
 */
export function createCodingSession(data: CreateCodingSessionParam) {
  return request.post<RespI<CodingSessionResult>, RespI<CodingSessionResult>>(
    "/coding-sessions",
    data
  );
}

/**
 * 获取当前用户的会话列表（按 updated_at 倒序，不含 sessionData）
 */
export function listCodingSessions() {
  return request.get<RespI<CodingSessionResult[]>, RespI<CodingSessionResult[]>>(
    "/coding-sessions"
  );
}

/**
 * 获取单个会话详情（含完整 sessionData）
 */
export function getCodingSession(sessionId: string) {
  return request.get<RespI<CodingSessionResult>, RespI<CodingSessionResult>>(
    `/coding-sessions/${sessionId}`
  );
}

/**
 * 更新会话（标题、配置、数据）
 */
export function updateCodingSession(sessionId: string, data: UpdateCodingSessionParam) {
  return request.put<RespI<CodingSessionResult>, RespI<CodingSessionResult>>(
    `/coding-sessions/${sessionId}`,
    data
  );
}

/**
 * 删除会话
 */
export function deleteCodingSession(sessionId: string) {
  return request.delete<RespI<void>, RespI<void>>(
    `/coding-sessions/${sessionId}`
  );
}
