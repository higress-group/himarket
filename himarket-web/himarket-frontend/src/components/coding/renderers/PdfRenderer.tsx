import { useMemo, useEffect, useState } from "react";

interface PdfRendererProps {
  content: string;
}

export function PdfRenderer({ content }: PdfRendererProps) {
  const [error, setError] = useState<string | null>(null);

  const blobUrl = useMemo(() => {
    try {
      const cleaned = content.replace(/[\s\r\n]/g, "");
      const binaryString = atob(cleaned);
      const bytes = new Uint8Array(binaryString.length);
      for (let i = 0; i < binaryString.length; i++) {
        bytes[i] = binaryString.charCodeAt(i);
      }
      const blob = new Blob([bytes], { type: "application/pdf" });
      return URL.createObjectURL(blob);
    } catch (e) {
      setError(e instanceof Error ? e.message : "PDF 解码失败");
      return null;
    }
  }, [content]);

  useEffect(() => {
    return () => { if (blobUrl) URL.revokeObjectURL(blobUrl); };
  }, [blobUrl]);

  if (error || !blobUrl) {
    return (
      <div className="flex items-center justify-center h-full text-sm text-gray-400">
        PDF 预览失败：{error ?? "未知错误"}
      </div>
    );
  }

  return (
    <iframe
      src={blobUrl}
      className="w-full h-full border-none"
      title="PDF Preview"
    />
  );
}
