import { Image } from 'antd';

interface ImageRendererProps {
  content: string;
  path: string;
}

function inferMimeType(path: string): string {
  const ext = path.split('.').pop()?.toLowerCase();
  switch (ext) {
    case 'png':
      return 'image/png';
    case 'jpg':
    case 'jpeg':
      return 'image/jpeg';
    case 'gif':
      return 'image/gif';
    case 'webp':
      return 'image/webp';
    default:
      return 'image/png';
  }
}

export function ImageRenderer({ content, path }: ImageRendererProps) {
  // If the content looks like base64 (no HTML/text-like characters at the start)
  const isBase64 = /^[A-Za-z0-9+/=\s]+$/.test(content.slice(0, 100));

  if (!isBase64) {
    return (
      <div className="flex items-center justify-center h-full text-gray-400 text-sm">
        Binary image preview is not available yet
      </div>
    );
  }

  const mimeType = inferMimeType(path);
  const src = `data:${mimeType};base64,${content.replace(/\s/g, '')}`;

  return (
    <div className="flex items-center justify-center h-full p-4 overflow-auto">
      <Image
        alt={path}
        src={src}
        style={{ maxHeight: '100%', maxWidth: '100%', objectFit: 'contain' }}
      />
    </div>
  );
}
