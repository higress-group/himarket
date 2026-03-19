interface TerminalOutputProps {
  text: string;
}

export function TerminalOutput({ text }: TerminalOutputProps) {
  if (!text) return null;
  return (
    <div className="rounded-lg border border-gray-200/60 bg-gray-50 p-3 overflow-x-auto">
      <pre className="text-xs font-mono text-gray-600 whitespace-pre-wrap">
        {text}
      </pre>
    </div>
  );
}
