import { Loader2, CheckCircle, Circle } from "lucide-react";
import { useCodingState } from "../../context/CodingSessionContext";

/** 阶段顺序 */
const PHASE_ORDER = [
  "sandbox-acquire",
  "filesystem-ready",
  "config-injection",
  "sidecar-connect",
  "cli-ready",
] as const;

/** 阶段信息映射 */
const PHASE_INFO: Record<string, { name: string; description: string }> = {
  "sandbox-acquire": {
    name: "沙箱获取",
    description: "正在分配计算资源",
  },
  "filesystem-ready": {
    name: "文件系统就绪",
    description: "正在准备工作目录",
  },
  "config-injection": {
    name: "配置注入",
    description: "正在注入 CLI 配置",
  },
  "sidecar-connect": {
    name: "Sidecar 连接",
    description: "正在建立运行时连接",
  },
  "cli-ready": {
    name: "CLI 就绪",
    description: "正在启动 CLI 工具",
  },
};

type PhaseStatus = "pending" | "executing" | "completed";

interface PhaseCardProps {
  phaseName: string;
  status: PhaseStatus;
}

function PhaseCard({ phaseName, status }: PhaseCardProps) {
  const info = PHASE_INFO[phaseName] || { name: phaseName, description: "" };

  const statusStyles = {
    pending: "border-gray-200 bg-gray-50/50",
    executing: "border-blue-300 bg-blue-50/50",
    completed: "border-green-300 bg-green-50/50",
  };

  const iconMap = {
    pending: <Circle size={20} className="text-gray-300" />,
    executing: <Loader2 size={20} className="animate-spin text-blue-500" />,
    completed: <CheckCircle size={20} className="text-green-500" />,
  };

  return (
    <div
      className={`flex items-center gap-3 p-3 rounded-lg border transition-all duration-300 ${statusStyles[status]}`}
    >
      <div className="flex-shrink-0">{iconMap[status]}</div>
      <div className="flex-1 min-w-0">
        <div className="text-sm font-medium text-gray-700">{info.name}</div>
        <div className="text-xs text-gray-500 truncate">{info.description}</div>
      </div>
    </div>
  );
}

export function SandboxInitProgress() {
  const state = useCodingState();
  const progress = state.initProgress;

  // 计算每个阶段的状态
  const getPhaseStatus = (phaseName: string): PhaseStatus => {
    if (!progress) return "pending";

    const phaseIndex = PHASE_ORDER.indexOf(phaseName as typeof PHASE_ORDER[number]);
    const currentIndex = PHASE_ORDER.indexOf(progress.phase as typeof PHASE_ORDER[number]);

    if (phaseIndex < currentIndex) return "completed";
    if (phaseIndex === currentIndex) {
      return progress.status === "completed" ? "completed" : "executing";
    }
    return "pending";
  };

  // 默认加载状态（无进度信息时）
  if (!progress) {
    return (
      <div className="w-full max-w-md mx-auto p-6">
        <div className="text-center">
          <Loader2 size={32} className="animate-spin text-blue-500 mx-auto mb-4" />
          <h3 className="text-lg font-semibold text-gray-700">
            正在连接沙箱环境
          </h3>
          <p className="text-sm text-gray-500 mt-1">请稍候...</p>
        </div>
      </div>
    );
  }

  return (
    <div className="w-full max-w-md mx-auto p-6">
      {/* 标题 */}
      <div className="text-center mb-6">
        <h3 className="text-lg font-semibold text-gray-700">
          正在初始化沙箱环境
        </h3>
        <p className="text-sm text-gray-500 mt-1">{progress.message}</p>
      </div>

      {/* 整体进度条 */}
      <div className="mb-6">
        <div className="h-2 bg-gray-100 rounded-full overflow-hidden">
          <div
            className="h-full bg-gradient-to-r from-blue-500 to-blue-600 transition-all duration-500 ease-out"
            style={{ width: `${progress.progress}%` }}
          />
        </div>
        <div className="text-center text-xs text-gray-500 mt-2">
          {progress.completedPhases} / {progress.totalPhases} 已完成
        </div>
      </div>

      {/* 阶段列表 */}
      <div className="space-y-2">
        {PHASE_ORDER.map((phaseName) => (
          <PhaseCard
            key={phaseName}
            phaseName={phaseName}
            status={getPhaseStatus(phaseName)}
          />
        ))}
      </div>
    </div>
  );
}
