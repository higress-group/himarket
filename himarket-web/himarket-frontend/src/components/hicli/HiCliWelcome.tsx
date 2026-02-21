import { Sparkles, Terminal, Loader2 } from "lucide-react";
import { WelcomePage } from "../common/WelcomePage";
import { useHiCliState } from "../../context/HiCliSessionContext";
import type { ICliProvider } from "../../lib/apis/cliProvider";

interface HiCliWelcomeProps {
  onSelectCli: (cliId: string, cwd: string, runtime?: string, providerObj?: ICliProvider, cliSessionConfig?: string) => void;
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

  // 沙箱状态提示 + 已连接后的操作内容
  const connectedContent = (
    <>
      {/* 沙箱创建中提示 */}
      {isSandboxCreating && (
        <div className="mb-4 flex items-center justify-center gap-2 text-sm text-blue-600">
          <Loader2 size={16} className="animate-spin" />
          <span>{sandbox.message}</span>
        </div>
      )}

      {/* 沙箱错误提示 */}
      {isSandboxError && (
        <div className="mb-4 text-sm text-red-500">
          {sandbox.message}
        </div>
      )}

      {isSandboxCreating ? (
        /* 已连接但沙箱正在创建中：只显示创建状态 */
        <p className="text-sm text-gray-400">
          请稍候，沙箱就绪后将自动进入会话
        </p>
      ) : (
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
      )}
    </>
  );

  return (
    <WelcomePage
      icon={<Terminal size={48} strokeWidth={1.5} />}
      title="HiCli"
      description="选择一个 CLI 工具开始调试"
      isConnected={isConnected}
      disabled={disabled || isSandboxCreating}
      onSelectCli={onSelectCli}
      showRuntimeSelector={true}
      connectedContent={connectedContent}
    />
  );
}
