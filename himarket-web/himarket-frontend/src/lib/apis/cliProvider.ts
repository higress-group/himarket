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
  supportsCustomModel?: boolean;
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


// ============ 模型市场类型定义 ============

export interface MarketModelInfo {
  productId: string;
  name: string;
  modelId: string;
  baseUrl: string;
  protocolType: string;
  description: string;
}

export interface MarketModelsResponse {
  models: MarketModelInfo[];
  apiKey: string | null;
}

// ============ 模型市场 API 函数 ============

/**
 * 获取当前开发者已订阅的模型市场模型列表
 */
export function getMarketModels() {
  return request.get<RespI<MarketModelsResponse>, RespI<MarketModelsResponse>>(
    "/cli-providers/market-models"
  );
}
