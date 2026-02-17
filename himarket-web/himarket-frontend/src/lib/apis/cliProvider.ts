/**
 * CLI Provider 相关接口
 */

import request, { type RespI } from "../request";
import type { RuntimeType } from "../../types/runtime";

// ============ 类型定义 ============

export interface ICliProvider {
  key: string;
  displayName: string;
  isDefault: boolean;
  available: boolean;
  compatibleRuntimes?: RuntimeType[];
  runtimeCategory?: 'native' | 'nodejs' | 'python';
  containerImage?: string;
}

// ============ API 函数 ============

/**
 * 获取可用的 CLI Provider 列表
 */
export function getCliProviders() {
  return request.get<RespI<ICliProvider[]>, RespI<ICliProvider[]>>(
    "/cli-providers"
  );
}
