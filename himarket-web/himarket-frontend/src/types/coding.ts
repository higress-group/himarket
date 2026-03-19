// ===== Coding IDE Types =====

export interface FileNode {
  name: string;
  path: string;
  type: "file" | "directory";
  extension?: string;
  size?: number;
  children?: FileNode[];
  truncated?: boolean; // true when children were cut off due to node limit
}

export interface OpenFile {
  path: string;
  fileName: string;
  content: string;
  language: string; // Monaco language id
  encoding?: "utf-8" | "base64"; // base64 for binary files (images, pdf, etc.)
}

export interface TerminalSession {
  id: string;
  lines: string[]; // raw text lines including ANSI codes
}

/**
 * 沙箱类型，与后端 SandboxType 枚举对应。
 * - remote: 远程沙箱（K8s / Docker / 裸机）
 * - open-sandbox: OpenSandbox 沙箱
 * - e2b: E2B 云沙箱
 */
export type SandboxRuntime = "remote" | "open-sandbox" | "e2b";

// ===== Coding Config Types =====

export interface CodingConfig {
  modelProductId: string | null;
  modelName?: string | null;
  cliProviderId: string | null;
  cliProviderName?: string | null;
  cliRuntime: SandboxRuntime;
  cliSessionConfig?: string;
  skills: string[];
  mcpServers: string[];
}

export const DEFAULT_CONFIG: CodingConfig = {
  modelProductId: null,
  cliProviderId: null,
  cliRuntime: "remote",
  skills: [],
  mcpServers: [],
};

export function isConfigComplete(config: CodingConfig): boolean {
  return config.modelProductId !== null && config.cliProviderId !== null;
}
