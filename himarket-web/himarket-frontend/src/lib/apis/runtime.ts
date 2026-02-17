/**
 * 运行时相关接口
 */

import request, { type RespI } from "../request";
import type { RuntimeType } from "../../types/runtime";

// ============ 类型定义 ============

export interface IRuntimeAvailability {
  type: RuntimeType;
  available: boolean;
  unavailableReason?: string;
}

// ============ API 函数 ============

/**
 * 获取当前环境可用的运行时列表
 */
export function getAvailableRuntimes() {
  return request.get<RespI<IRuntimeAvailability[]>, RespI<IRuntimeAvailability[]>>(
    "/runtime/available"
  );
}
