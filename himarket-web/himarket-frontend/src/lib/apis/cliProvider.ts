/**
 * CLI Provider 相关接口
 */

import request, { type RespI } from '../request';

// ============ 类型定义 ============

export interface ICliProvider {
  key: string;
  displayName: string;
  isDefault: boolean;
  available: boolean;
  compatibleRuntimes?: string[];
  runtimeCategory?: 'native' | 'nodejs' | 'python';
  containerImage?: string;
  supportsCustomModel?: boolean;
  supportsMcp?: boolean;
  supportsSkill?: boolean;
  authOptions?: string[]; // 认证方案列表，如 ["default", "personal_access_token"]
  authEnvVar?: string; // Token/API Key 对应的环境变量名
}

// ============ API 函数 ============

/**
 * 获取可用的 CLI Provider 列表
 */
export function getCliProviders() {
  return request.get<RespI<ICliProvider[]>, RespI<ICliProvider[]>>('/cli-providers');
}

// ============ 功能开关类型定义 ============

export interface CodingFeatures {
  terminalEnabled: boolean;
}

// ============ 功能开关 API 函数 ============

/**
 * 获取 HiCoding 功能开关状态
 */
export function getCodingFeatures() {
  return request.get<RespI<CodingFeatures>, RespI<CodingFeatures>>('/cli-providers/features');
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
}

// ============ 模型市场 API 函数 ============

/**
 * 获取当前开发者已订阅的模型市场模型列表
 */
export function getMarketModels() {
  return request.get<RespI<MarketModelsResponse>, RespI<MarketModelsResponse>>(
    '/cli-providers/market-models',
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
  productId: string;
  name: string;
}

export interface SkillEntry {
  productId: string;
  name: string;
}

export interface CliSessionConfig {
  modelProductId?: string;
  mcpServers?: McpServerEntry[];
  skills?: SkillEntry[];
  authToken?: string; // 认证凭据（PAT / API Key）
}

// ============ MCP 市场 API 函数 ============

/**
 * 获取当前开发者已订阅的 MCP Server 列表
 */
export function getMarketMcps() {
  return request.get<RespI<MarketMcpsResponse>, RespI<MarketMcpsResponse>>(
    '/cli-providers/market-mcps',
  );
}

// ============ Skill 市场 API 函数 ============

/**
 * 获取已发布的 Skill 列表
 */
export function getMarketSkills() {
  return request.get<RespI<MarketSkillInfo[]>, RespI<MarketSkillInfo[]>>(
    '/cli-providers/market-skills',
  );
}

// ============ Skill 文件树类型定义 ============

export interface SkillFileTreeNode {
  name: string;
  path: string;
  type: 'file' | 'directory';
  encoding?: string;
  size?: number;
  children?: SkillFileTreeNode[];
}

export interface SkillFileContent {
  path: string;
  content: string;
  encoding: string;
  size: number;
}

export interface SkillVersion {
  version: string;
  status: string;
  updateTime?: number;
  downloadCount?: number;
  isLatest?: boolean;
}

// ============ Skill 文件 API 函数 ============

/**
 * 获取 Skill 文件树（支持指定版本）
 */
export function getSkillFiles(productId: string, version?: string) {
  return request.get<RespI<SkillFileTreeNode[]>, RespI<SkillFileTreeNode[]>>(
    `/skills/${productId}/files`,
    { params: version ? { version } : {} },
  );
}

/**
 * 获取单个文件内容（支持指定版本）
 */
export function getSkillFileContent(productId: string, filePath: string, version?: string) {
  return request.get<RespI<SkillFileContent>, RespI<SkillFileContent>>(
    `/skills/${productId}/files/${filePath}`,
    { params: version ? { version } : {} },
  );
}

/**
 * Skill CLI download info
 */
export interface SkillCliInfo {
  nacosHost: string;
  resourceName: string;
  resourceType: string;
}

/**
 * 获取 Skill CLI 下载信息
 */
export function getSkillCliInfo(productId: string) {
  return request.get<RespI<SkillCliInfo>, RespI<SkillCliInfo>>(`/skills/${productId}/cli-info`);
}

/**
 * 获取 Skill 包下载 URL
 */
export function getSkillPackageUrl(productId: string, version?: string): string {
  const base = `/api/v1/skills/${productId}/download`;
  return version ? `${base}?version=${encodeURIComponent(version)}` : base;
}

/**
 * 获取 Skill 版本列表
 */
export function getSkillVersions(productId: string) {
  return request.get<RespI<SkillVersion[]>, RespI<SkillVersion[]>>(`/skills/${productId}/versions`);
}

// ============ Nacos 辅助 API ============

export interface NacosInfo {
  nacosId: string;
  name: string;
  defaultNamespace?: string;
}

export function getDefaultNacos() {
  return request.get<RespI<NacosInfo>, RespI<NacosInfo>>('/nacos/default');
}
