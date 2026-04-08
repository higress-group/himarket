import { Download, Maximize2, Loader2, RefreshCcw, AlertCircle } from 'lucide-react';
import { useEffect, useState } from 'react';

import { ArtifactRenderer } from './renderers/ArtifactRenderer';
import { useCodingDispatch } from '../../context/CodingSessionContext';
import { fetchArtifactContent } from '../../lib/utils/workspaceApi';

import type { Artifact } from '../../types/artifact';

interface ArtifactPreviewProps {
  artifact: Artifact;
}

export function ArtifactPreview({ artifact }: ArtifactPreviewProps) {
  const dispatch = useCodingDispatch();
  const [error, setError] = useState<string | null>(null);
  const [retryToken, setRetryToken] = useState(0);

  // Reset error when switching to a different artifact
  useEffect(() => {
    setError(null);
  }, [artifact.id]);

  const resolveDownloadName = (name: string) => {
    if (artifact.type !== 'pdf') return name;
    if (name.toLowerCase().endsWith('.pdf')) return name;
    const dot = name.lastIndexOf('.');
    const base = dot > 0 ? name.slice(0, dot) : name;
    return `${base}.pdf`;
  };

  const decodeBase64ToBlob = (content: string, mime: string) => {
    const binaryString = atob(content);
    const bytes = new Uint8Array(binaryString.length);
    for (let i = 0; i < binaryString.length; i++) {
      bytes[i] = binaryString.charCodeAt(i);
    }
    return new Blob([bytes], { type: mime });
  };

  // Fetch full content from API whenever content is missing (null).
  // Content is reset to null each time the file is modified (see
  // applyArtifactDetection), so this always fetches the latest version.
  useEffect(() => {
    if (artifact.content !== null) return;
    if (artifact.type === 'file') return;

    let cancelled = false;
    setError(null);

    const load = async () => {
      const result = await fetchArtifactContent(artifact.path);
      if (cancelled) return;

      if (result.content !== null) {
        setError(null);
        dispatch({
          artifactId: artifact.id,
          content: result.content,
          type: 'UPDATE_ARTIFACT_CONTENT',
        });
      } else {
        setError(result.error?.message ?? '加载预览失败');
      }
    };

    load().catch(() => {
      if (!cancelled) {
        setError('加载预览失败');
      }
    });

    return () => {
      cancelled = true;
    };
  }, [
    artifact.id,
    artifact.content,
    artifact.type,
    artifact.path,
    artifact.updatedAt,
    dispatch,
    retryToken,
  ]);

  const hasContent = artifact.content !== null;
  const isLoading = !hasContent && artifact.type !== 'file' && !error;

  const handleRetry = () => {
    setError(null);
    setRetryToken((v) => v + 1);
  };

  const handleDownload = () => {
    if (!artifact.content) return;

    let blob: Blob;
    if (artifact.type === 'pdf' || artifact.type === 'image') {
      const mime = artifact.type === 'pdf' ? 'application/pdf' : 'application/octet-stream';
      blob = decodeBase64ToBlob(artifact.content, mime);
    } else {
      blob = new Blob([artifact.content], { type: 'text/plain' });
    }

    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = resolveDownloadName(artifact.fileName);
    a.click();
    URL.revokeObjectURL(url);
  };

  const handleOpenTab = () => {
    if (!artifact.content) return;

    if (artifact.type === 'pdf') {
      const blob = decodeBase64ToBlob(artifact.content, 'application/pdf');
      const url = URL.createObjectURL(blob);
      window.open(url, '_blank');
      return;
    }

    const mimeMap: Record<string, string> = {
      html: 'text/html',
      image: 'text/plain',
      markdown: 'text/plain',
      svg: 'image/svg+xml',
    };
    const blob = new Blob([artifact.content], {
      type: mimeMap[artifact.type] ?? 'text/plain',
    });
    const url = URL.createObjectURL(blob);
    window.open(url, '_blank');
  };

  return (
    <div className="flex flex-col h-full">
      {hasContent && (
        <div className="flex items-center justify-end px-3 py-1.5 border-b border-gray-200/60">
          <div className="flex items-center gap-1">
            <button
              className="w-7 h-7 flex items-center justify-center rounded text-gray-400
                         hover:text-gray-600 hover:bg-gray-100 transition-colors"
              onClick={handleDownload}
              title="Download"
            >
              <Download size={14} />
            </button>
            <button
              className="w-7 h-7 flex items-center justify-center rounded text-gray-400
                         hover:text-gray-600 hover:bg-gray-100 transition-colors"
              onClick={handleOpenTab}
              title="Open in new tab"
            >
              <Maximize2 size={14} />
            </button>
          </div>
        </div>
      )}

      <div className="flex-1 min-h-0 overflow-hidden">
        {isLoading ? (
          <div className="flex items-center justify-center h-full text-gray-400 text-sm gap-2">
            <Loader2 className="animate-spin" size={16} />
            Loading preview...
          </div>
        ) : error ? (
          <div className="flex h-full items-center justify-center p-6">
            <div className="max-w-md text-center space-y-3">
              <div className="flex items-center justify-center gap-2 text-amber-600">
                <AlertCircle size={16} />
                <span className="text-sm font-medium">预览失败</span>
              </div>
              <div className="text-xs text-gray-500 break-words">{error}</div>
              <div className="flex items-center justify-center gap-2">
                <button
                  className="inline-flex items-center gap-1.5 px-3 py-1.5 text-xs
                             rounded border border-gray-200 text-gray-600 hover:bg-gray-50"
                  onClick={handleRetry}
                >
                  <RefreshCcw size={12} />
                  重试
                </button>
              </div>
            </div>
          </div>
        ) : (
          <ArtifactRenderer
            content={artifact.content}
            fileName={artifact.fileName}
            path={artifact.path}
            type={artifact.type}
          />
        )}
      </div>
    </div>
  );
}
