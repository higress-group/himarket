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
