import { useState } from "react";
import { ChevronDown, ChevronRight, Brain } from "lucide-react";

interface ThoughtBlockProps {
  text: string;
}

export function ThoughtBlock({ text }: ThoughtBlockProps) {
  const [expanded, setExpanded] = useState(false);

  return (
    <div className="rounded-xl border border-gray-200/60 bg-white/60 backdrop-blur-sm overflow-hidden">
      <button
        className="flex items-center gap-1.5 w-full px-3 py-2 text-xs text-gray-500
                   hover:bg-gray-50/50 transition-colors"
        onClick={() => setExpanded(!expanded)}
      >
        <Brain size={12} />
        思考中...
        {expanded ? <ChevronDown size={12} /> : <ChevronRight size={12} />}
      </button>
      {expanded && (
        <div className="px-3 pb-2 text-xs text-gray-500 whitespace-pre-wrap leading-relaxed border-t border-gray-100">
          {text}
        </div>
      )}
    </div>
  );
}
