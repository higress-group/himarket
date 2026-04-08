import {
  FolderFilled,
  FolderOpenFilled,
  FileFilled,
  FileMarkdownFilled,
  FileTextFilled,
  CodeFilled,
  SettingFilled,
  Html5Filled,
  FileZipFilled,
  FileImageFilled,
  JavaScriptOutlined,
  JavaOutlined,
  PythonOutlined,
  DockerOutlined,
  RightOutlined,
  DownOutlined,
} from '@ant-design/icons';
import { Tooltip } from 'antd';
import { useState, useCallback } from 'react';

import type { FileNode } from '../../types/coding';

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

// ── File icon by extension (matching admin IDEA style) ───────────────
const iconClass = 'flex-shrink-0';
const iconStyle = { fontSize: 14 };

function FileIcon({ name }: { name: string }) {
  const ext = name.split('.').pop()?.toLowerCase() ?? '';
  const lowerName = name.toLowerCase();

  if (lowerName === 'dockerfile')
    return <DockerOutlined className={iconClass} style={{ ...iconStyle, color: '#1a9ad0' }} />;
  if (lowerName === '.gitignore')
    return <FileTextFilled className={iconClass} style={{ ...iconStyle, color: '#999' }} />;
  if (lowerName === 'license' || lowerName === 'notice')
    return <FileTextFilled className={iconClass} style={{ ...iconStyle, color: '#999' }} />;

  switch (ext) {
    case 'md':
      return (
        <FileMarkdownFilled className={iconClass} style={{ ...iconStyle, color: '#1a72bd' }} />
      );
    case 'json':
      return <SettingFilled className={iconClass} style={{ ...iconStyle, color: '#7568b8' }} />;
    case 'yaml':
    case 'yml':
      return <SettingFilled className={iconClass} style={{ ...iconStyle, color: '#c88a0a' }} />;
    case 'toml':
      return <SettingFilled className={iconClass} style={{ ...iconStyle, color: '#c88a0a' }} />;
    case 'xml':
      return <CodeFilled className={iconClass} style={{ ...iconStyle, color: '#cc5e1e' }} />;
    case 'html':
      return <Html5Filled className={iconClass} style={{ ...iconStyle, color: '#d94020' }} />;
    case 'css':
    case 'scss':
    case 'less':
      return <CodeFilled className={iconClass} style={{ ...iconStyle, color: '#2060b0' }} />;
    case 'js':
    case 'jsx':
      return (
        <JavaScriptOutlined className={iconClass} style={{ ...iconStyle, color: '#c89008' }} />
      );
    case 'ts':
    case 'tsx':
      return <CodeFilled className={iconClass} style={{ ...iconStyle, color: '#1e68b0' }} />;
    case 'py':
      return <PythonOutlined className={iconClass} style={{ ...iconStyle, color: '#2060a0' }} />;
    case 'java':
      return <JavaOutlined className={iconClass} style={{ ...iconStyle, color: '#cc5818' }} />;
    case 'sh':
    case 'bash':
      return <CodeFilled className={iconClass} style={{ ...iconStyle, color: '#208848' }} />;
    case 'go':
    case 'rs':
    case 'rb':
    case 'php':
    case 'c':
    case 'cpp':
    case 'h':
    case 'hpp':
    case 'cs':
    case 'swift':
    case 'kt':
    case 'vue':
    case 'svelte':
    case 'sql':
      return <CodeFilled className={iconClass} style={{ ...iconStyle, color: '#3880c0' }} />;
    case 'zip':
    case 'tar':
    case 'gz':
      return <FileZipFilled className={iconClass} style={{ ...iconStyle, color: '#b88520' }} />;
    case 'png':
    case 'jpg':
    case 'jpeg':
    case 'gif':
    case 'webp':
    case 'svg':
    case 'ico':
      return <FileImageFilled className={iconClass} style={{ ...iconStyle, color: '#5848b0' }} />;
    case 'txt':
    case 'log':
    case 'csv':
    case 'ini':
    case 'cfg':
    case 'conf':
      return <FileTextFilled className={iconClass} style={{ ...iconStyle, color: '#999' }} />;
    default:
      return <FileFilled className={iconClass} style={{ ...iconStyle, color: '#3880c0' }} />;
  }
}

function TreeNode({ depth, node, onFileSelect, selectedPath }: TreeNodeProps) {
  const [expanded, setExpanded] = useState(depth < 1);
  const isDir = node.type === 'directory';
  const isSelected = node.path === selectedPath;

  const handleClick = useCallback(() => {
    if (isDir) {
      setExpanded((prev) => !prev);
    } else {
      onFileSelect(node);
    }
  }, [isDir, node, onFileSelect]);

  return (
    <div>
      <Tooltip mouseEnterDelay={0.8} placement="right" title={node.name}>
        <button
          className={`
            flex items-center gap-1 px-1 py-[2px] rounded cursor-pointer text-[13px] select-none
            transition-colors duration-100 w-full text-left border-0 bg-transparent
            ${isSelected ? 'bg-blue-100 text-gray-900' : 'hover:bg-gray-100 text-gray-700'}
          `}
          onClick={handleClick}
          style={{ paddingLeft: `${4 + depth * 16}px` }}
          type="button"
        >
          {isDir ? (
            <span className="w-4 flex items-center justify-center flex-shrink-0 text-[10px] text-gray-400">
              {expanded ? <DownOutlined /> : <RightOutlined />}
            </span>
          ) : (
            <span className="w-4 flex-shrink-0" />
          )}
          {isDir ? (
            expanded ? (
              <FolderOpenFilled className="text-amber-500 flex-shrink-0 text-sm" />
            ) : (
              <FolderFilled className="text-amber-400 flex-shrink-0 text-sm" />
            )
          ) : (
            <FileIcon name={node.name} />
          )}
          <span className="truncate ml-0.5">{node.name}</span>
        </button>
      </Tooltip>
      {isDir && expanded && node.children && (
        <div>
          {node.children.map((child) => (
            <TreeNode
              depth={depth + 1}
              key={child.path}
              node={child}
              onFileSelect={onFileSelect}
              selectedPath={selectedPath}
            />
          ))}
          {node.truncated && (
            <div
              className="text-[11px] text-amber-500 px-2 py-1"
              style={{ paddingLeft: `${(depth + 1) * 16 + 4}px` }}
            >
              ⚠ 文件过多，仅显示部分
            </div>
          )}
        </div>
      )}
    </div>
  );
}

export function FileTree({ onFileSelect, selectedPath, tree }: FileTreeProps) {
  if (tree.length === 0) {
    return (
      <div className="flex items-center justify-center h-full text-xs text-gray-400 px-3">
        暂无文件
      </div>
    );
  }

  return (
    <div className="overflow-y-auto h-full py-1 select-none">
      {tree.map((node) => (
        <TreeNode
          depth={0}
          key={node.path}
          node={node}
          onFileSelect={onFileSelect}
          selectedPath={selectedPath}
        />
      ))}
    </div>
  );
}
