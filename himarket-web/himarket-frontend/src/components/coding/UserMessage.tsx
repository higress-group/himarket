import type { Attachment } from "../../types/coding-protocol";
import { Image, FileText } from "lucide-react";

interface UserMessageProps {
  text: string;
  attachments?: Attachment[];
}

export function UserMessage({ text, attachments }: UserMessageProps) {
  return (
    <div className="flex justify-end">
      <div
        className="max-w-[80%] rounded-2xl rounded-tr-md px-4 py-2.5
                      bg-gray-800 text-white text-[14.5px] leading-relaxed tracking-[-0.01em]"
      >
        {attachments && attachments.length > 0 && (
          <div className="flex flex-wrap gap-1.5 mb-2">
            {attachments.map(att =>
              att.previewUrl ? (
                <img
                  key={att.id}
                  src={att.previewUrl}
                  alt={att.name}
                  className="w-20 h-20 rounded-lg object-cover"
                />
              ) : (
                <div
                  key={att.id}
                  className="flex items-center gap-1.5 px-2 py-1 rounded-md
                             bg-white/10 text-white/80 text-xs max-w-[180px]"
                  title={att.filePath}
                >
                  {att.mimeType?.startsWith("image/") ? (
                    <Image size={12} className="flex-shrink-0" />
                  ) : (
                    <FileText size={12} className="flex-shrink-0" />
                  )}
                  <span className="truncate">{att.name}</span>
                </div>
              )
            )}
          </div>
        )}
        {text && <span>{text}</span>}
      </div>
    </div>
  );
}
