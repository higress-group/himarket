import { useState } from "react";
import { ChevronDown, ChevronRight, Eye, FileCode, Terminal } from "lucide-react";

export type InlineBlockType = "artifact" | "terminal";

interface InlineArtifactProps {
  type: InlineBlockType;
  title: string;
  children?: React.ReactNode;
  defaultExpanded?: boolean;
  onPreviewClick?: () => void;
}

const TYPE_CONFIG: Record<InlineBlockType, { label: string; icon: React.ReactNode; color: string }> = {
  artifact: {
    label: "Artifact",
    icon: <FileCode size={14} />,
    color: "text-blue-600 bg-blue-50 border-blue-200",
  },
  terminal: {
    label: "Terminal",
    icon: <Terminal size={14} />,
    color: "text-green-600 bg-green-50 border-green-200",
  },
};

export function InlineArtifact({
  type,
  title,
  children,
  defaultExpanded = true,
  onPreviewClick,
}: InlineArtifactProps) {
  const hasContent = children != null;
  const [expanded, setExpanded] = useState(hasContent ? defaultExpanded : false);
  const config = TYPE_CONFIG[type];

  return (
    <div className={`my-2 rounded-lg border ${config.color} overflow-hidden`}>
      {/* Header */}
      <div
        className={`flex items-center gap-2 px-3 py-1.5 select-none${hasContent ? " cursor-pointer" : ""}`}
        onClick={hasContent ? () => setExpanded(prev => !prev) : undefined}
      >
        {hasContent ? (
          expanded ? <ChevronDown size={14} /> : <ChevronRight size={14} />
        ) : null}
        {config.icon}
        <span className="text-xs font-medium">{config.label}</span>
        <span className="text-xs text-gray-500 truncate">{title}</span>
        <div className="flex-1" />
        {type === "artifact" && onPreviewClick && (
          <button
            className="flex items-center gap-1 text-xs text-blue-600 hover:text-blue-800 px-1.5 py-0.5 rounded hover:bg-blue-100 transition-colors"
            onClick={e => {
              e.stopPropagation();
              onPreviewClick();
            }}
            title="预览"
          >
            <Eye size={12} />
            预览
          </button>
        )}
      </div>

      {/* Content */}
      {hasContent && expanded && (
        <div className="border-t border-inherit px-3 py-2 bg-white/60">
          {children}
        </div>
      )}
    </div>
  );
}
