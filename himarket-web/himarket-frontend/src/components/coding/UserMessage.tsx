import { Image, FileText } from 'lucide-react';

import type { Attachment } from '../../types/coding-protocol';

interface UserMessageProps {
  text: string;
  attachments?: Attachment[];
}

export function UserMessage({ attachments, text }: UserMessageProps) {
  return (
    <div className="flex justify-end">
      <div
        className="max-w-[80%] rounded-2xl rounded-tr-md px-4 py-2.5
                      bg-gray-800 text-white text-[14.5px] leading-relaxed tracking-[-0.01em]"
      >
        {attachments && attachments.length > 0 && (
          <div className="flex flex-wrap gap-1.5 mb-2">
            {attachments.map((att) =>
              att.previewUrl ? (
                <img
                  alt={att.name}
                  className="w-20 h-20 rounded-lg object-cover"
                  key={att.id}
                  src={att.previewUrl}
                />
              ) : (
                <div
                  className="flex items-center gap-1.5 px-2 py-1 rounded-md
                             bg-white/10 text-white/80 text-xs max-w-[180px]"
                  key={att.id}
                  title={att.filePath}
                >
                  {att.mimeType?.startsWith('image/') ? (
                    <Image className="flex-shrink-0" size={12} />
                  ) : (
                    <FileText className="flex-shrink-0" size={12} />
                  )}
                  <span className="truncate">{att.name}</span>
                </div>
              ),
            )}
          </div>
        )}
        {text && <span>{text}</span>}
      </div>
    </div>
  );
}
