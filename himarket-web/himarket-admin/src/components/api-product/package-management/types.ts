export interface PackageFileTreeNode {
  name: string;
  path: string;
  type: 'file' | 'directory';
  encoding?: string;
  size?: number;
  children?: PackageFileTreeNode[];
}

export interface PackageFileContent {
  path: string;
  content: string;
  encoding: string;
  size: number;
}

export interface PackageVersionItem {
  version: string;
  updateTime?: number;
  status?: string;
  downloadCount?: number;
  author?: string;
  publishPipelineInfo?: string;
  isLatest?: boolean;
}

export interface PackagePipelineNode {
  nodeId?: string;
  passed?: boolean;
  durationMs?: number | null;
  message?: string;
  executedAt?: string | number;
}
