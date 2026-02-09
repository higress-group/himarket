import { Sparkles } from "lucide-react";

interface CodingWelcomeProps {
  onCreateQuest: () => void;
  disabled: boolean;
}

export function CodingWelcome({ onCreateQuest, disabled }: CodingWelcomeProps) {
  return (
    <div className="flex-1 flex items-center justify-center">
      <div className="text-center">
        <div className="text-4xl mb-3 text-gray-300">&#9672;</div>
        <h1 className="text-2xl font-semibold text-gray-700 mb-2">HiCoding</h1>
        <p className="text-sm text-gray-400 mb-6">
          创建一个新的 Quest 开始编程
        </p>
        <button
          className="inline-flex items-center gap-2 px-5 py-2.5 rounded-full
                     bg-gray-800 text-white text-sm font-medium
                     hover:bg-gray-700 transition-colors
                     disabled:opacity-40 disabled:cursor-not-allowed"
          onClick={onCreateQuest}
          disabled={disabled}
        >
          <Sparkles size={16} />
          New Quest
        </button>
      </div>
    </div>
  );
}
