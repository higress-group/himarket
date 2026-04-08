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
import { useState } from 'react';

import type { SkillFileTreeNode } from '../../lib/apis/cliProvider';

interface SkillFileTreeProps {
  nodes: SkillFileTreeNode[];
  selectedPath?: string;
  onSelect: (path: string) => void;
}

interface TreeNodeProps {
  node: SkillFileTreeNode;
  selectedPath?: string;
  onSelect: (path: string) => void;
  depth: number;
}

// ── File icon by extension (matching admin style) ───────────────
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
    case 'zip':
    case 'tar':
    case 'gz':
      return <FileZipFilled className={iconClass} style={{ ...iconStyle, color: '#b88520' }} />;
    case 'png':
    case 'jpg':
    case 'jpeg':
    case 'gif':
    case 'svg':
      return <FileImageFilled className={iconClass} style={{ ...iconStyle, color: '#5848b0' }} />;
    case 'txt':
    case 'log':
    case 'csv':
      return <FileTextFilled className={iconClass} style={{ ...iconStyle, color: '#999' }} />;
    default:
      return <FileFilled className={iconClass} style={{ ...iconStyle, color: '#3880c0' }} />;
  }
}

function TreeNode({ depth, node, onSelect, selectedPath }: TreeNodeProps) {
  const [expanded, setExpanded] = useState(true);
  const isDir = node.type === 'directory';
  const isSelected = node.path === selectedPath;

  return (
    <div>
      <Tooltip mouseEnterDelay={0.8} placement="right" title={node.name}>
        <button
          className={`
            flex items-center gap-1 px-1 py-[2px] rounded cursor-pointer text-[13px] select-none
            transition-colors duration-100 w-full text-left border-0 bg-transparent
            ${isSelected ? 'bg-blue-100 text-gray-900' : 'hover:bg-gray-100 text-gray-700'}
          `}
          onClick={() => (isDir ? setExpanded((v) => !v) : onSelect(node.path))}
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
      {isDir && expanded && node.children && node.children.length > 0 && (
        <div>
          {node.children.map((child) => (
            <TreeNode
              depth={depth + 1}
              key={child.path}
              node={child}
              onSelect={onSelect}
              selectedPath={selectedPath}
            />
          ))}
        </div>
      )}
    </div>
  );
}

export default function SkillFileTree({ nodes, onSelect, selectedPath }: SkillFileTreeProps) {
  return (
    <div className="overflow-y-auto overflow-x-hidden py-1">
      {nodes.map((node) => (
        <TreeNode
          depth={0}
          key={node.path}
          node={node}
          onSelect={onSelect}
          selectedPath={selectedPath}
        />
      ))}
    </div>
  );
}
