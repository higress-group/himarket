import {
  useState,
  useRef,
  useCallback,
  type KeyboardEvent,
  type ClipboardEvent,
  type DragEvent,
  type ChangeEvent,
} from "react";
import {
  Send,
  Square,
  Paperclip,
  X,
  Image,
  FileText,
  Loader2,
} from "lucide-react";
import { useQuestState } from "../../context/QuestSessionContext";
import { SlashMenu } from "./SlashMenu";
import { uploadFileToWorkspace } from "../../lib/utils/workspaceApi";
import type { Attachment, FilePathAttachment } from "../../types/acp";
import type { QueuedPromptItem } from "../../context/QuestSessionContext";

const MAX_ATTACHMENTS = 10;
const MAX_SIZE_BYTES = 5 * 1024 * 1024; // 5MB

// Browsers often return "" for many text file types; map common extensions explicitly
const EXT_TO_MIME: Record<string, string> = {
  md: "text/markdown",
  mdx: "text/markdown",
  txt: "text/plain",
  csv: "text/csv",
  json: "application/json",
  yaml: "application/x-yaml",
  yml: "application/x-yaml",
  toml: "application/toml",
  xml: "application/xml",
  sql: "application/sql",
  graphql: "application/graphql",
  sh: "application/x-sh",
  bash: "application/x-sh",
};

function inferMimeType(file: File): string {
  if (file.type) return file.type;
  const ext = file.name.split(".").pop()?.toLowerCase() ?? "";
  return EXT_TO_MIME[ext] ?? "application/octet-stream";
}

let _attId = 0;
function nextAttId(): string {
  return `att-${++_attId}-${Date.now()}`;
}

interface QuestInputProps {
  onSend: (
    text: string,
    attachments?: Attachment[]
  ) =>
    | { queued: true; queuedPromptId?: string }
    | { queued: false; requestId?: string | number };
  onSendQueued?: (queuedPromptId?: string) => void;
  onDropQueuedPrompt: (promptId: string) => void;
  onCancel: () => void;
  isProcessing: boolean;
  queueSize: number;
  queuedPrompts: QueuedPromptItem[];
  disabled: boolean;
}

export function QuestInput({
  onSend,
  onSendQueued,
  onDropQueuedPrompt,
  onCancel,
  isProcessing,
  queueSize,
  queuedPrompts,
  disabled,
}: QuestInputProps) {
  const [text, setText] = useState("");
  const [showSlash, setShowSlash] = useState(false);
  const [attachments, setAttachments] = useState<Attachment[]>([]);
  const [uploading, setUploading] = useState(false);
  const [dragOver, setDragOver] = useState(false);
  const inputRef = useRef<HTMLTextAreaElement>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);
  const state = useQuestState();

  // Upload files to backend and create FilePathAttachment entries
  const addFiles = useCallback(
    async (files: FileList | File[]) => {
      const fileArray = Array.from(files);
      if (fileArray.length === 0) return;

      const remaining = MAX_ATTACHMENTS - attachments.length;
      if (remaining <= 0) return;
      const toProcess = fileArray
        .slice(0, remaining)
        .filter(f => f.size <= MAX_SIZE_BYTES);

      if (toProcess.length === 0) return;

      setUploading(true);
      const newAttachments: FilePathAttachment[] = [];
      for (const file of toProcess) {
        try {
          const serverPath = await uploadFileToWorkspace(file);
          const isImage = file.type.startsWith("image/");
          newAttachments.push({
            id: nextAttId(),
            kind: "file_path",
            name: file.name,
            filePath: serverPath,
            mimeType: inferMimeType(file),
            previewUrl: isImage ? URL.createObjectURL(file) : undefined,
          });
        } catch {
          // skip failed files
        }
      }
      setUploading(false);
      if (newAttachments.length > 0) {
        setAttachments(prev => [...prev, ...newAttachments]);
      }
    },
    [attachments.length]
  );

  const removeAttachment = useCallback((id: string) => {
    setAttachments(prev => {
      const att = prev.find(a => a.id === id);
      if (att && att.previewUrl) {
        URL.revokeObjectURL(att.previewUrl);
      }
      return prev.filter(a => a.id !== id);
    });
  }, []);

  const handleSend = useCallback(() => {
    const trimmed = text.trim();
    if (!trimmed && attachments.length === 0) return;
    const result = onSend(trimmed, attachments.length > 0 ? attachments : undefined);
    if (result.queued) {
      onSendQueued?.(result.queuedPromptId);
    }
    setText("");
    setShowSlash(false);
    setAttachments([]);
  }, [text, attachments, onSend, onSendQueued]);

  const handleKeyDown = (e: KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === "Enter" && !e.shiftKey) {
      e.preventDefault();
      handleSend();
    }
  };

  const handleChange = (value: string) => {
    setText(value);
    setShowSlash(
      value === "/" || (value.startsWith("/") && !value.includes(" "))
    );
  };

  const handleCommandSelect = (name: string) => {
    setText("/" + name + " ");
    setShowSlash(false);
    inputRef.current?.focus();
  };

  const handlePaste = (e: ClipboardEvent<HTMLTextAreaElement>) => {
    const items = e.clipboardData?.files;
    if (items && items.length > 0) {
      const hasImage = Array.from(items).some(f => f.type.startsWith("image/"));
      if (hasImage) {
        e.preventDefault();
        addFiles(items);
      }
    }
  };

  const handleDragOver = (e: DragEvent) => {
    e.preventDefault();
    setDragOver(true);
  };

  const handleDragLeave = (e: DragEvent) => {
    e.preventDefault();
    setDragOver(false);
  };

  const handleDrop = (e: DragEvent) => {
    e.preventDefault();
    setDragOver(false);
    const files = e.dataTransfer?.files;
    if (files && files.length > 0) {
      addFiles(files);
    }
  };

  const handleFileChange = (e: ChangeEvent<HTMLInputElement>) => {
    const files = e.target.files;
    if (files && files.length > 0) {
      addFiles(files);
    }
    // reset so same file can be selected again
    e.target.value = "";
  };

  const canSend =
    !disabled &&
    !uploading &&
    (text.trim().length > 0 || attachments.length > 0);

  return (
    <div
      className={`relative px-4 py-3 border-t border-gray-200/60 bg-white/30 backdrop-blur-sm
        ${dragOver ? "ring-2 ring-blue-400 ring-inset bg-blue-50/30" : ""}`}
      onDragOver={handleDragOver}
      onDragLeave={handleDragLeave}
      onDrop={handleDrop}
    >
      {isProcessing && (
        <div className="absolute top-0 left-0 right-0 h-0.5 bg-blue-500/30 overflow-hidden">
          <div className="h-full w-1/3 bg-blue-500 animate-[slide_1.5s_ease-in-out_infinite]" />
        </div>
      )}
      {showSlash && state.commands.length > 0 && (
        <SlashMenu
          commands={state.commands}
          filter={text.slice(1)}
          onSelect={handleCommandSelect}
        />
      )}

      {/* Attachment preview strip */}
      {(attachments.length > 0 || uploading) && (
        <div className="flex items-center gap-2 mb-2 overflow-x-auto scrollbar-hide">
          {attachments.map(att =>
            att.previewUrl ? (
              <div
                key={att.id}
                className="relative group w-16 h-16 rounded-lg overflow-hidden flex-shrink-0 border border-gray-200/80"
              >
                <img
                  src={att.previewUrl}
                  alt={att.name}
                  className="w-full h-full object-cover"
                />
                <button
                  className="absolute top-0.5 right-0.5 hidden group-hover:flex items-center justify-center
                             w-5 h-5 rounded-full bg-black/60 text-white hover:bg-black/80 transition-colors"
                  onClick={() => removeAttachment(att.id)}
                >
                  <X size={12} />
                </button>
              </div>
            ) : (
              <div
                key={att.id}
                className="relative group flex items-center gap-1.5 px-2.5 py-1.5 rounded-lg
                           border border-gray-200/80 bg-gray-50 flex-shrink-0 max-w-[200px]"
              >
                {att.mimeType?.startsWith("image/") ? (
                  <Image size={14} className="text-blue-500 flex-shrink-0" />
                ) : (
                  <FileText size={14} className="text-gray-400 flex-shrink-0" />
                )}
                <span
                  className="text-xs text-gray-600 truncate"
                  title={att.filePath}
                >
                  {att.name}
                </span>
                <button
                  className="hidden group-hover:flex items-center justify-center
                             w-4 h-4 rounded-full bg-black/60 text-white hover:bg-black/80
                             transition-colors flex-shrink-0"
                  onClick={() => removeAttachment(att.id)}
                >
                  <X size={10} />
                </button>
              </div>
            )
          )}
          {uploading && (
            <div className="flex items-center gap-1 px-2 py-1 text-xs text-gray-400">
              <Loader2 size={12} className="animate-spin" />
              <span>上传中...</span>
            </div>
          )}
        </div>
      )}

      {queuedPrompts.length > 0 && (
        <div className="mb-2 rounded-lg border border-amber-200 bg-amber-50/70 px-2.5 py-2">
          <div className="flex items-center justify-between text-[11px] text-amber-700 mb-1.5">
            <span>队列中 {queueSize} 条消息</span>
          </div>
          <div className="space-y-1 max-h-24 overflow-y-auto">
            {queuedPrompts.map(item => (
              <div
                key={item.id}
                className="flex items-center gap-2 rounded border border-amber-200/80 bg-white/70 px-2 py-1"
              >
                <span className="text-xs text-gray-700 truncate flex-1 min-w-0">
                  {item.text || "[仅附件]"}
                </span>
                <button
                  className="text-[11px] text-gray-400 hover:text-gray-600"
                  onClick={() => onDropQueuedPrompt(item.id)}
                  title="移除队列消息"
                >
                  移除
                </button>
              </div>
            ))}
          </div>
        </div>
      )}

      <div className="flex items-end gap-2">
        <div className="flex-1 flex items-end gap-1.5">
          <button
            className="flex items-center justify-center w-9 h-[40px] rounded-lg text-gray-400
                       hover:text-gray-600 hover:bg-gray-100/60 transition-colors
                       disabled:opacity-30 disabled:cursor-not-allowed"
            onClick={() => fileInputRef.current?.click()}
            disabled={disabled || uploading || attachments.length >= MAX_ATTACHMENTS}
            title="添加附件"
          >
            <Paperclip size={18} />
          </button>
          <textarea
            ref={inputRef}
            className="flex-1 resize-none rounded-xl border border-gray-200/80 bg-white/80 px-4 py-2.5
                       text-sm text-gray-700 placeholder-gray-400
                       outline-none focus:border-gray-300 focus:shadow-sm transition-all
                       min-h-[40px] max-h-[160px] overflow-y-hidden"
            value={text}
            onChange={e => handleChange(e.target.value)}
            onKeyDown={handleKeyDown}
            onPaste={handlePaste}
            placeholder={
              disabled
                ? "正在连接..."
                : "输入消息… (Enter 发送)"
            }
            disabled={disabled}
            rows={1}
          />
        </div>
        {isProcessing ? (
          <div className="flex items-center gap-2 flex-shrink-0">
            <button
              className="flex items-center gap-1.5 px-4 py-2.5 rounded-xl text-sm font-medium
                         bg-gray-800 text-white whitespace-nowrap flex-shrink-0
                         hover:bg-gray-700 transition-colors
                         disabled:opacity-40 disabled:cursor-not-allowed"
              onClick={handleSend}
              disabled={!canSend}
            >
              <Send size={14} className="flex-shrink-0" />
              发送到队列
            </button>
            <button
              className="flex items-center gap-1.5 px-4 py-2.5 rounded-xl text-sm font-medium
                         bg-red-50 text-red-600 border border-red-200 whitespace-nowrap flex-shrink-0
                         hover:bg-red-100 transition-colors"
              onClick={onCancel}
            >
              <Square size={14} className="flex-shrink-0" />
              停止
            </button>
          </div>
        ) : (
          <button
            className="flex items-center gap-1.5 px-4 py-2.5 rounded-xl text-sm font-medium
                       bg-gray-800 text-white
                       hover:bg-gray-700 transition-colors
                       disabled:opacity-40 disabled:cursor-not-allowed"
            onClick={handleSend}
            disabled={!canSend}
          >
            <Send size={14} />
            发送
          </button>
        )}
      </div>

      <input
        ref={fileInputRef}
        type="file"
        multiple
        onChange={handleFileChange}
        className="hidden"
      />
    </div>
  );
}
