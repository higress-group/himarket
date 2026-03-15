import { useState, useCallback, type ReactNode } from "react";
import {
  ChevronRight,
  ChevronDown,
  File,
  Folder,
  FolderOpen,
  Braces,
  FileCode,
  FileText,
  Image,
} from "lucide-react";
import type { FileNode } from "../../types/coding";

interface FileTreeProps {
  tree: FileNode[];
  onFileSelect: (node: FileNode) => void;
  selectedPath?: string | null;
}

interface TreeNodeProps {
  node: FileNode;
  depth: number;
  onFileSelect: (node: FileNode) => void;
  selectedPath?: string | null;
}

const JSON_EXTS = new Set(["json"]);
const CODE_EXTS = new Set([
  "ts",
  "tsx",
  "js",
  "jsx",
  "py",
  "java",
  "go",
  "rs",
  "rb",
  "php",
  "c",
  "cpp",
  "h",
  "hpp",
  "cs",
  "swift",
  "kt",
  "html",
  "htm",
  "css",
  "scss",
  "less",
  "vue",
  "svelte",
  "sh",
  "bash",
  "sql",
]);
const IMAGE_EXTS = new Set(["png", "jpg", "jpeg", "gif", "webp", "svg", "ico"]);
const TEXT_EXTS = new Set([
  "md",
  "txt",
  "log",
  "yml",
  "yaml",
  "toml",
  "ini",
  "cfg",
  "conf",
]);

function getFileIcon(name: string): ReactNode {
  const ext = name.split(".").pop()?.toLowerCase() ?? "";
  if (JSON_EXTS.has(ext))
    return <Braces size={14} className="text-amber-500" />;
  if (CODE_EXTS.has(ext))
    return <FileCode size={14} className="text-blue-400" />;
  if (IMAGE_EXTS.has(ext))
    return <Image size={14} className="text-green-400" />;
  if (TEXT_EXTS.has(ext))
    return <FileText size={14} className="text-gray-400" />;
  return <File size={14} className="text-gray-400" />;
}

function TreeNode({ node, depth, onFileSelect, selectedPath }: TreeNodeProps) {
  const [expanded, setExpanded] = useState(depth < 1);
  const isDir = node.type === "directory";
  const isSelected = node.path === selectedPath;

  const handleClick = useCallback(() => {
    if (isDir) {
      setExpanded(prev => !prev);
    } else {
      onFileSelect(node);
    }
  }, [isDir, node, onFileSelect]);

  return (
    <div>
      <button
        className={`flex items-center w-full text-left py-[3px] text-[13px] transition-colors group
          border-l-[3px] ${
            isSelected
              ? "bg-purple-50/80 text-purple-700 border-purple-400"
              : "text-gray-600 hover:bg-gray-50 border-transparent"
          }`}
        style={{ paddingLeft: `${depth * 12 + 4}px` }}
        onClick={handleClick}
      >
        {isDir ? (
          <span className="w-4 h-4 flex items-center justify-center flex-shrink-0 mr-0.5">
            {expanded ? (
              <ChevronDown size={14} className="text-gray-400" />
            ) : (
              <ChevronRight size={14} className="text-gray-400" />
            )}
          </span>
        ) : (
          <span className="w-4 h-4 flex-shrink-0 mr-0.5" />
        )}
        <span className="w-4 h-4 flex items-center justify-center flex-shrink-0 mr-1.5">
          {isDir ? (
            expanded ? (
              <FolderOpen size={14} className="text-amber-500" />
            ) : (
              <Folder size={14} className="text-amber-500" />
            )
          ) : (
            getFileIcon(node.name)
          )}
        </span>
        <span className="truncate">{node.name}</span>
      </button>
      {isDir && expanded && node.children && (
        <div>
          {node.children.map(child => (
            <TreeNode
              key={child.path}
              node={child}
              depth={depth + 1}
              onFileSelect={onFileSelect}
              selectedPath={selectedPath}
            />
          ))}
          {node.truncated && (
            <div
              className="text-[11px] text-amber-500 px-2 py-1"
              style={{ paddingLeft: `${(depth + 1) * 12 + 4}px` }}
            >
              ⚠ 文件过多，仅显示部分
            </div>
          )}
        </div>
      )}
    </div>
  );
}

export function FileTree({ tree, onFileSelect, selectedPath }: FileTreeProps) {
  if (tree.length === 0) {
    return (
      <div className="flex items-center justify-center h-full text-xs text-gray-400 px-3">
        暂无文件
      </div>
    );
  }

  return (
    <div className="overflow-y-auto h-full py-1 select-none">
      {tree.map(node => (
        <TreeNode
          key={node.path}
          node={node}
          depth={0}
          onFileSelect={onFileSelect}
          selectedPath={selectedPath}
        />
      ))}
    </div>
  );
}
