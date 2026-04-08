import { diffLines } from 'diff';
import { useMemo } from 'react';

interface DiffViewerProps {
  path?: string;
  oldText?: string | null;
  newText?: string | null;
}

type DiffLineType = 'add' | 'del' | 'ctx';

function splitToDisplayLines(value: string): string[] {
  if (value.length === 0) return [''];
  const lines = value.split('\n');
  if (lines.length > 0 && lines[lines.length - 1] === '') {
    lines.pop();
  }
  return lines.length > 0 ? lines : [''];
}

export function DiffViewer({ newText, oldText, path }: DiffViewerProps) {
  const lines = useMemo(() => {
    const changes = diffLines(oldText ?? '', newText ?? '');
    const result: Array<{ type: DiffLineType; text: string }> = [];

    for (const change of changes) {
      const type: DiffLineType = change.added ? 'add' : change.removed ? 'del' : 'ctx';
      const displayLines = splitToDisplayLines(change.value);
      for (const line of displayLines) {
        result.push({ text: line, type });
      }
    }

    return result;
  }, [oldText, newText]);

  return (
    <div className="rounded-lg border border-gray-200/60 overflow-hidden">
      {path && (
        <div className="px-3 py-1.5 bg-gray-50 text-xs text-gray-500 font-mono border-b border-gray-200/60">
          {path}
        </div>
      )}
      <pre className="text-xs font-mono leading-5 overflow-x-auto p-2">
        {lines.length === 0 ? (
          <div className="text-gray-400 px-1">No diff content</div>
        ) : (
          lines.map((line, i) => (
            <div
              className={
                line.type === 'add'
                  ? 'bg-green-50 text-green-700 px-1'
                  : line.type === 'del'
                    ? 'bg-red-50 text-red-700 px-1'
                    : 'text-gray-600 px-1'
              }
              key={i}
            >
              {line.type === 'add' ? '+ ' : line.type === 'del' ? '- ' : '  '}
              {line.text}
            </div>
          ))
        )}
      </pre>
    </div>
  );
}
