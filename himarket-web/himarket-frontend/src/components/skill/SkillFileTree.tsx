import { useState } from "react";
import { FolderOutlined, FolderOpenOutlined, FileOutlined } from "@ant-design/icons";
import type { SkillFileTreeNode } from "../../lib/apis/cliProvider";

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

function TreeNode({ node, selectedPath, onSelect, depth }: TreeNodeProps) {
  const [expanded, setExpanded] = useState(true);
  const isDir = node.type === "directory";
  const isSelected = node.path === selectedPath;

  const handleClick = () => {
    if (isDir) {
      setExpanded((v) => !v);
    } else {
      onSelect(node.path);
    }
  };

  return (
    <div>
      <div
        title={node.name}
        className={`
          flex items-center gap-1.5 px-2 py-1 rounded cursor-pointer text-sm select-none
          transition-colors duration-100
          ${isSelected ? "bg-purple-100 text-purple-700" : "hover:bg-gray-100 text-gray-700"}
        `}
        style={{ paddingLeft: `${8 + depth * 16}px` }}
        onClick={handleClick}
      >
        {isDir ? (
          expanded ? (
            <FolderOpenOutlined className="text-yellow-500 flex-shrink-0" />
          ) : (
            <FolderOutlined className="text-yellow-500 flex-shrink-0" />
          )
        ) : (
          <FileOutlined className="text-blue-400 flex-shrink-0" />
        )}
        <span className="truncate">{node.name}</span>
      </div>
      {isDir && expanded && node.children && node.children.length > 0 && (
        <div>
          {node.children.map((child) => (
            <TreeNode
              key={child.path}
              node={child}
              selectedPath={selectedPath}
              onSelect={onSelect}
              depth={depth + 1}
            />
          ))}
        </div>
      )}
    </div>
  );
}

export default function SkillFileTree({ nodes, selectedPath, onSelect }: SkillFileTreeProps) {
  return (
    <div className="overflow-y-auto overflow-x-hidden py-1">
      {nodes.map((node) => (
        <TreeNode key={node.path} node={node} selectedPath={selectedPath} onSelect={onSelect} depth={0} />
      ))}
    </div>
  );
}
