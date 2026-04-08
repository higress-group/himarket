import { FileText, FileCode, Image, Loader2 } from 'lucide-react';
import { useEffect, useState, useRef } from 'react';

import type { FlatFileItem } from '../../lib/utils/fileTreeUtils';

interface FileMentionMenuProps {
  files: FlatFileItem[];
  filter: string;
  onSelect: (file: FlatFileItem) => void;
  loading?: boolean;
}

// Map file extensions to icons
function getFileIcon(extension?: string) {
  if (!extension) return <FileText className="text-gray-400" size={14} />;

  const ext = extension.toLowerCase();

  // Images
  if (['png', 'jpg', 'jpeg', 'gif', 'svg', 'webp', 'ico'].includes(ext)) {
    return <Image className="text-blue-500" size={14} />;
  }

  // Code files
  if (
    [
      'ts',
      'tsx',
      'js',
      'jsx',
      'py',
      'java',
      'go',
      'rs',
      'c',
      'cpp',
      'h',
      'cs',
      'rb',
      'php',
      'swift',
      'kt',
    ].includes(ext)
  ) {
    return <FileCode className="text-green-500" size={14} />;
  }

  // Default
  return <FileText className="text-gray-400" size={14} />;
}

export function FileMentionMenu({ files, filter, loading, onSelect }: FileMentionMenuProps) {
  const [selectedIndex, setSelectedIndex] = useState(0);
  const menuRef = useRef<HTMLDivElement>(null);

  // Reset selection when filter changes
  useEffect(() => {
    setSelectedIndex(0);
  }, [filter]);

  // Keyboard navigation
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (files.length === 0) return;

      if (e.key === 'ArrowDown') {
        e.preventDefault();
        setSelectedIndex((prev) => (prev + 1) % files.length);
      } else if (e.key === 'ArrowUp') {
        e.preventDefault();
        setSelectedIndex((prev) => (prev - 1 + files.length) % files.length);
      } else if (e.key === 'Enter') {
        e.preventDefault();
        if (files[selectedIndex]) {
          onSelect(files[selectedIndex]);
        }
      }
    };

    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [files, selectedIndex, onSelect]);

  // Scroll selected item into view
  useEffect(() => {
    if (menuRef.current) {
      const selectedElement = menuRef.current.children[selectedIndex] as HTMLElement;
      if (selectedElement) {
        selectedElement.scrollIntoView({ block: 'nearest' });
      }
    }
  }, [selectedIndex]);

  if (loading) {
    return (
      <div
        className="absolute bottom-full left-4 mb-1 w-80 rounded-xl border border-gray-200/80
                      bg-white/95 backdrop-blur-md shadow-lg px-3 py-2"
      >
        <div className="flex items-center gap-2 text-sm text-gray-400">
          <Loader2 className="animate-spin" size={14} />
          <span>加载文件列表...</span>
        </div>
      </div>
    );
  }

  if (files.length === 0) {
    return (
      <div
        className="absolute bottom-full left-4 mb-1 w-80 rounded-xl border border-gray-200/80
                      bg-white/95 backdrop-blur-md shadow-lg px-3 py-2"
      >
        <div className="text-sm text-gray-400">{filter ? '未找到匹配的文件' : '工作区无文件'}</div>
      </div>
    );
  }

  return (
    <div
      className="absolute bottom-full left-4 mb-1 w-80 rounded-xl border border-gray-200/80
                    bg-white/95 backdrop-blur-md shadow-lg overflow-hidden max-h-64 overflow-y-auto"
      ref={menuRef}
    >
      {files.map((file, index) => (
        <button
          className={`px-3 py-2 cursor-pointer transition-colors flex items-center gap-2 w-full text-left border-0 bg-transparent
            ${
              index === selectedIndex ? 'bg-blue-50 border-l-2 border-blue-500' : 'hover:bg-gray-50'
            }`}
          key={file.path}
          onClick={() => onSelect(file)}
          type="button"
        >
          {getFileIcon(file.extension)}
          <div className="flex-1 min-w-0">
            <div className="text-sm font-medium text-gray-700 truncate">{file.name}</div>
            <div className="text-xs text-gray-400 truncate">{file.relativePath}</div>
          </div>
        </button>
      ))}
    </div>
  );
}
