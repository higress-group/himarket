import { useMemo } from "react";

interface DiffViewerProps {
  path?: string;
  oldText?: string | null;
  newText?: string;
}

export function DiffViewer({ path, oldText, newText }: DiffViewerProps) {
  const lines = useMemo(() => {
    if (!oldText && !newText) return [];

    const oldLines = (oldText ?? "").split("\n");
    const newLines = (newText ?? "").split("\n");
    const result: Array<{ type: "add" | "del" | "ctx"; text: string }> = [];

    let oi = 0;
    let ni = 0;

    while (oi < oldLines.length || ni < newLines.length) {
      if (oi < oldLines.length && ni < newLines.length) {
        if (oldLines[oi] === newLines[ni]) {
          result.push({ type: "ctx", text: oldLines[oi] });
          oi++;
          ni++;
        } else {
          result.push({ type: "del", text: oldLines[oi] });
          oi++;
          if (ni < newLines.length) {
            result.push({ type: "add", text: newLines[ni] });
            ni++;
          }
        }
      } else if (oi < oldLines.length) {
        result.push({ type: "del", text: oldLines[oi] });
        oi++;
      } else {
        result.push({ type: "add", text: newLines[ni] });
        ni++;
      }
    }

    return result;
  }, [oldText, newText]);

  if (lines.length === 0 && newText) {
    return (
      <div className="rounded-lg border border-gray-200/60 overflow-hidden">
        {path && (
          <div className="px-3 py-1.5 bg-gray-50 text-xs text-gray-500 font-mono border-b border-gray-200/60">
            {path} <span className="text-green-500">(new file)</span>
          </div>
        )}
        <pre className="text-xs font-mono leading-5 overflow-x-auto p-2">
          {newText.split("\n").map((line, i) => (
            <div key={i} className="bg-green-50 text-green-700 px-1">
              + {line}
            </div>
          ))}
        </pre>
      </div>
    );
  }

  return (
    <div className="rounded-lg border border-gray-200/60 overflow-hidden">
      {path && (
        <div className="px-3 py-1.5 bg-gray-50 text-xs text-gray-500 font-mono border-b border-gray-200/60">
          {path}
        </div>
      )}
      <pre className="text-xs font-mono leading-5 overflow-x-auto p-2">
        {lines.map((line, i) => (
          <div
            key={i}
            className={
              line.type === "add"
                ? "bg-green-50 text-green-700 px-1"
                : line.type === "del"
                  ? "bg-red-50 text-red-700 px-1"
                  : "text-gray-600 px-1"
            }
          >
            {line.type === "add" ? "+ " : line.type === "del" ? "- " : "  "}
            {line.text}
          </div>
        ))}
      </pre>
    </div>
  );
}
