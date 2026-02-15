import { Sparkles, Terminal } from "lucide-react";
import { HiCliSelector } from "./HiCliSelector";

interface HiCliWelcomeProps {
  onSelectCli: (cliId: string, cwd: string) => void;
  onCreateQuest: () => void;
  isConnected: boolean;
  disabled: boolean;
}

export function HiCliWelcome({
  onSelectCli,
  onCreateQuest,
  isConnected,
  disabled,
}: HiCliWelcomeProps) {
  return (
    <div className="flex-1 flex items-center justify-center">
      <div className="flex flex-col items-center text-center">
        <div className="mb-4 text-gray-300">
          <Terminal size={48} strokeWidth={1.5} />
        </div>
        <h1 className="text-2xl font-semibold text-gray-700 mb-2">HiCli</h1>

        {isConnected ? (
          <>
            <p className="text-sm text-gray-400 mb-6">
              创建一个新的 Quest 开始对话
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
              新建 Quest
            </button>
          </>
        ) : (
          <>
            <p className="text-sm text-gray-400 mb-6">
              选择一个 CLI 工具开始调试
            </p>
            <HiCliSelector onSelect={onSelectCli} disabled={disabled} />
          </>
        )}
      </div>
    </div>
  );
}
