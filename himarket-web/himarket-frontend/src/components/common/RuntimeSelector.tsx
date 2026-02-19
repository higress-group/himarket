import { useEffect } from 'react';
import { Radio, Tag } from 'antd';
import {
  Monitor,
  Container,
  AlertCircle,
} from 'lucide-react';
import type { RuntimeType, SandboxMode } from '../../types/runtime';

export interface RuntimeOption {
  type: RuntimeType;
  label: string;
  description: string;
  available: boolean;
  unavailableReason?: string;
}

export interface RuntimeSelectorProps {
  cliProvider: string;
  compatibleRuntimes: RuntimeOption[];
  selectedRuntime: string;
  onSelect: (runtimeType: string) => void;
  sandboxMode?: SandboxMode;
  onSandboxModeChange?: (mode: SandboxMode) => void;
}

const RUNTIME_ICONS: Record<RuntimeType, React.ReactNode> = {
  local: <Monitor size={16} />,
  k8s: <Container size={16} />,
};

/**
 * 运行时选择器组件
 *
 * 根据 CLI Provider 的 compatibleRuntimes 展示可选运行时列表，
 * 不兼容/不可用的运行时标记为 disabled 并显示原因，
 * 单一兼容运行时时自动选中。
 */
export const RuntimeSelector: React.FC<RuntimeSelectorProps> = ({
  compatibleRuntimes,
  selectedRuntime,
  onSelect,
  sandboxMode = 'user',
  onSandboxModeChange,
}) => {
  const availableRuntimes = compatibleRuntimes.filter((r) => r.available);

  // 单一兼容运行时时自动选中
  useEffect(() => {
    if (availableRuntimes.length === 1 && selectedRuntime !== availableRuntimes[0].type) {
      onSelect(availableRuntimes[0].type);
    }
  }, [availableRuntimes, selectedRuntime, onSelect]);

  if (compatibleRuntimes.length === 0) {
    return (
      <div className="flex items-center gap-2 text-gray-400 text-sm py-2">
        <AlertCircle size={16} />
        <span>没有兼容的运行时方案</span>
      </div>
    );
  }

  return (
    <div className="flex flex-col gap-1.5 w-full">
      <label className="text-sm font-medium text-gray-600">运行时方案</label>
      <Radio.Group
        value={selectedRuntime}
        onChange={(e) => onSelect(e.target.value)}
        className="w-full"
      >
        <div className="flex flex-col gap-2">
          {compatibleRuntimes.map((runtime) => {
            const isDisabled = !runtime.available;

            const radioContent = (
              <Radio
                key={runtime.type}
                value={runtime.type}
                disabled={isDisabled}
                className="w-full"
              >
                <div className="flex items-center gap-2">
                  <span className={isDisabled ? 'text-gray-400' : 'text-gray-700'}>
                    {RUNTIME_ICONS[runtime.type]}
                  </span>
                  <div className="flex flex-col">
                    <div className="flex items-center gap-2">
                      <span
                        className={`text-sm font-medium ${isDisabled ? 'text-gray-400' : 'text-gray-800'}`}
                      >
                        {runtime.label}
                      </span>
                      {isDisabled && (
                        <Tag color="default" className="text-xs">
                          不可用
                        </Tag>
                      )}
                    </div>
                    <span className={`text-xs ${isDisabled ? 'text-gray-300' : 'text-gray-500'}`}>
                      {runtime.description}
                    </span>
                    {isDisabled && runtime.unavailableReason && (
                      <span className="text-xs text-orange-400 mt-0.5">
                        {runtime.unavailableReason}
                      </span>
                    )}
                  </div>
                </div>
              </Radio>
            );

            return <div key={runtime.type}>{radioContent}</div>;
          })}
        </div>
      </Radio.Group>

      {/* 沙箱模式子选项：仅当 K8s 运行时被选中且可用时展示 */}
      {selectedRuntime === 'k8s' &&
        compatibleRuntimes.some((r) => r.type === 'k8s' && r.available) && (
          <div className="ml-6 mt-1 flex flex-col gap-1.5">
            <label className="text-xs font-medium text-gray-500">沙箱模式</label>
            <Radio.Group
              value={sandboxMode}
              onChange={(e) => onSandboxModeChange?.(e.target.value)}
            >
              <div className="flex flex-col gap-1.5">
                <Radio value="user">
                  <div className="flex flex-col">
                    <span className="text-sm text-gray-800">用户级沙箱</span>
                    <span className="text-xs text-gray-500">
                      常驻 Pod，多会话复用，适合调试开发
                    </span>
                  </div>
                </Radio>
                <Radio value="session" disabled>
                  <div className="flex flex-col">
                    <div className="flex items-center gap-2">
                      <span className="text-sm text-gray-400">会话级沙箱</span>
                      <Tag color="default" className="text-xs">
                        即将推出
                      </Tag>
                    </div>
                    <span className="text-xs text-gray-300">
                      每会话独立 Pod，会话结束后自动销毁
                    </span>
                  </div>
                </Radio>
              </div>
            </Radio.Group>
          </div>
        )}
    </div>
  );
};
