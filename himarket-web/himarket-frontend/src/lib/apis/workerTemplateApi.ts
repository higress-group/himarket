import request, { type RespI } from '../request';

export interface WorkerFileTreeNode {
  name: string;
  path: string;
  type: 'file' | 'directory';
  encoding?: string;
  size?: number;
  children?: WorkerFileTreeNode[];
}

export interface WorkerFileContent {
  path: string;
  content: string;
  encoding: string;
  size: number;
}

export interface WorkerVersion {
  version: string;
  status: string;
  updateTime?: number;
  downloadCount?: number;
  isLatest?: boolean;
}

// ============ API 函数 ============

/**
 * 获取 Worker 文件树（支持指定版本）
 */
export function getWorkerFileTree(productId: string, version?: string) {
  return request.get<RespI<WorkerFileTreeNode[]>, RespI<WorkerFileTreeNode[]>>(
    `/workers/${productId}/files`,
    { params: version ? { version } : {} },
  );
}

/**
 * 获取 Worker 单个文件内容（支持指定版本）
 */
export function getWorkerFileContent(productId: string, filePath: string, version?: string) {
  return request.get<RespI<WorkerFileContent>, RespI<WorkerFileContent>>(
    `/workers/${productId}/files/${filePath}`,
    { params: version ? { version } : {} },
  );
}

/**
 * 获取 Worker 版本列表
 */
export function getWorkerVersions(productId: string) {
  return request.get<RespI<WorkerVersion[]>, RespI<WorkerVersion[]>>(
    `/workers/${productId}/versions`,
  );
}

/**
 * 获取 Worker CLI 下载信息
 */
export interface WorkerCliInfo {
  nacosHost: string;
  nacosPort?: number;
  namespace: string;
  resourceName: string;
  resourceType: string;
}

export function getWorkerCliInfo(productId: string) {
  return request.get<RespI<WorkerCliInfo>, RespI<WorkerCliInfo>>(`/workers/${productId}/cli-info`);
}

/**
 * 获取 Worker 包下载 URL
 */
export function getWorkerPackageUrl(productId: string, version?: string): string {
  const base = `/api/v1/workers/${productId}/download`;
  return version ? `${base}?version=${encodeURIComponent(version)}` : base;
}
