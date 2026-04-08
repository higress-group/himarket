export type ArtifactType = 'html' | 'markdown' | 'svg' | 'image' | 'pdf' | 'file';

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
  '.gif': 'image',
  '.htm': 'html',
  '.html': 'html',
  '.jpeg': 'image',
  '.jpg': 'image',
  '.pdf': 'pdf',
  '.png': 'image',
  '.ppt': 'file',
  '.pptx': 'file',
  '.svg': 'svg',
  '.webp': 'image',
  '.xls': 'file',
  '.xlsx': 'file',
};
