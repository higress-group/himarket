import { useState, useEffect } from "react";
import { MessageSquare, Terminal, Loader2 } from "lucide-react";
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

/** 格式化已等待秒数为 "Xs" 或 "Xm Ys" */
function formatElapsed(seconds: number): string {
  if (seconds < 60) return `${seconds}s`;
  const m = Math.floor(seconds / 60);
  const s = seconds % 60;
  return s > 0 ? `${m}m ${s}s` : `${m}m`;
}

export function HiCliWelcome({
  onSelectCli,
  onCreateQuest: _onCreateQuest,
  isConnected,
  disabled,
  creatingQuest: _creatingQuest,
}: HiCliWelcomeProps) {
  const state = useHiCliState();
  const sandbox = state.sandboxStatus;
  const isSandboxCreating = sandbox?.status === "creating";
  const isSandboxError = sandbox?.status === "error";

  // 等待计时器：从 connectStartedAt 开始计时，直到 initialized
  const [elapsed, setElapsed] = useState(0);
  const isWaiting = !state.initialized && !!state.connectStartedAt;

  useEffect(() => {
    if (!isWaiting || !state.connectStartedAt) {
      setElapsed(0);
      return;
    }
    setElapsed(Math.floor((Date.now() - state.connectStartedAt) / 1000));
    const timer = setInterval(() => {
      setElapsed(Math.floor((Date.now() - state.connectStartedAt!) / 1000));
    }, 1000);
    return () => clearInterval(timer);
  }, [isWaiting, state.connectStartedAt]);

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
        /* 已连接且就绪：展示欢迎提示，引导用户通过输入框发送消息 */
        <div className="flex flex-col items-center gap-3">
          <MessageSquare size={24} className="text-gray-400" />
          <p className="text-sm text-gray-500">
            在下方输入框发送消息开始新对话
          </p>
        </div>
      )}

      {/* 等待计时器：CLI 启动中时显示已等待时间 */}
      {isWaiting && elapsed > 0 && (
        <div className="mt-4 flex items-center justify-center gap-2 text-xs text-gray-400">
          <Loader2 size={14} className="animate-spin" />
          <span>正在启动 CLI 工具，已等待 {formatElapsed(elapsed)}</span>
        </div>
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
