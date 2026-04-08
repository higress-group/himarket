import request from '../request';

import type { FileNode } from '../../types/coding';

interface FileContentResponse {
  content: string;
  encoding: 'utf-8' | 'base64';
}

export const ARTIFACT_SCAN_FALLBACK_ENABLED =
  import.meta.env.VITE_ARTIFACT_SCAN_FALLBACK !== 'false';

export const PPT_PREVIEW_PREPARE_ENABLED = import.meta.env.VITE_PPT_PREPARE_PREVIEW !== 'false';

/**
 * 全局默认 runtime，由 HiCoding 页面初始化时设置。
 * 当 workspace API 函数未显式传入 runtime 时，自动使用此值。
 */
let _defaultRuntime: string | undefined;

export function setDefaultRuntime(runtime: string | undefined) {
  _defaultRuntime = runtime;
}

export function getDefaultRuntime(): string | undefined {
  return _defaultRuntime;
}

/**
 * 通过后端 /workspace/download 接口下载文件（二进制流），自动触发浏览器下载。
 */
export async function downloadWorkspaceFile(
  filePath: string,
  fileName: string,
  runtime?: string,
): Promise<void> {
  const rt = runtime ?? _defaultRuntime;
  const params: Record<string, string> = { path: filePath };
  if (rt) params.runtime = rt;
  const resp = await request.get('/workspace/download', {
    params,
    responseType: 'blob',
    timeout: 60000,
  });
  const blob = resp instanceof Blob ? resp : new Blob([resp as unknown as BlobPart]);
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = fileName;
  a.click();
  URL.revokeObjectURL(url);
}

export interface WorkspaceApiError {
  code: string;
  message: string;
  status?: number;
}

export interface ArtifactContentResult {
  content: string | null;
  encoding: 'utf-8' | 'base64' | null;
  error: WorkspaceApiError | null;
}

export interface WorkspaceChange {
  path: string;
  mtimeMs: number;
  size: number;
  ext: string;
}

export interface PreparePreviewResponse {
  status: 'ready' | 'converting' | 'failed' | 'unsupported';
  previewPath?: string;
  reason?: string;
}

interface WorkspaceChangesResponse {
  changes: WorkspaceChange[];
}

function parseWorkspaceError(error: unknown): WorkspaceApiError {
  if (typeof error === 'object' && error !== null) {
    const errObj = error as {
      response?: { status?: number; data?: { error?: string; code?: string } };
      message?: string;
    };
    const status = errObj.response?.status;
    const code = errObj.response?.data?.code ?? 'WORKSPACE_API_ERROR';
    const message =
      status === 413
        ? '文件过大，无法预览'
        : (errObj.response?.data?.error ?? errObj.message ?? 'Workspace API request failed');
    return { code, message, status };
  }
  return {
    code: 'WORKSPACE_API_ERROR',
    message: 'Workspace API request failed',
  };
}

/**
 * Upload a file to the backend workspace and return the server-side absolute path.
 */
export async function uploadFileToWorkspace(file: File): Promise<string> {
  const formData = new FormData();
  formData.append('file', file);
  const resp: { filePath: string } = await request.post('/workspace/upload', formData, {
    headers: {
      'Content-Type': 'multipart/form-data',
    },
    timeout: 60000,
  });
  return resp.filePath;
}

/**
 * Fetch artifact content from workspace.
 * - default: preview mode (e.g. PPT/PPTX will return converted PDF content)
 * - raw=true: returns original file content without conversion
 */
export async function fetchArtifactContent(
  filePath: string,
  opts?: { raw?: boolean; runtime?: string },
): Promise<ArtifactContentResult> {
  try {
    const runtime = opts?.runtime ?? _defaultRuntime;
    const resp: FileContentResponse = await request.get('/workspace/file', {
      params: {
        path: filePath,
        raw: opts?.raw === true,
        ...(runtime ? { runtime } : {}),
      },
    });
    return {
      content: resp.content ?? null,
      encoding: resp.encoding ?? null,
      error: null,
    };
  } catch (error) {
    return {
      content: null,
      encoding: null,
      error: parseWorkspaceError(error),
    };
  }
}

/**
 * Prepare preview conversion in background (currently for PPT/PPTX -> PDF).
 */
export async function prepareArtifactPreview(filePath: string): Promise<PreparePreviewResponse> {
  try {
    const resp: PreparePreviewResponse = await request.post('/workspace/preview/prepare', {
      path: filePath,
    });
    return resp;
  } catch (error) {
    const e = parseWorkspaceError(error);
    return { reason: e.message, status: 'failed' };
  }
}

/**
 * List changed files after given timestamp under cwd.
 */
export async function fetchWorkspaceChanges(
  cwd: string,
  since: number,
  limit = 200,
  runtime?: string,
): Promise<WorkspaceChange[]> {
  try {
    const effectiveRuntime = runtime ?? _defaultRuntime;
    const resp: WorkspaceChangesResponse = await request.get('/workspace/changes', {
      params: { cwd, limit, since, ...(effectiveRuntime ? { runtime: effectiveRuntime } : {}) },
    });
    return resp.changes ?? [];
  } catch {
    return [];
  }
}

/**
 * Fetch directory tree from workspace.
 * Backend returns a root node { name, path, type, children }.
 * We extract children as the top-level tree.
 */
export async function fetchDirectoryTree(
  cwd: string,
  depth = 10,
  runtime?: string,
): Promise<FileNode[] | null> {
  try {
    const effectiveRuntime = runtime ?? _defaultRuntime;
    const resp: FileNode = await request.get('/workspace/tree', {
      params: { cwd, depth, ...(effectiveRuntime ? { runtime: effectiveRuntime } : {}) },
    });
    return resp.children ?? [];
  } catch {
    return null;
  }
}
