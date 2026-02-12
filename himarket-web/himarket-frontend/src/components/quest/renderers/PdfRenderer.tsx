import { useMemo, useEffect } from "react";

interface PdfRendererProps {
  content: string;
}

export function PdfRenderer({ content }: PdfRendererProps) {
  const blobUrl = useMemo(() => {
    const binaryString = atob(content);
    const bytes = new Uint8Array(binaryString.length);
    for (let i = 0; i < binaryString.length; i++) {
      bytes[i] = binaryString.charCodeAt(i);
    }
    const blob = new Blob([bytes], { type: "application/pdf" });
    return URL.createObjectURL(blob);
  }, [content]);

  useEffect(() => {
    return () => URL.revokeObjectURL(blobUrl);
  }, [blobUrl]);

  return (
    <iframe
      src={blobUrl}
      className="w-full h-full border-none"
      title="PDF Preview"
    />
  );
}
