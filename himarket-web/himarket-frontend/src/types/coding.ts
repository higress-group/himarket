// ===== Coding IDE Types =====

export interface FileNode {
  name: string;
  path: string;
  type: "file" | "directory";
  extension?: string;
  size?: number;
  children?: FileNode[];
}

export interface OpenFile {
  path: string;
  fileName: string;
  content: string;
  language: string; // Monaco language id
}

export interface TerminalSession {
  id: string;
  lines: string[]; // raw text lines including ANSI codes
}
