import { ChevronDown, ChevronRight, Eye, FileCode, Terminal } from 'lucide-react';
import { useState } from 'react';

export type InlineBlockType = 'artifact' | 'terminal';

interface InlineArtifactProps {
  type: InlineBlockType;
  title: string;
  children?: React.ReactNode;
  defaultExpanded?: boolean;
  onPreviewClick?: () => void;
}

const TYPE_CONFIG: Record<
  InlineBlockType,
  { label: string; icon: React.ReactNode; color: string }
> = {
  artifact: {
    color: 'text-blue-600 bg-blue-50 border-blue-200',
    icon: <FileCode size={14} />,
    label: 'Artifact',
  },
  terminal: {
    color: 'text-green-600 bg-green-50 border-green-200',
    icon: <Terminal size={14} />,
    label: 'Terminal',
  },
};

export function InlineArtifact({
  children,
  defaultExpanded = true,
  onPreviewClick,
  title,
  type,
}: InlineArtifactProps) {
  const hasContent = children !== null && children !== undefined;
  const [expanded, setExpanded] = useState(hasContent ? defaultExpanded : false);
  const config = TYPE_CONFIG[type];

  return (
    <div className={`my-2 rounded-lg border ${config.color} overflow-hidden`}>
      {/* Header */}
      <button
        className={`flex items-center gap-2 px-3 py-1.5 select-none${hasContent ? ' cursor-pointer' : ''} border-0 bg-transparent w-full text-left`}
        onClick={hasContent ? () => setExpanded((prev) => !prev) : undefined}
        type="button"
      >
        {hasContent ? expanded ? <ChevronDown size={14} /> : <ChevronRight size={14} /> : null}
        {config.icon}
        <span className="text-xs font-medium">{config.label}</span>
        <span className="text-xs text-gray-500 truncate">{title}</span>
        <div className="flex-1" />
        {type === 'artifact' && onPreviewClick && (
          <button
            className="flex items-center gap-1 text-xs text-blue-600 hover:text-blue-800 px-1.5 py-0.5 rounded hover:bg-blue-100 transition-colors"
            onClick={(e) => {
              e.stopPropagation();
              onPreviewClick();
            }}
            title="预览"
          >
            <Eye size={12} />
            预览
          </button>
        )}
      </button>

      {/* Content */}
      {hasContent && expanded && (
        <div className="border-t border-inherit px-3 py-2 bg-white/60">{children}</div>
      )}
    </div>
  );
}
