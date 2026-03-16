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
import { useCodingState, useActiveCodingSession } from "../../context/CodingSessionContext";
import { SlashMenu } from "./SlashMenu";
import { FileMentionMenu } from "./FileMentionMenu";
import {
  uploadFileToWorkspace,
  fetchDirectoryTree,
} from "../../lib/utils/workspaceApi";
import {
  flattenFileTree,
  filterFiles,
  type FlatFileItem,
} from "../../lib/utils/fileTreeUtils";
import type { Attachment, FilePathAttachment } from "../../types/coding-protocol";
import type { QueuedPromptItem } from "../../context/CodingSessionContext";

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

interface CodingInputProps {
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
  variant?: "default" | "welcome";
  /** Extra elements rendered in the welcome toolbar, after the attachment button */
  toolbarExtra?: React.ReactNode;
}

export function CodingInput({
  onSend,
  onSendQueued,
  onDropQueuedPrompt,
  onCancel,
  isProcessing,
  queueSize,
  queuedPrompts,
  disabled,
  variant = "default",
  toolbarExtra,
}: CodingInputProps) {
  const [text, setText] = useState("");
  const [showSlash, setShowSlash] = useState(false);
  const [showMentionMenu, setShowMentionMenu] = useState(false);
  const [mentionFilter, setMentionFilter] = useState("");
  const [flatFiles, setFlatFiles] = useState<FlatFileItem[]>([]);
  const [filesLoading, setFilesLoading] = useState(false);
  const [attachments, setAttachments] = useState<Attachment[]>([]);
  const [uploading, setUploading] = useState(false);
  const [dragOver, setDragOver] = useState(false);
  const [mentionedFiles, setMentionedFiles] = useState<FlatFileItem[]>([]);
  const inputRef = useRef<HTMLTextAreaElement>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);
  const state = useCodingState();
  const activeQuest = useActiveCodingSession();

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

  // Load file tree on first "@" trigger
  const loadFileTree = useCallback(async () => {
    if (flatFiles.length > 0 || !activeQuest?.cwd) return;

    setFilesLoading(true);
    try {
      const tree = await fetchDirectoryTree(activeQuest.cwd, 10);
      const flattened = flattenFileTree(tree ?? [], activeQuest.cwd);
      setFlatFiles(flattened);
    } catch {
      setFlatFiles([]);
    } finally {
      setFilesLoading(false);
    }
  }, [flatFiles.length, activeQuest?.cwd]);

  const removeMention = useCallback((path: string) => {
    setMentionedFiles(prev => prev.filter(f => f.path !== path));
  }, []);

  const handleSend = useCallback(() => {
    const trimmed = text.trim();
    if (!trimmed && attachments.length === 0 && mentionedFiles.length === 0) return;

    // Convert mentioned files to resource_link attachments
    const mentionAttachments: FilePathAttachment[] = mentionedFiles.map(file => ({
      id: nextAttId(),
      kind: "file_path" as const,
      name: file.name,
      filePath: file.path,
      mimeType: file.extension ? `text/${file.extension}` : "text/plain",
    }));

    const allAttachments = [...mentionAttachments, ...attachments];

    const result = onSend(
      trimmed,
      allAttachments.length > 0 ? allAttachments : undefined
    );
    if (result.queued) {
      onSendQueued?.(result.queuedPromptId);
    }
    setText("");
    setShowSlash(false);
    setShowMentionMenu(false);
    setAttachments([]);
    setMentionedFiles([]);
  }, [text, attachments, mentionedFiles, onSend, onSendQueued]);

  const handleKeyDown = (e: KeyboardEvent<HTMLTextAreaElement>) => {
    // Let SlashMenu or FileMentionMenu handle navigation when open
    if (
      (showSlash || showMentionMenu) &&
      ["ArrowDown", "ArrowUp", "Enter", "Tab"].includes(e.key)
    ) {
      return;
    }

    if (e.key === "Enter" && !e.shiftKey) {
      e.preventDefault();
      handleSend();
    } else if (e.key === "Escape") {
      if (showSlash) {
        e.preventDefault();
        setShowSlash(false);
      } else if (showMentionMenu) {
        e.preventDefault();
        setShowMentionMenu(false);
        setMentionFilter("");
      }
    }
  };

  const handleChange = (value: string) => {
    setText(value);

    // Check for slash command (only at start)
    const isSlashCommand = value === "/" || (value.startsWith("/") && !value.includes(" "));
    setShowSlash(isSlashCommand);

    // Check for "@" mention (at end of input)
    const mentionMatch = value.match(/@(\S*)$/);
    if (mentionMatch && !isSlashCommand) {
      setShowMentionMenu(true);
      setMentionFilter(mentionMatch[1]);
      loadFileTree();
    } else {
      setShowMentionMenu(false);
      setMentionFilter("");
    }
  };

  const handleCommandSelect = (name: string) => {
    setText("/" + name + " ");
    setShowSlash(false);
    inputRef.current?.focus();
  };

  const handleFileSelect = useCallback(
    (file: FlatFileItem) => {
      // Remove "@query" from text — the file chip provides the visual reference
      const newText = text.replace(/@\S*$/, "");
      setText(newText);
      setShowMentionMenu(false);
      setMentionFilter("");

      // Add to mentioned files if not already present
      setMentionedFiles(prev => {
        if (prev.some(f => f.path === file.path)) return prev;
        return [...prev, file];
      });

      inputRef.current?.focus();
    },
    [text]
  );

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
    (text.trim().length > 0 || attachments.length > 0 || mentionedFiles.length > 0);

  return (
    <div
      className={`relative ${
        variant === "welcome"
          ? "px-4 py-4"
          : "px-5 py-4 bg-white/60 backdrop-blur-md shadow-[0_-4px_16px_rgba(0,0,0,0.06)] border-t border-gray-100/80"
      } ${dragOver ? "ring-2 ring-blue-400 ring-inset bg-blue-50/30" : ""}`}
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
      {showMentionMenu && (
        <FileMentionMenu
          files={filterFiles(flatFiles, mentionFilter)}
          filter={mentionFilter}
          onSelect={handleFileSelect}
          loading={filesLoading}
        />
      )}

      {/* Mentioned file chips (from @ references) */}
      {mentionedFiles.length > 0 && (
        <div className="flex items-center gap-1.5 mb-2 flex-wrap">
          {mentionedFiles.map(file => (
            <span
              key={file.path}
              className="inline-flex items-center gap-1 pl-1.5 pr-1 py-0.5 rounded-md
                         bg-blue-50 border border-blue-200/80 text-blue-700 text-xs font-medium
                         transition-colors"
            >
              <FileText size={12} className="text-blue-500 flex-shrink-0" />
              <span className="truncate max-w-[160px]" title={file.relativePath}>
                {file.name}
              </span>
              <button
                className="ml-0.5 p-0.5 rounded text-blue-400 hover:text-blue-600 hover:bg-blue-100
                           transition-colors flex-shrink-0"
                onClick={() => removeMention(file.path)}
              >
                <X size={12} />
              </button>
            </span>
          ))}
        </div>
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

      {variant === "welcome" ? (
        /* Welcome 模式布局 */
        <>
          <textarea
            ref={inputRef}
            className="w-full resize-none rounded-xl border border-gray-200/80 bg-white/80 px-4 py-2.5
                       text-sm text-gray-700 placeholder-gray-400
                       outline-none focus:border-gray-300 focus:shadow-sm transition-all
                       min-h-[80px] max-h-[200px] overflow-y-hidden"
            value={text}
            onChange={e => handleChange(e.target.value)}
            onKeyDown={handleKeyDown}
            onPaste={handlePaste}
            placeholder={disabled ? "正在连接..." : "输入消息… (Enter 发送)"}
            disabled={disabled}
            rows={2}
          />
          <div className="flex items-center justify-between mt-2 pt-2 border-t border-gray-100">
            <div className="flex items-center gap-1">
              <button
                className="p-2 rounded-lg text-gray-400 hover:text-gray-600 hover:bg-gray-100/60 transition-colors
                           disabled:opacity-30 disabled:cursor-not-allowed"
                onClick={() => fileInputRef.current?.click()}
                disabled={disabled || uploading || attachments.length >= MAX_ATTACHMENTS}
                title="添加附件"
              >
                <Paperclip size={16} />
              </button>
              {toolbarExtra}
            </div>
            <button
              className="w-9 h-9 rounded-full bg-gray-800 text-white flex items-center justify-center
                         hover:bg-gray-700 transition-colors disabled:opacity-40 disabled:cursor-not-allowed"
              onClick={handleSend}
              disabled={!canSend}
            >
              <Send size={14} />
            </button>
          </div>
        </>
      ) : (
        /* Default 模式布局 */
        <div className="flex items-end gap-3">
          <div className="flex-1 flex items-end gap-1.5">
            <button
              className="flex items-center justify-center w-9 h-[44px] rounded-lg text-gray-400
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
              className="flex-1 resize-none rounded-2xl border border-gray-200 bg-white/90 px-5 py-3
                         text-sm text-gray-700 placeholder-gray-400
                         outline-none focus:border-gray-300 focus:shadow-md focus:ring-2 focus:ring-gray-100 transition-all
                         min-h-[44px] max-h-[160px] overflow-y-hidden"
              value={text}
              onChange={e => handleChange(e.target.value)}
              onKeyDown={handleKeyDown}
              onPaste={handlePaste}
              placeholder={disabled ? "正在连接..." : "输入消息… (Enter 发送)"}
              disabled={disabled}
              rows={1}
            />
          </div>
          {isProcessing ? (
            <div className="flex items-center gap-2 flex-shrink-0">
              <button
                className="flex items-center gap-1.5 px-4 py-2.5 rounded-2xl text-sm font-medium
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
                className="flex items-center gap-1.5 px-4 py-2.5 rounded-2xl text-sm font-medium
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
              className="flex items-center gap-1.5 px-4 py-2.5 rounded-2xl text-sm font-medium
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
      )}

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
