import { message } from 'antd';
import hljs from 'highlight.js';
import { Download, FileBox, Loader2, RefreshCw, Copy, WrapText, Check } from 'lucide-react';
import { useState, useCallback, useEffect, useMemo } from 'react';

import 'highlight.js/styles/github.css';
import { ImageRenderer } from './renderers/ImageRenderer';
import request from '../../lib/request';
import { downloadWorkspaceFile, getDefaultRuntime } from '../../lib/utils/workspaceApi';

import type { OpenFile } from '../../types/coding';

interface EditorAreaProps {
  openFiles: OpenFile[];
  activeFilePath: string | null;
  onSelectFile: (path: string) => void;
  onCloseFile: (path: string) => void;
  onRefreshFile?: (path: string) => void;
}

const IMAGE_EXTENSIONS = new Set(['png', 'jpg', 'jpeg', 'gif', 'webp']);
const PDF_EXTENSIONS = new Set(['pdf']);
const BINARY_DOWNLOAD_EXTENSIONS = new Set([
  'pptx',
  'ppt',
  'docx',
  'doc',
  'xlsx',
  'xls',
  'zip',
  'tar',
  'gz',
  'mp4',
  'mov',
  'mp3',
  'wav',
]);

function getExt(fileName: string): string {
  return fileName.split('.').pop()?.toLowerCase() ?? '';
}

const EXT_LABELS: Record<string, string> = {
  doc: 'Word',
  docx: 'Word',
  gz: 'Archive',
  mov: 'Video',
  mp3: 'Audio',
  mp4: 'Video',
  ppt: 'PowerPoint',
  pptx: 'PowerPoint',
  tar: 'Archive',
  wav: 'Audio',
  xls: 'Excel',
  xlsx: 'Excel',
  zip: 'Archive',
};

const LANG_MAP: Record<string, string> = {
  css: 'css',
  go: 'go',
  html: 'xml',
  java: 'java',
  javascript: 'javascript',
  json: 'json',
  less: 'less',
  markdown: 'markdown',
  plaintext: 'plaintext',
  python: 'python',
  rust: 'rust',
  scss: 'scss',
  shell: 'bash',
  sql: 'sql',
  toml: 'ini',
  typescript: 'typescript',
  xml: 'xml',
  yaml: 'yaml',
};

function escapeHtml(text: string): string {
  return text.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
}

// ===== PDF Preview =====

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
      .get('/workspace/download', {
        params,
        responseType: 'blob',
        timeout: 60000,
      })
      .then((resp: unknown) => {
        if (cancelled) return;
        const blob = resp instanceof Blob ? resp : new Blob([resp as BlobPart]);
        setBlobUrl(URL.createObjectURL(blob));
      })
      .catch((e: Error) => {
        if (!cancelled) setError(e.message ?? 'PDF 加载失败');
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });

    return () => {
      cancelled = true;
      setBlobUrl((prev) => {
        if (prev) URL.revokeObjectURL(prev);
        return null;
      });
    };
  }, [file.path]);

  if (loading) {
    return (
      <div className="flex items-center justify-center h-full text-gray-400 text-sm gap-2">
        <Loader2 className="animate-spin" size={16} />
        加载 PDF...
      </div>
    );
  }
  if (error || !blobUrl) {
    return (
      <div className="flex items-center justify-center h-full text-sm text-gray-400">
        PDF 预览失败：{error ?? '未知错误'}
      </div>
    );
  }
  return <iframe className="w-full h-full border-none" src={blobUrl} title="PDF Preview" />;
}

// ===== Binary File Placeholder =====

function BinaryFilePlaceholder({ file }: { file: OpenFile }) {
  const ext = getExt(file.fileName);
  const label = EXT_LABELS[ext] ?? ext.toUpperCase();
  const handleDownload = () => downloadWorkspaceFile(file.path, file.fileName);

  return (
    <div className="flex items-center justify-center h-full p-6">
      <div className="text-center space-y-3">
        <FileBox className="mx-auto text-gray-300" size={48} />
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

// ===== Syntax Highlighted Code =====

const CODE_FONT = "'Menlo', 'Monaco', 'Courier New', monospace";
const CODE_FONT_SIZE = '13px';
const CODE_LINE_HEIGHT = '20px';

function SyntaxHighlightedCode({
  content,
  language,
  wordWrap,
}: {
  content: string;
  language: string;
  wordWrap: boolean;
}) {
  const highlighted = useMemo(() => {
    const lang = LANG_MAP[language] || language;
    try {
      if (lang && lang !== 'plaintext' && hljs.getLanguage(lang)) {
        return hljs.highlight(content, { language: lang }).value;
      }
      return hljs.highlightAuto(content).value;
    } catch {
      return escapeHtml(content);
    }
  }, [content, language]);

  const lineCount = content.split('\n').length;

  return (
    <div className="flex-1 overflow-auto bg-white">
      <div className="flex min-h-full">
        {/* Line numbers */}
        <div
          className="flex-shrink-0 py-3 pr-3 pl-4 text-right select-none border-r border-gray-100 sticky left-0 bg-white z-10"
          style={{
            fontFamily: CODE_FONT,
            fontSize: CODE_FONT_SIZE,
            lineHeight: CODE_LINE_HEIGHT,
          }}
        >
          {Array.from({ length: lineCount }, (_, i) => (
            <div className="text-gray-300" key={i}>
              {i + 1}
            </div>
          ))}
        </div>
        {/* Code content */}
        <pre
          className="flex-1 py-3 pl-5 pr-4 m-0 bg-white"
          style={{
            fontFamily: CODE_FONT,
            fontSize: CODE_FONT_SIZE,
            lineHeight: CODE_LINE_HEIGHT,
            whiteSpace: wordWrap ? 'pre-wrap' : 'pre',
            wordBreak: wordWrap ? 'break-all' : 'normal',
          }}
        >
          <code
            className="hljs"
            dangerouslySetInnerHTML={{ __html: highlighted }}
            style={{ background: 'transparent', padding: 0 }}
          />
        </pre>
      </div>
    </div>
  );
}

// ===== Code Header with Action Buttons =====

function CodeHeader({
  copySuccess,
  fileName,
  onCopy,
  onDownload,
  onRefresh,
  onToggleWrap,
  wordWrap,
}: {
  fileName: string;
  onRefresh?: () => void;
  onCopy: () => void;
  wordWrap: boolean;
  onToggleWrap: () => void;
  onDownload: () => void;
  copySuccess: boolean;
}) {
  const btnCls =
    'w-7 h-7 flex items-center justify-center rounded-md text-gray-400 hover:text-gray-600 hover:bg-gray-100 transition-colors';

  return (
    <div className="flex items-center justify-between px-4 py-2 border-b border-gray-200/60 bg-white flex-shrink-0">
      <span className="text-sm text-gray-600 font-medium truncate">{fileName}</span>
      <div className="flex items-center gap-0.5">
        {onRefresh && (
          <button className={btnCls} onClick={onRefresh} title="刷新文件">
            <RefreshCw size={14} />
          </button>
        )}
        <button className={btnCls} onClick={onCopy} title={copySuccess ? '已复制' : '复制代码'}>
          {copySuccess ? <Check className="text-green-500" size={14} /> : <Copy size={14} />}
        </button>
        <button
          className={`${btnCls} ${wordWrap ? 'text-blue-500 bg-blue-50' : ''}`}
          onClick={onToggleWrap}
          title={wordWrap ? '取消换行' : '自动换行'}
        >
          <WrapText size={14} />
        </button>
        <button className={btnCls} onClick={onDownload} title="下载文件">
          <Download size={14} />
        </button>
      </div>
    </div>
  );
}

// ===== Main Component =====

export function EditorArea({ activeFilePath, onRefreshFile, openFiles }: EditorAreaProps) {
  const [wordWrap, setWordWrap] = useState(false);
  const [copySuccess, setCopySuccess] = useState(false);

  const activeFile = openFiles.find((f) => f.path === activeFilePath) ?? null;

  const handleCopy = useCallback(async () => {
    if (!activeFile) return;
    try {
      await navigator.clipboard.writeText(activeFile.content);
      setCopySuccess(true);
      message.success('已复制到剪贴板');
      setTimeout(() => setCopySuccess(false), 2000);
    } catch {
      message.error('复制失败');
    }
  }, [activeFile]);

  const handleRefresh = useCallback(() => {
    if (activeFile && onRefreshFile) {
      onRefreshFile(activeFile.path);
    }
  }, [activeFile, onRefreshFile]);

  const handleDownload = useCallback(() => {
    if (activeFile) {
      downloadWorkspaceFile(activeFile.path, activeFile.fileName);
    }
  }, [activeFile]);

  if (openFiles.length === 0 || !activeFile) {
    return (
      <div className="flex-1 flex items-center justify-center bg-gray-50/50 text-gray-400 text-sm">
        从左侧文件树选择文件查看
      </div>
    );
  }

  const ext = getExt(activeFile.fileName);
  const isImage = IMAGE_EXTENSIONS.has(ext);
  const isPdf = PDF_EXTENSIONS.has(ext);
  const isBinaryDownload = BINARY_DOWNLOAD_EXTENSIONS.has(ext);

  return (
    <div className="flex-1 flex flex-col min-w-0 min-h-0 overflow-hidden">
      {/* File header with action buttons */}
      <CodeHeader
        copySuccess={copySuccess}
        fileName={activeFile.fileName}
        onCopy={handleCopy}
        onDownload={handleDownload}
        onRefresh={onRefreshFile ? handleRefresh : undefined}
        onToggleWrap={() => setWordWrap((v) => !v)}
        wordWrap={wordWrap}
      />

      {/* Content */}
      <div className="flex-1 min-h-0 relative">
        {isPdf ? (
          <div className="absolute inset-0">
            <PdfPreview file={activeFile} />
          </div>
        ) : isImage ? (
          <div className="absolute inset-0">
            <ImageRenderer content={activeFile.content} path={activeFile.path} />
          </div>
        ) : isBinaryDownload ? (
          <div className="absolute inset-0">
            <BinaryFilePlaceholder file={activeFile} />
          </div>
        ) : (
          <div className="absolute inset-0 flex">
            <SyntaxHighlightedCode
              content={activeFile.content}
              language={activeFile.language}
              wordWrap={wordWrap}
            />
          </div>
        )}
      </div>
    </div>
  );
}
