import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';

interface PdfRendererProps {
  content: string;
}

export function PdfRenderer({ content }: PdfRendererProps) {
  const { t } = useTranslation('coding');
  const [blobUrl, setBlobUrl] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    try {
      const cleaned = content.replace(/[\s\r\n]/g, '');
      const binaryString = atob(cleaned);
      const bytes = new Uint8Array(binaryString.length);
      for (let i = 0; i < binaryString.length; i++) {
        bytes[i] = binaryString.charCodeAt(i);
      }
      const blob = new Blob([bytes], { type: 'application/pdf' });
      setBlobUrl(URL.createObjectURL(blob));
      setError(null);
    } catch (e) {
      setError(e instanceof Error ? e.message : t('editor.pdfDecodeFailed'));
      setBlobUrl(null);
    }

    return () => {
      if (blobUrl) URL.revokeObjectURL(blobUrl);
    };
  }, [content, blobUrl, t]);

  if (error || !blobUrl) {
    return (
      <div className="flex items-center justify-center h-full text-sm text-gray-400">
        {t('editor.pdfPreviewFailed', { error: error ?? t('editor.unknownError') })}
      </div>
    );
  }

  return <iframe className="w-full h-full border-none" src={blobUrl} title="PDF Preview" />;
}
