import { useCallback } from "react";
import Editor from "@monaco-editor/react";
import { X, Download, FileBox, Maximize2 } from "lucide-react";
import type { OpenFile } from "../../types/coding";
import { ImageRenderer } from "../quest/renderers/ImageRenderer";
import { PdfRenderer } from "../quest/renderers/PdfRenderer";

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

function decodeBase64ToBlob(content: string, mime: string): Blob {
  const bin = atob(content);
  const bytes = new Uint8Array(bin.length);
  for (let i = 0; i < bin.length; i++) bytes[i] = bin.charCodeAt(i);
  return new Blob([bytes], { type: mime });
}

const EXT_MIME: Record<string, string> = {
  pdf: "application/pdf",
  png: "image/png", jpg: "image/jpeg", jpeg: "image/jpeg",
  gif: "image/gif", webp: "image/webp",
  pptx: "application/vnd.openxmlformats-officedocument.presentationml.presentation",
  ppt: "application/vnd.ms-powerpoint",
  docx: "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
  xlsx: "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
  zip: "application/zip",
  mp4: "video/mp4",
  mp3: "audio/mpeg",
};

const EXT_LABELS: Record<string, string> = {
  pptx: "PowerPoint", ppt: "PowerPoint",
  docx: "Word", doc: "Word",
  xlsx: "Excel", xls: "Excel",
  zip: "Archive", tar: "Archive", gz: "Archive",
  mp4: "Video", mov: "Video",
  mp3: "Audio", wav: "Audio",
};

/** 下载工具栏：显示在预览内容上方 */
function DownloadBar({ file }: { file: OpenFile }) {
  const ext = getExt(file.fileName);
  const mime = EXT_MIME[ext] ?? "application/octet-stream";

  const handleDownload = () => {
    const blob =
      file.encoding === "base64"
        ? decodeBase64ToBlob(file.content, mime)
        : new Blob([file.content], { type: mime });
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = file.fileName;
    a.click();
    URL.revokeObjectURL(url);
  };

  const handleOpenTab = () => {
    const blob =
      file.encoding === "base64"
        ? decodeBase64ToBlob(file.content, mime)
        : new Blob([file.content], { type: mime });
    const url = URL.createObjectURL(blob);
    window.open(url, "_blank");
  };

  return (
    <div className="flex items-center justify-between px-3 py-1.5 border-b border-gray-200/60 bg-white/80 flex-shrink-0">
      <span className="text-xs text-gray-500 truncate">{file.fileName}</span>
      <div className="flex items-center gap-1">
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
        <button
          className="w-7 h-7 flex items-center justify-center rounded text-gray-400
                     hover:text-gray-600 hover:bg-gray-100 transition-colors"
          onClick={handleOpenTab}
          title="在新标签页打开"
        >
          <Maximize2 size={14} />
        </button>
      </div>
    </div>
  );
}

/** 通用二进制文件占位 + 下载 */
function BinaryFilePlaceholder({ file }: { file: OpenFile }) {
  const ext = getExt(file.fileName);
  const label = EXT_LABELS[ext] ?? ext.toUpperCase();
  const mime = EXT_MIME[ext] ?? "application/octet-stream";

  const handleDownload = () => {
    const blob =
      file.encoding === "base64"
        ? decodeBase64ToBlob(file.content, mime)
        : new Blob([file.content], { type: mime });
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = file.fileName;
    a.click();
    URL.revokeObjectURL(url);
  };

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
            <PdfRenderer content={activeFile.content} />
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
