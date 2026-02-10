import request from "../request";

interface FileContentResponse {
  content: string;
  encoding: "utf-8" | "base64";
}

/**
 * Fetch file content from the user workspace via the backend API.
 * Returns the file content string, or null if not available.
 */
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
      headers: { "Content-Type": "multipart/form-data" },
      timeout: 60000,
    }
  );
  return resp.filePath;
}

export async function fetchArtifactContent(
  filePath: string
): Promise<string | null> {
  try {
    const resp: FileContentResponse = await request.get("/workspace/file", {
      params: { path: filePath },
    });
    return resp.content ?? null;
  } catch {
    return null;
  }
}
