import { useEffect } from "react";
import { Download, Maximize2, Loader2 } from "lucide-react";
import { ArtifactRenderer } from "./renderers/ArtifactRenderer";
import { useCodingDispatch } from "../../context/CodingSessionContext";
import { fetchArtifactContent } from "../../lib/utils/workspaceApi";
import type { Artifact } from "../../types/artifact";

interface ArtifactPreviewProps {
  artifact: Artifact;
}

export function ArtifactPreview({ artifact }: ArtifactPreviewProps) {
  const dispatch = useCodingDispatch();

  // Fetch full content from API whenever content is missing (null).
  // Content is reset to null each time the file is modified (see
  // applyArtifactDetection), so this always fetches the latest version.
  useEffect(() => {
    if (artifact.content !== null) return;
    if (artifact.type === "file") return;

    let cancelled = false;
    fetchArtifactContent(artifact.path)
      .then(content => {
        if (!cancelled && content !== null) {
          dispatch({
            type: "UPDATE_ARTIFACT_CONTENT",
            artifactId: artifact.id,
            content,
          });
        }
      })
      .catch(() => {
        // Silently fail — FileRenderer fallback will show
      });

    return () => {
      cancelled = true;
    };
  }, [artifact.id, artifact.content, artifact.type, artifact.path, artifact.updatedAt, dispatch]);

  const hasContent = artifact.content !== null;
  const isLoading = !hasContent && artifact.type !== "file";

  const handleDownload = () => {
    if (!artifact.content) return;

    let blob: Blob;
    if (artifact.type === "pdf" || artifact.type === "image") {
      const binaryString = atob(artifact.content);
      const bytes = new Uint8Array(binaryString.length);
      for (let i = 0; i < binaryString.length; i++) {
        bytes[i] = binaryString.charCodeAt(i);
      }
      const mime = artifact.type === "pdf" ? "application/pdf" : "application/octet-stream";
      blob = new Blob([bytes], { type: mime });
    } else {
      blob = new Blob([artifact.content], { type: "text/plain" });
    }

    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = artifact.fileName;
    a.click();
    URL.revokeObjectURL(url);
  };

  const handleOpenTab = () => {
    if (!artifact.content) return;

    if (artifact.type === "pdf") {
      const binaryString = atob(artifact.content);
      const bytes = new Uint8Array(binaryString.length);
      for (let i = 0; i < binaryString.length; i++) {
        bytes[i] = binaryString.charCodeAt(i);
      }
      const blob = new Blob([bytes], { type: "application/pdf" });
      const url = URL.createObjectURL(blob);
      window.open(url, "_blank");
      return;
    }

    const mimeMap: Record<string, string> = {
      html: "text/html",
      markdown: "text/plain",
      svg: "image/svg+xml",
      image: "text/plain",
    };
    const blob = new Blob([artifact.content], {
      type: mimeMap[artifact.type] ?? "text/plain",
    });
    const url = URL.createObjectURL(blob);
    window.open(url, "_blank");
  };

  return (
    <div className="flex flex-col h-full">
      {/* Action bar */}
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

      {/* Preview area */}
      <div className="flex-1 min-h-0 overflow-hidden">
        {isLoading ? (
          <div className="flex items-center justify-center h-full text-gray-400 text-sm gap-2">
            <Loader2 size={16} className="animate-spin" />
            Loading preview...
          </div>
        ) : (
          <ArtifactRenderer
            key={artifact.updatedAt}
            type={artifact.type}
            content={artifact.content}
            path={artifact.path}
            fileName={artifact.fileName}
          />
        )}
      </div>
    </div>
  );
}
