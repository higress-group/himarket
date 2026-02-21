import { Sparkles, Loader2, Workflow } from "lucide-react";
import { WelcomePage } from "../common/WelcomePage";
import type { ICliProvider } from "../../lib/apis/cliProvider";

interface QuestWelcomeProps {
  onSelectCli: (
    cliId: string,
    cwd: string,
    runtime?: string,
    providerObj?: ICliProvider,
    cliSessionConfig?: string,
  ) => void;
  onCreateQuest: () => void;
  isConnected: boolean;
  disabled: boolean;
  creatingQuest?: boolean;
}

export function QuestWelcome({
  onSelectCli,
  onCreateQuest,
  isConnected,
  disabled,
  creatingQuest,
}: QuestWelcomeProps) {
  // 已连接状态下展示的"新建 Quest"按钮
  const connectedContent = (
    <>
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
    </>
  );

  return (
    <WelcomePage
      icon={<Workflow size={48} />}
      title="HiWork"
      description="选择一个 CLI 工具开始编程"
      isConnected={isConnected}
      disabled={disabled}
      onSelectCli={onSelectCli}
      connectedContent={connectedContent}
    />
  );
}
