import { useCallback, useEffect, useState } from "react";
import Editor from "@monaco-editor/react";
import { X, Download, FileBox, Loader2 } from "lucide-react";
import type { OpenFile } from "../../types/coding";
import { ImageRenderer } from "../quest/renderers/ImageRenderer";
import { downloadWorkspaceFile } from "../../lib/utils/workspaceApi";
import request from "../../lib/request";
import { getDefaultRuntime } from "../../lib/utils/workspaceApi";

interface EditorAreaProps {
  openFiles: OpenFile[];
  activeFilePath: string | null;
  onSelectFile: (path: string) => void;
  onCloseFile: (path: string) => void;
}

const IMAGE_EXTENSIONS = new Set(["png", "jpg", "jpeg", "gif", "webp"]);
const PDF_EXTENSIONS = new Set(["pdf"]);
const BINARY_DOWNLOAD_EXTENSIONS = new Set([
  "pptx", "ppt", "docx", "doc", "xlsx", "xls",
  "zip", "tar", "gz", "mp4", "mov", "mp3", "wav",
]);

function getExt(fileName: string): string {
  return fileName.split(".").pop()?.toLowerCase() ?? "";
}

const EXT_LABELS: Record<string, string> = {
  pptx: "PowerPoint", ppt: "PowerPoint",
  docx: "Word", doc: "Word",
  xlsx: "Excel", xls: "Excel",
  zip: "Archive", tar: "Archive", gz: "Archive",
  mp4: "Video", mov: "Video",
  mp3: "Audio", wav: "Audio",
};

/** 通过后端下载接口获取 PDF blob 并用 iframe 渲染 */
function PdfPreview({ file }: { file: OpenFile }) {
  const [blobUrl, setBlobUrl] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError(null);

    const params: Record<string, string> = { path: file.path };
    const rt = getDefaultRuntime();
    if (rt) params.runtime = rt;

    request
      .get("/workspace/download", { params, responseType: "blob", timeout: 60000 })
      .then((resp: unknown) => {
        if (cancelled) return;
        const blob = resp instanceof Blob ? resp : new Blob([resp as BlobPart]);
        setBlobUrl(URL.createObjectURL(blob));
      })
      .catch((e: Error) => {
        if (!cancelled) setError(e.message ?? "PDF 加载失败");
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });

    return () => {
      cancelled = true;
      setBlobUrl(prev => { if (prev) URL.revokeObjectURL(prev); return null; });
    };
  }, [file.path]);

  if (loading) {
    return (
      <div className="flex items-center justify-center h-full text-gray-400 text-sm gap-2">
        <Loader2 size={16} className="animate-spin" />
        加载 PDF...
      </div>
    );
  }
  if (error || !blobUrl) {
    return (
      <div className="flex items-center justify-center h-full text-sm text-gray-400">
        PDF 预览失败：{error ?? "未知错误"}
      </div>
    );
  }
  return <iframe src={blobUrl} className="w-full h-full border-none" title="PDF Preview" />;
}

/** 下载工具栏：显示在预览内容上方 */
function DownloadBar({ file }: { file: OpenFile }) {
  const handleDownload = () => downloadWorkspaceFile(file.path, file.fileName);

  return (
    <div className="flex items-center justify-between px-3 py-1.5 border-b border-gray-200/60 bg-white/80 flex-shrink-0">
      <span className="text-xs text-gray-500 truncate">{file.fileName}</span>
      <button
        className="inline-flex items-center gap-1.5 px-2.5 py-1 text-xs font-medium rounded
                   border border-gray-200 text-gray-600
                   hover:bg-gray-50 hover:border-gray-300 transition-colors"
        onClick={handleDownload}
        title="下载文件"
      >
        <Download size={13} />
        下载
      </button>
    </div>
  );
}

/** 通用二进制文件占位 + 下载 */
function BinaryFilePlaceholder({ file }: { file: OpenFile }) {
  const ext = getExt(file.fileName);
  const label = EXT_LABELS[ext] ?? ext.toUpperCase();
  const handleDownload = () => downloadWorkspaceFile(file.path, file.fileName);

  return (
    <div className="flex items-center justify-center h-full p-6">
      <div className="text-center space-y-3">
        <FileBox size={48} className="mx-auto text-gray-300" />
        <div>
          <div className="text-sm font-medium text-gray-700">{file.fileName}</div>
          <div className="text-xs text-gray-400 mt-1">{label} 文件</div>
        </div>
        <button
          className="inline-flex items-center gap-1.5 px-4 py-2 text-xs font-medium
                     rounded-md border border-gray-200 text-gray-600
                     hover:bg-gray-50 hover:border-gray-300 transition-colors"
          onClick={handleDownload}
        >
          <Download size={14} />
          下载文件
        </button>
      </div>
    </div>
  );
}

export function EditorArea({
  openFiles,
  activeFilePath,
  onSelectFile,
  onCloseFile,
}: EditorAreaProps) {
  const activeFile = openFiles.find(f => f.path === activeFilePath) ?? null;

  const handleClose = useCallback(
    (e: React.MouseEvent, path: string) => {
      e.stopPropagation();
      onCloseFile(path);
    },
    [onCloseFile]
  );

  if (openFiles.length === 0) {
    return (
      <div className="flex-1 flex items-center justify-center bg-gray-50/50 text-gray-400 text-sm">
        从左侧文件树选择文件开始编辑
      </div>
    );
  }

  const ext = activeFile ? getExt(activeFile.fileName) : "";
  const isImage = activeFile ? IMAGE_EXTENSIONS.has(ext) : false;
  const isPdf = activeFile ? PDF_EXTENSIONS.has(ext) : false;
  const isBinaryDownload = activeFile ? BINARY_DOWNLOAD_EXTENSIONS.has(ext) : false;
  const showDownloadBar = isImage || isPdf;

  return (
    <div className="flex-1 flex flex-col min-w-0 min-h-0 overflow-hidden">
      {/* Tabs */}
      <div className="flex items-center border-b border-gray-200/60 bg-gray-50/80 overflow-x-auto scrollbar-hide flex-shrink-0">
        {openFiles.map(file => {
          const isActive = file.path === activeFilePath;
          return (
            <button
              key={file.path}
              className={`flex items-center gap-1.5 px-3 py-1.5 text-xs border-r border-gray-200/60
                whitespace-nowrap transition-colors group
                ${
                  isActive
                    ? "bg-white text-gray-800 border-b-2 border-b-blue-500"
                    : "text-gray-500 hover:bg-gray-100/80 hover:text-gray-700"
                }`}
              onClick={() => onSelectFile(file.path)}
            >
              <span className="truncate max-w-[120px]">{file.fileName}</span>
              <span
                className="w-4 h-4 flex items-center justify-center rounded-sm
                  opacity-0 group-hover:opacity-100 hover:bg-gray-200 transition-all"
                onClick={e => handleClose(e, file.path)}
              >
                <X size={12} />
              </span>
            </button>
          );
        })}
      </div>

      {/* Download bar for previewable binary files */}
      {activeFile && showDownloadBar && <DownloadBar file={activeFile} />}

      {/* Content */}
      <div className="flex-1 min-h-0 relative">
        {activeFile && isPdf ? (
          <div className="absolute inset-0">
            <PdfPreview file={activeFile} />
          </div>
        ) : activeFile && isImage ? (
          <div className="absolute inset-0">
            <ImageRenderer content={activeFile.content} path={activeFile.path} />
          </div>
        ) : activeFile && isBinaryDownload ? (
          <div className="absolute inset-0">
            <BinaryFilePlaceholder file={activeFile} />
          </div>
        ) : activeFile ? (
          <Editor
            key={activeFile.path}
            language={activeFile.language}
            value={activeFile.content}
            path={activeFile.path}
            theme="vs-light"
            height="100%"
            options={{
              readOnly: true,
              minimap: { enabled: false },
              fontSize: 13,
              lineNumbers: "on",
              scrollBeyondLastLine: false,
              wordWrap: "on",
              automaticLayout: true,
              renderLineHighlight: "line",
              padding: { top: 8 },
            }}
          />
        ) : null}
      </div>
    </div>
  );
}
