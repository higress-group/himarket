import { Sparkles, Loader2 } from "lucide-react";

interface QuestWelcomeProps {
  onCreateQuest: () => void;
  disabled: boolean;
  creatingQuest?: boolean;
}

export function QuestWelcome({ onCreateQuest, disabled, creatingQuest }: QuestWelcomeProps) {
  return (
    <div className="flex-1 flex items-center justify-center px-6">
      <div className="text-center">
        <div className="text-4xl mb-3 text-gray-300">&#9672;</div>
        <h1 className="text-2xl font-semibold text-gray-700 mb-2">HiWork</h1>
        <p className="text-sm text-gray-400 mb-6">
          创建一个新的 Quest 开始编程
        </p>
        <button
          className="inline-flex items-center gap-2 px-5 py-2.5 rounded-full
                     bg-gray-800 text-white text-sm font-medium
                     hover:bg-gray-700 transition-colors
                     disabled:opacity-40 disabled:cursor-not-allowed"
          onClick={onCreateQuest}
          disabled={disabled || creatingQuest}
        >
          {creatingQuest ? (
            <Loader2 size={16} className="animate-spin" />
          ) : (
            <Sparkles size={16} />
          )}
          {creatingQuest ? "创建中..." : "New Quest"}
        </button>
      </div>
    </div>
  );
}
