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
  supportsMcp?: boolean;
  supportsSkill?: boolean;
  authOptions?: string[];    // 认证方案列表，如 ["default", "personal_access_token"]
  authEnvVar?: string;       // Token/API Key 对应的环境变量名
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


// ============ MCP 市场类型定义 ============

export interface MarketMcpInfo {
  productId: string;
  name: string;
  url: string;
  transportType: string;
  description: string;
}

export interface MarketMcpsResponse {
  mcpServers: MarketMcpInfo[];
  authHeaders: Record<string, string> | null;
}

// ============ Skill 市场类型定义 ============

export interface MarketSkillInfo {
  productId: string;
  name: string;
  description: string;
  skillTags: string[];
}

// ============ CliSessionConfig 类型定义 ============

export interface McpServerEntry {
  name: string;
  url: string;
  transportType: string;
  headers?: Record<string, string>;
}

export interface SkillEntry {
  name: string;
  skillMdContent: string;
}

export interface CliSessionConfig {
  customModelConfig?: import("../../components/hicli/CustomModelForm").CustomModelFormData;
  mcpServers?: McpServerEntry[];
  skills?: SkillEntry[];
  authToken?: string;  // 认证凭据（PAT / API Key）
}

// ============ MCP 市场 API 函数 ============

/**
 * 获取当前开发者已订阅的 MCP Server 列表
 */
export function getMarketMcps() {
  return request.get<RespI<MarketMcpsResponse>, RespI<MarketMcpsResponse>>(
    "/cli-providers/market-mcps"
  );
}

// ============ Skill 市场 API 函数 ============

/**
 * 获取已发布的 Skill 列表
 */
export function getMarketSkills() {
  return request.get<RespI<MarketSkillInfo[]>, RespI<MarketSkillInfo[]>>(
    "/cli-providers/market-skills"
  );
}

/**
 * 下载指定 Skill 的 SKILL.md 内容
 */
export function downloadSkill(productId: string) {
  return request.get<string, string>(
    `/skills/${productId}/download`
  );
}
