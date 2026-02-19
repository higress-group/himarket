import { Sparkles, Terminal, Loader2 } from "lucide-react";
import { HiCliSelector } from "./HiCliSelector";
import { useHiCliState } from "../../context/HiCliSessionContext";
import type { ICliProvider } from "../../lib/apis/cliProvider";

interface HiCliWelcomeProps {
  onSelectCli: (cliId: string, cwd: string, runtime?: string, providerObj?: ICliProvider) => void;
  onCreateQuest: () => void;
  isConnected: boolean;
  disabled: boolean;
  creatingQuest?: boolean;
}

export function HiCliWelcome({
  onSelectCli,
  onCreateQuest,
  isConnected,
  disabled,
  creatingQuest,
}: HiCliWelcomeProps) {
  const state = useHiCliState();
  const sandbox = state.sandboxStatus;
  const isSandboxCreating = sandbox?.status === "creating";
  const isSandboxError = sandbox?.status === "error";

  return (
    <div className="flex-1 flex items-center justify-center px-6">
      <div className="flex flex-col items-center text-center w-full max-w-sm">
        <div className="mb-4 text-gray-300">
          <Terminal size={48} strokeWidth={1.5} />
        </div>
        <h1 className="text-2xl font-semibold text-gray-700 mb-2">HiCli</h1>

        {isSandboxCreating && (
          <div className="mb-4 flex items-center justify-center gap-2 text-sm text-blue-600">
            <Loader2 size={16} className="animate-spin" />
            <span>{sandbox.message}</span>
          </div>
        )}

        {isSandboxError && (
          <div className="mb-4 text-sm text-red-500">
            {sandbox.message}
          </div>
        )}

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
              disabled={disabled || creatingQuest}
            >
              {creatingQuest ? (
                <Loader2 size={16} className="animate-spin" />
              ) : (
                <Sparkles size={16} />
              )}
              {creatingQuest ? "创建中..." : "新建 Quest"}
            </button>
          </>
        ) : (
          <>
            <p className="text-sm text-gray-400 mb-6">
              选择一个 CLI 工具开始调试
            </p>
            <HiCliSelector onSelect={onSelectCli} disabled={disabled || isSandboxCreating} />
          </>
        )}
      </div>
    </div>
  );
}
