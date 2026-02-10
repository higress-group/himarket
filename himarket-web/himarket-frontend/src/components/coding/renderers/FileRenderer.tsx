import { FileBox } from "lucide-react";

interface FileRendererProps {
  fileName: string;
  path: string;
}

const EXT_LABELS: Record<string, string> = {
  ".pptx": "PowerPoint",
  ".ppt": "PowerPoint",
  ".docx": "Word",
  ".doc": "Word",
  ".xlsx": "Excel",
  ".xls": "Excel",
  ".pdf": "PDF",
  ".zip": "Archive",
  ".tar": "Archive",
  ".gz": "Archive",
  ".mp4": "Video",
  ".mov": "Video",
  ".mp3": "Audio",
  ".wav": "Audio",
};

function getExtLabel(fileName: string): string {
  const lastDot = fileName.lastIndexOf(".");
  if (lastDot === -1) return "File";
  const ext = fileName.slice(lastDot).toLowerCase();
  return EXT_LABELS[ext] ?? ext.slice(1).toUpperCase();
}

export function FileRenderer({ fileName, path }: FileRendererProps) {
  const label = getExtLabel(fileName);

  return (
    <div className="flex items-center justify-center h-full p-6">
      <div className="text-center space-y-3">
        <FileBox size={48} className="mx-auto text-gray-300" />
        <div>
          <div className="text-sm font-medium text-gray-700">{fileName}</div>
          <div className="text-xs text-gray-400 mt-1">{label} file</div>
        </div>
        <div className="text-[11px] text-gray-400 font-mono max-w-[300px] truncate">
          {path}
        </div>
      </div>
    </div>
  );
}
