import { FileBox, Download, Loader2 } from 'lucide-react';
import { useState } from 'react';

import { downloadWorkspaceFile } from '../../../lib/utils/workspaceApi';

interface FileRendererProps {
  fileName: string;
  path: string;
}

const EXT_LABELS: Record<string, string> = {
  '.doc': 'Word',
  '.docx': 'Word',
  '.gz': 'Archive',
  '.mov': 'Video',
  '.mp3': 'Audio',
  '.mp4': 'Video',
  '.pdf': 'PDF',
  '.ppt': 'PowerPoint',
  '.pptx': 'PowerPoint',
  '.tar': 'Archive',
  '.wav': 'Audio',
  '.xls': 'Excel',
  '.xlsx': 'Excel',
  '.zip': 'Archive',
};

function getExtLabel(fileName: string): string {
  const lastDot = fileName.lastIndexOf('.');
  if (lastDot === -1) return 'File';
  const ext = fileName.slice(lastDot).toLowerCase();
  return EXT_LABELS[ext] ?? ext.slice(1).toUpperCase();
}

export function FileRenderer({ fileName, path }: FileRendererProps) {
  const label = getExtLabel(fileName);
  const [downloading, setDownloading] = useState(false);

  const handleDownload = async () => {
    setDownloading(true);
    try {
      await downloadWorkspaceFile(path, fileName);
    } finally {
      setDownloading(false);
    }
  };

  return (
    <div className="flex items-center justify-center h-full p-6">
      <div className="text-center space-y-3">
        <FileBox className="mx-auto text-gray-300" size={48} />
        <div>
          <div className="text-sm font-medium text-gray-700">{fileName}</div>
          <div className="text-xs text-gray-400 mt-1">{label} file</div>
        </div>
        <div className="text-[11px] text-gray-400 font-mono max-w-[300px] truncate">{path}</div>
        <button
          className="inline-flex items-center gap-1.5 px-4 py-2 text-xs font-medium
                     rounded-md border border-gray-200 text-gray-600
                     hover:bg-gray-50 hover:border-gray-300 transition-colors
                     disabled:opacity-50 disabled:cursor-not-allowed"
          disabled={downloading}
          onClick={handleDownload}
        >
          {downloading ? <Loader2 className="animate-spin" size={14} /> : <Download size={14} />}
          下载文件
        </button>
      </div>
    </div>
  );
}
