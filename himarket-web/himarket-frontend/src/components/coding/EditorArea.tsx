import { useCallback } from "react";
import Editor from "@monaco-editor/react";
import { X } from "lucide-react";
import type { OpenFile } from "../../types/coding";

interface EditorAreaProps {
  openFiles: OpenFile[];
  activeFilePath: string | null;
  onSelectFile: (path: string) => void;
  onCloseFile: (path: string) => void;
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

      {/* Editor */}
      <div className="flex-1 min-h-0 relative">
        {activeFile && (
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
        )}
      </div>
    </div>
  );
}
