import { useState, useRef, useEffect, useCallback } from "react";
import {
  Monitor,
  Lightbulb,
  FileText,
  Globe,
  RefreshCw,
  ExternalLink,
  ChevronDown,
  Plus,
  Check,
} from "lucide-react";
import { getPreviewUrl } from "../../lib/utils/workspaceApi";
import type { PreviewPortState } from "../../context/QuestSessionContext";
import type { Artifact } from "../../types/artifact";
import { ArtifactPreview } from "../quest/ArtifactPreview";

// ===== Types =====

export type PreviewMode = "artifact" | "http";

export interface PreviewPanelProps {
  sessionId: string | null;
  previewPorts: PreviewPortState;
  artifacts: Artifact[];
  activeArtifactId: string | null;
  onPortSelect: (port: number) => void;
  onAddPort: (port: number) => void;
  onSelectArtifact: (artifactId: string) => void;
  onRefresh: () => void;
  onOpenExternal: () => void;
}

// ===== Placeholder sub-components (to be implemented in subsequent tasks) =====

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

function HttpPreviewPane({
  selectedPort,
}: {
  selectedPort: number | null;
}) {
  if (!selectedPort) {
    return (
      <div className="flex-1 flex items-center justify-center text-gray-400 text-sm">
        未检测到可用端口
      </div>
    );
  }

  const url = getPreviewUrl(selectedPort);
  return (
    <iframe
      id="coding-preview-iframe"
      src={url}
      className="flex-1 w-full h-full border-none bg-white"
      sandbox="allow-scripts allow-same-origin allow-forms allow-popups"
      title="Dev Server Preview"
    />
  );
}

// ===== Empty State =====

function EmptyState() {
  return (
    <div className="flex-1 flex items-center justify-center bg-gray-50/50">
      <div className="text-center">
        <Monitor size={48} className="mx-auto mb-4 text-gray-300" />
        <div className="text-base text-gray-500 mb-1">预览窗口</div>
        <div className="text-xs text-gray-400 mb-4">
          等待 Agent 生成产物或启动开发服务器...
        </div>
        <div className="inline-flex items-center gap-1.5 text-xs text-gray-400 bg-gray-100/80 rounded-lg px-3 py-2">
          <Lightbulb size={12} className="text-amber-400 flex-shrink-0" />
          <span>在对话中让 Agent 创建项目并运行开发服务器即可预览</span>
        </div>
      </div>
    </div>
  );
}

// ===== Port Validation =====

function isValidPort(port: number): boolean {
  return Number.isInteger(port) && port >= 1024 && port <= 65535;
}

// ===== PortSelector =====

function PortSelector({
  previewPorts,
  onPortSelect,
  onAddPort,
}: {
  previewPorts: PreviewPortState;
  onPortSelect: (port: number) => void;
  onAddPort: (port: number) => void;
}) {
  const [open, setOpen] = useState(false);
  const [adding, setAdding] = useState(false);
  const [inputValue, setInputValue] = useState("");
  const [inputError, setInputError] = useState<string | null>(null);
  const dropdownRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLInputElement>(null);

  // Track previous ports to detect newly added ones
  const prevPortsRef = useRef<number[]>([]);
  const [newPorts, setNewPorts] = useState<Set<number>>(new Set());

  // Detect new ports and apply temporary highlight
  useEffect(() => {
    const prevPorts = prevPortsRef.current;
    const currentPorts = previewPorts.ports;

    if (prevPorts.length > 0) {
      const added = currentPorts.filter((p) => !prevPorts.includes(p));
      if (added.length > 0) {
        setNewPorts((prev) => {
          const next = new Set(prev);
          added.forEach((p) => next.add(p));
          return next;
        });

        // Clear highlight after 3 seconds
        const timer = setTimeout(() => {
          setNewPorts((prev) => {
            const next = new Set(prev);
            added.forEach((p) => next.delete(p));
            return next;
          });
        }, 3000);

        return () => clearTimeout(timer);
      }
    }

    prevPortsRef.current = currentPorts;
  }, [previewPorts.ports]);

  // Close dropdown on outside click
  useEffect(() => {
    if (!open) return;
    const handler = (e: MouseEvent) => {
      if (dropdownRef.current && !dropdownRef.current.contains(e.target as Node)) {
        setOpen(false);
        setAdding(false);
        setInputError(null);
      }
    };
    document.addEventListener("mousedown", handler);
    return () => document.removeEventListener("mousedown", handler);
  }, [open]);

  // Focus input when adding
  useEffect(() => {
    if (adding && inputRef.current) inputRef.current.focus();
  }, [adding]);

  const handleAddPort = useCallback(() => {
    const port = parseInt(inputValue, 10);
    if (isNaN(port) || !isValidPort(port)) {
      setInputError("端口范围：1024-65535");
      return;
    }
    onAddPort(port);
    onPortSelect(port);
    setInputValue("");
    setInputError(null);
    setAdding(false);
    setOpen(false);
  }, [inputValue, onAddPort, onPortSelect]);

  const { ports, selectedPort } = previewPorts;

  return (
    <div className="relative" ref={dropdownRef}>
      <button
        className="inline-flex items-center gap-1 px-2 py-1 rounded text-xs text-gray-600 hover:bg-gray-100 transition-colors font-mono"
        onClick={() => setOpen(!open)}
        title="选择端口"
      >
        :{selectedPort ?? "—"}
        <ChevronDown size={10} className={`transition-transform ${open ? "rotate-180" : ""}`} />
      </button>

      {open && (
        <div className="absolute top-full left-0 mt-1 bg-white border border-gray-200 rounded-lg shadow-lg z-50 min-w-[160px] py-1">
          {ports.map((port) => (
            <button
              key={port}
              className={`w-full flex items-center gap-2 px-3 py-1.5 text-xs font-mono hover:bg-gray-50 transition-colors ${
                port === selectedPort ? "text-blue-600 font-medium" : "text-gray-600"
              } ${newPorts.has(port) ? "bg-blue-50" : ""}`}
              onClick={() => {
                onPortSelect(port);
                setOpen(false);
              }}
            >
              {port === selectedPort && <Check size={10} className="flex-shrink-0" />}
              <span className={port === selectedPort ? "" : "ml-[18px]"}>:{port}</span>
              {newPorts.has(port) && (
                <span className="ml-auto px-1.5 py-0.5 text-[10px] font-semibold text-green-700 bg-green-100 rounded animate-pulse">
                  NEW
                </span>
              )}
            </button>
          ))}

          <div className="border-t border-gray-100 mt-1 pt-1">
            {adding ? (
              <div className="px-2 py-1">
                <div className="flex items-center gap-1">
                  <input
                    ref={inputRef}
                    type="number"
                    className={`flex-1 px-2 py-1 text-xs font-mono border rounded focus:outline-none focus:ring-1 ${
                      inputError
                        ? "border-red-300 focus:ring-red-400"
                        : "border-gray-200 focus:ring-blue-400"
                    }`}
                    placeholder="端口号"
                    value={inputValue}
                    onChange={(e) => {
                      setInputValue(e.target.value);
                      setInputError(null);
                    }}
                    onKeyDown={(e) => {
                      if (e.key === "Enter") handleAddPort();
                      if (e.key === "Escape") {
                        setAdding(false);
                        setInputError(null);
                      }
                    }}
                    min={1024}
                    max={65535}
                  />
                  <button
                    className="px-2 py-1 text-xs text-blue-600 hover:bg-blue-50 rounded transition-colors"
                    onClick={handleAddPort}
                  >
                    添加
                  </button>
                </div>
                {inputError && (
                  <div className="text-[10px] text-red-500 mt-0.5 px-0.5">{inputError}</div>
                )}
              </div>
            ) : (
              <button
                className="w-full flex items-center gap-1.5 px-3 py-1.5 text-xs text-gray-500 hover:bg-gray-50 transition-colors"
                onClick={() => setAdding(true)}
              >
                <Plus size={10} />
                手动添加端口
              </button>
            )}
          </div>
        </div>
      )}
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
  mode,
  onModeChange,
  hasArtifacts,
  hasPorts,
  previewPorts,
  artifacts,
  activeArtifactId,
  onPortSelect,
  onAddPort,
  onSelectArtifact,
  onRefresh,
  onOpenExternal,
}: {
  mode: PreviewMode;
  onModeChange: (mode: PreviewMode) => void;
  hasArtifacts: boolean;
  hasPorts: boolean;
  previewPorts: PreviewPortState;
  artifacts: Artifact[];
  activeArtifactId: string | null;
  onPortSelect: (port: number) => void;
  onAddPort: (port: number) => void;
  onSelectArtifact: (artifactId: string) => void;
  onRefresh: () => void;
  onOpenExternal: () => void;
}) {
  return (
    <div className="flex items-center gap-1 px-2 py-1 border-b border-gray-200/60 bg-white">
      {/* Mode switch buttons */}
      <button
        className={`inline-flex items-center gap-1 px-2 py-1 rounded text-xs transition-colors ${
          mode === "artifact"
            ? "bg-gray-100 text-gray-700 font-medium"
            : "text-gray-400 hover:text-gray-600 hover:bg-gray-50"
        }`}
        onClick={() => onModeChange("artifact")}
        disabled={!hasArtifacts}
        title="产物预览"
      >
        <FileText size={12} />
        产物
      </button>
      <button
        className={`inline-flex items-center gap-1 px-2 py-1 rounded text-xs transition-colors ${
          mode === "http"
            ? "bg-gray-100 text-gray-700 font-medium"
            : "text-gray-400 hover:text-gray-600 hover:bg-gray-50"
        }`}
        onClick={() => onModeChange("http")}
        disabled={!hasPorts}
        title="HTTP 服务预览"
      >
        <Globe size={12} />
        HTTP 服务
      </button>

      {/* Separator */}
      <div className="w-px h-4 bg-gray-200 mx-1" />

      {/* Mode-specific controls */}
      {mode === "http" && (
        <>
          <PortSelector
            previewPorts={previewPorts}
            onPortSelect={onPortSelect}
            onAddPort={onAddPort}
          />
          <div className="flex-1" />
          <button
            className="w-6 h-6 flex items-center justify-center rounded text-gray-400 hover:text-gray-600 hover:bg-gray-100 transition-colors"
            onClick={onRefresh}
            title="刷新预览"
          >
            <RefreshCw size={12} />
          </button>
          <button
            className="w-6 h-6 flex items-center justify-center rounded text-gray-400 hover:text-gray-600 hover:bg-gray-100 transition-colors"
            onClick={onOpenExternal}
            title="在新窗口打开"
          >
            <ExternalLink size={12} />
          </button>
        </>
      )}

      {mode === "artifact" && (
        <>
          <ArtifactSelector
            artifacts={artifacts}
            activeArtifactId={activeArtifactId}
            onSelectArtifact={onSelectArtifact}
          />
          <div className="flex-1" />
        </>
      )}
    </div>
  );
}

// ===== Main Component =====

export function PreviewPanel({
  previewPorts,
  artifacts,
  activeArtifactId,
  onPortSelect,
  onAddPort,
  onSelectArtifact,
  onRefresh,
  onOpenExternal,
}: PreviewPanelProps) {
  const [mode, setMode] = useState<PreviewMode>("artifact");
  const prevArtifactIdRef = useRef<string | null>(activeArtifactId);
  const prevPortsLengthRef = useRef<number>(previewPorts.ports.length);

  const hasArtifacts = artifacts.length > 0;
  const hasPorts = previewPorts.ports.length > 0;
  const hasContent = hasArtifacts || hasPorts;

  // 自动切换：新产物生成时切换到产物模式（需求 1.5）
  useEffect(() => {
    if (activeArtifactId && activeArtifactId !== prevArtifactIdRef.current) {
      setMode("artifact");
    }
    prevArtifactIdRef.current = activeArtifactId;
  }, [activeArtifactId]);

  // 自动切换：新端口检测到且无活跃产物时切换到 HTTP 模式（需求 1.6）
  useEffect(() => {
    const prevLen = prevPortsLengthRef.current;
    const currLen = previewPorts.ports.length;
    if (currLen > prevLen && !activeArtifactId) {
      setMode("http");
    }
    prevPortsLengthRef.current = currLen;
  }, [previewPorts.ports.length, activeArtifactId]);

  // Empty state: no artifacts and no ports
  if (!hasContent) {
    return <EmptyState />;
  }

  return (
    <div className="flex flex-col h-full">
      <PreviewToolbar
        mode={mode}
        onModeChange={setMode}
        hasArtifacts={hasArtifacts}
        hasPorts={hasPorts}
        previewPorts={previewPorts}
        artifacts={artifacts}
        activeArtifactId={activeArtifactId}
        onPortSelect={onPortSelect}
        onAddPort={onAddPort}
        onSelectArtifact={onSelectArtifact}
        onRefresh={onRefresh}
        onOpenExternal={onOpenExternal}
      />
      <div className="flex-1 min-h-0 overflow-hidden flex flex-col">
        {mode === "artifact" ? (
          <ArtifactPreviewPane
            artifacts={artifacts}
            activeArtifactId={activeArtifactId}
          />
        ) : (
          <HttpPreviewPane selectedPort={previewPorts.selectedPort} />
        )}
      </div>
    </div>
  );
}
