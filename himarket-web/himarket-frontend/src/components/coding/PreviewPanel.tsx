import { useState, useRef, useEffect } from "react";
import {
  Monitor,
  Lightbulb,
  FileText,
  ChevronDown,
  Check,
} from "lucide-react";
import type { Artifact } from "../../types/artifact";
import { ArtifactPreview } from "./ArtifactPreview";

// ===== Types =====

export interface PreviewPanelProps {
  artifacts: Artifact[];
  activeArtifactId: string | null;
  onSelectArtifact: (artifactId: string) => void;
}

// ===== ArtifactPreviewPane =====

function ArtifactPreviewPane({
  artifacts,
  activeArtifactId,
}: {
  artifacts: Artifact[];
  activeArtifactId: string | null;
}) {
  const active = artifacts.find((a) => a.id === activeArtifactId);
  if (!active) {
    return (
      <div className="flex-1 flex items-center justify-center text-gray-400 text-sm">
        未选择产物
      </div>
    );
  }
  return <ArtifactPreview artifact={active} />;
}

// ===== Empty State =====

function EmptyState() {
  return (
    <div className="flex-1 flex items-center justify-center bg-gray-50/50">
      <div className="text-center">
        <Monitor size={48} className="mx-auto mb-4 text-gray-300" />
        <div className="text-base text-gray-500 mb-1">预览窗口</div>
        <div className="text-xs text-gray-400 mb-4">
          等待 Agent 生成可预览的产物...
        </div>
        <div className="inline-flex items-center gap-1.5 text-xs text-gray-400 bg-gray-100/80 rounded-lg px-3 py-2">
          <Lightbulb size={12} className="text-amber-400 flex-shrink-0" />
          <span>在对话中让 Agent 生成 HTML、图片、PDF 等文件即可预览</span>
        </div>
      </div>
    </div>
  );
}

// ===== ArtifactSelector =====

function ArtifactSelector({
  artifacts,
  activeArtifactId,
  onSelectArtifact,
}: {
  artifacts: Artifact[];
  activeArtifactId: string | null;
  onSelectArtifact: (artifactId: string) => void;
}) {
  const [open, setOpen] = useState(false);
  const dropdownRef = useRef<HTMLDivElement>(null);

  const active = artifacts.find((a) => a.id === activeArtifactId);

  useEffect(() => {
    if (!open) return;
    const handler = (e: MouseEvent) => {
      if (dropdownRef.current && !dropdownRef.current.contains(e.target as Node)) {
        setOpen(false);
      }
    };
    document.addEventListener("mousedown", handler);
    return () => document.removeEventListener("mousedown", handler);
  }, [open]);

  // Single artifact: just show file name, no dropdown
  if (artifacts.length <= 1) {
    return (
      <span className="inline-flex items-center gap-1 px-2 py-1 text-xs text-gray-600 truncate max-w-[180px]" title={active?.fileName}>
        <FileText size={11} className="flex-shrink-0 text-gray-400" />
        {active?.fileName ?? "—"}
      </span>
    );
  }

  // Multiple artifacts: dropdown selector
  return (
    <div className="relative" ref={dropdownRef}>
      <button
        className="inline-flex items-center gap-1 px-2 py-1 rounded text-xs text-gray-600 hover:bg-gray-100 transition-colors truncate max-w-[180px]"
        onClick={() => setOpen(!open)}
        title={active?.fileName ?? "选择产物"}
      >
        <FileText size={11} className="flex-shrink-0 text-gray-400" />
        <span className="truncate">{active?.fileName ?? "选择产物"}</span>
        <ChevronDown size={10} className={`flex-shrink-0 transition-transform ${open ? "rotate-180" : ""}`} />
      </button>

      {open && (
        <div className="absolute top-full left-0 mt-1 bg-white border border-gray-200 rounded-lg shadow-lg z-50 min-w-[200px] max-w-[300px] py-1 max-h-[240px] overflow-y-auto">
          {artifacts.map((artifact) => (
            <button
              key={artifact.id}
              className={`w-full flex items-center gap-2 px-3 py-1.5 text-xs hover:bg-gray-50 transition-colors truncate ${
                artifact.id === activeArtifactId ? "text-blue-600 font-medium" : "text-gray-600"
              }`}
              onClick={() => {
                onSelectArtifact(artifact.id);
                setOpen(false);
              }}
              title={artifact.fileName}
            >
              {artifact.id === activeArtifactId && <Check size={10} className="flex-shrink-0" />}
              <span className={`truncate ${artifact.id === activeArtifactId ? "" : "ml-[18px]"}`}>
                {artifact.fileName}
              </span>
            </button>
          ))}
        </div>
      )}
    </div>
  );
}

// ===== PreviewToolbar =====

function PreviewToolbar({
  artifacts,
  activeArtifactId,
  onSelectArtifact,
}: {
  artifacts: Artifact[];
  activeArtifactId: string | null;
  onSelectArtifact: (artifactId: string) => void;
}) {
  return (
    <div className="flex items-center gap-1 px-2 py-1 border-b border-gray-200/60 bg-white">
      <ArtifactSelector
        artifacts={artifacts}
        activeArtifactId={activeArtifactId}
        onSelectArtifact={onSelectArtifact}
      />
      <div className="flex-1" />
    </div>
  );
}

// ===== Main Component =====

export function PreviewPanel({
  artifacts,
  activeArtifactId,
  onSelectArtifact,
}: PreviewPanelProps) {
  if (artifacts.length === 0) {
    return <EmptyState />;
  }

  return (
    <div className="flex flex-col h-full">
      <PreviewToolbar
        artifacts={artifacts}
        activeArtifactId={activeArtifactId}
        onSelectArtifact={onSelectArtifact}
      />
      <div className="flex-1 min-h-0 overflow-hidden flex flex-col">
        <ArtifactPreviewPane
          artifacts={artifacts}
          activeArtifactId={activeArtifactId}
        />
      </div>
    </div>
  );
}
