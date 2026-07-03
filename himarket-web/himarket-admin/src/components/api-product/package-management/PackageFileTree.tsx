import {
  CodeFilled,
  DockerOutlined,
  DownOutlined,
  FileFilled,
  FileImageFilled,
  FileMarkdownFilled,
  FileTextFilled,
  FileZipFilled,
  FolderFilled,
  FolderOpenFilled,
  Html5Filled,
  JavaOutlined,
  JavaScriptOutlined,
  PythonOutlined,
  RightOutlined,
  SettingFilled,
} from '@ant-design/icons';
import { Tooltip } from 'antd';
import { useState } from 'react';

import type { PackageFileTreeNode } from './types';

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

interface TreeNodeProps {
  node: PackageFileTreeNode;
  selectedPath?: string;
  onSelect: (path: string) => void;
  depth: number;
}

function TreeNode({ depth, node, onSelect, selectedPath }: TreeNodeProps) {
  const [expanded, setExpanded] = useState(true);
  const isDir = node.type === 'directory';
  const isSelected = node.path === selectedPath;

  return (
    <div>
      <Tooltip mouseEnterDelay={0.8} placement="right" title={node.name}>
        <div
          className={`
            flex items-center gap-1 px-1 py-[2px] rounded cursor-pointer text-[13px] select-none
            transition-colors duration-100
            ${isSelected ? 'bg-blue-100 text-gray-900' : 'hover:bg-gray-100 text-gray-700'}
          `}
          onClick={() => (isDir ? setExpanded((value) => !value) : onSelect(node.path))}
          onKeyDown={(event) => {
            if (event.key === 'Enter' || event.key === ' ') {
              event.preventDefault();
              if (isDir) {
                setExpanded((value) => !value);
              } else {
                onSelect(node.path);
              }
            }
          }}
          role="button"
          style={{ paddingLeft: `${4 + depth * 16}px` }}
          tabIndex={0}
        >
          {isDir ? (
            <span className="flex w-4 flex-shrink-0 items-center justify-center text-[10px] text-gray-400">
              {expanded ? <DownOutlined /> : <RightOutlined />}
            </span>
          ) : (
            <span className="w-4 flex-shrink-0" />
          )}
          {isDir ? (
            expanded ? (
              <FolderOpenFilled className="flex-shrink-0 text-sm text-amber-500" />
            ) : (
              <FolderFilled className="flex-shrink-0 text-sm text-amber-400" />
            )
          ) : (
            <FileIcon name={node.name} />
          )}
          <span className="ml-0.5 truncate">{node.name}</span>
        </div>
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

interface PackageFileTreeProps {
  nodes: PackageFileTreeNode[];
  selectedPath?: string;
  onSelect: (path: string) => void;
}

export function PackageFileTree({ nodes, onSelect, selectedPath }: PackageFileTreeProps) {
  return (
    <div className="py-1">
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
