import request from "../request";

interface FileContentResponse {
  content: string;
  encoding: "utf-8" | "base64";
}

export const ARTIFACT_SCAN_FALLBACK_ENABLED =
  import.meta.env.VITE_ARTIFACT_SCAN_FALLBACK !== "false";

export const PPT_PREVIEW_PREPARE_ENABLED =
  import.meta.env.VITE_PPT_PREPARE_PREVIEW !== "false";

export interface WorkspaceApiError {
  code: string;
  message: string;
  status?: number;
}

export interface ArtifactContentResult {
  content: string | null;
  encoding: "utf-8" | "base64" | null;
  error: WorkspaceApiError | null;
}

export interface WorkspaceChange {
  path: string;
  mtimeMs: number;
  size: number;
  ext: string;
}

export interface PreparePreviewResponse {
  status: "ready" | "converting" | "failed" | "unsupported";
  previewPath?: string;
  reason?: string;
}

interface WorkspaceChangesResponse {
  changes: WorkspaceChange[];
}

function parseWorkspaceError(error: unknown): WorkspaceApiError {
  if (typeof error === "object" && error !== null) {
    const errObj = error as {
      response?: { status?: number; data?: { error?: string; code?: string } };
      message?: string;
    };
    const status = errObj.response?.status;
    const code = errObj.response?.data?.code ?? "WORKSPACE_API_ERROR";
    const message =
      errObj.response?.data?.error ??
      errObj.message ??
      "Workspace API request failed";
    return { code, message, status };
  }
  return { code: "WORKSPACE_API_ERROR", message: "Workspace API request failed" };
}

/**
 * Upload a file to the backend workspace and return the server-side absolute path.
 */
export async function uploadFileToWorkspace(file: File): Promise<string> {
  const formData = new FormData();
  formData.append("file", file);
  const resp: { filePath: string } = await request.post(
    "/workspace/upload",
    formData,
    {
      timeout: 60000,
      headers: {
        "Content-Type": "multipart/form-data",
      },
    }
  );
  return resp.filePath;
}

/**
 * Fetch artifact content from workspace.
 * - default: preview mode (e.g. PPT/PPTX will return converted PDF content)
 * - raw=true: returns original file content without conversion
 */
export async function fetchArtifactContent(
  filePath: string,
  opts?: { raw?: boolean }
): Promise<ArtifactContentResult> {
  try {
    const resp: FileContentResponse = await request.get("/workspace/file", {
      params: { path: filePath, raw: opts?.raw === true },
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
export async function prepareArtifactPreview(
  filePath: string
): Promise<PreparePreviewResponse> {
  try {
    const resp: PreparePreviewResponse = await request.post(
      "/workspace/preview/prepare",
      { path: filePath }
    );
    return resp;
  } catch (error) {
    const e = parseWorkspaceError(error);
    return { status: "failed", reason: e.message };
  }
}

/**
 * List changed files after given timestamp under cwd.
 */
export async function fetchWorkspaceChanges(
  cwd: string,
  since: number,
  limit = 200
): Promise<WorkspaceChange[]> {
  try {
    const resp: WorkspaceChangesResponse = await request.get("/workspace/changes", {
      params: { cwd, since, limit },
    });
    return resp.changes ?? [];
  } catch {
    return [];
  }
}
