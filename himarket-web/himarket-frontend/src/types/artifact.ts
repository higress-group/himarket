export type ArtifactType = "html" | "markdown" | "svg" | "image" | "pdf" | "file";

export interface Artifact {
  id: string;
  toolCallId: string;
  type: ArtifactType;
  path: string;
  fileName: string;
  content: string | null;
  updatedAt: number;
}

/** Extensions that map to a previewable artifact type */
export const ARTIFACT_EXTENSIONS: Record<string, ArtifactType> = {
  ".html": "html",
  ".htm": "html",
  ".svg": "svg",
  ".png": "image",
  ".jpg": "image",
  ".jpeg": "image",
  ".gif": "image",
  ".webp": "image",
  ".pdf": "pdf",
  ".pptx": "file",
  ".ppt": "file",
  ".xlsx": "file",
  ".xls": "file",
};
