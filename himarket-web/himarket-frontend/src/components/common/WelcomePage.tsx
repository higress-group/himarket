import { CliSelector } from './CliSelector';

import type { ICliProvider } from '../../lib/apis/cliProvider';

export interface WelcomePageProps {
  icon: React.ReactNode;
  title: string;
  description: string;
  isConnected: boolean;
  disabled: boolean;
  // CLI 选择相关
  onSelectCli: (
    cliId: string,
    cwd: string,
    runtime?: string,
    providerObj?: ICliProvider,
    cliSessionConfig?: string,
  ) => void;
  // 已连接后的操作内容
  connectedContent?: React.ReactNode;
}

/**
 * 统一欢迎页组件
 * 两个模块（HiWork、HiCoding）共用的欢迎页布局：
 * 居中容器 → 模块图标 → 模块名称 → 描述文案 → CliSelector 或操作按钮
 */
export function WelcomePage({
  connectedContent,
  description,
  disabled,
  icon,
  isConnected,
  onSelectCli,
  title,
}: WelcomePageProps) {
  return (
    <div className="flex-1 flex items-center justify-center px-6 min-h-0 overflow-y-auto">
      <div className="flex flex-col items-center text-center w-full max-w-sm my-auto py-6">
        {/* 模块图标 */}
        <div className="mb-4 text-gray-300">{icon}</div>

        {/* 模块名称 */}
        <h1 className="text-2xl font-semibold text-gray-700 mb-2">{title}</h1>

        {/* 描述文案 + CliSelector 或已连接内容 */}
        {isConnected ? (
          connectedContent
        ) : (
          <>
            <p className="text-sm text-gray-400 mb-6">{description}</p>
            <CliSelector disabled={disabled} onSelect={onSelectCli} />
          </>
        )}
      </div>
    </div>
  );
}
