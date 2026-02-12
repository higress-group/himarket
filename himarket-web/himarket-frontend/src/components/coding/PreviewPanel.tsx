import { Monitor, Lightbulb } from "lucide-react";
import { getPreviewUrl } from "../../lib/utils/workspaceApi";

interface PreviewPanelProps {
  port: number | null;
}

export function PreviewPanel({ port }: PreviewPanelProps) {
  // No port at all
  if (!port) {
    return (
      <div className="flex-1 flex items-center justify-center bg-gray-50/50">
        <div className="text-center">
          <Monitor size={48} className="mx-auto mb-4 text-gray-300" />
          <div className="text-base text-gray-500 mb-1">预览窗口</div>
          <div className="text-xs text-gray-400 mb-4">
            等待 Agent 启动开发服务器...
          </div>
          <div className="inline-flex items-center gap-1.5 text-xs text-gray-400 bg-gray-100/80 rounded-lg px-3 py-2">
            <Lightbulb size={12} className="text-amber-400 flex-shrink-0" />
            <span>在对话中让 Agent 创建项目并运行开发服务器即可预览</span>
          </div>
        </div>
      </div>
    );
  }

  const url = getPreviewUrl(port);

  // Show iframe directly - dev server is assumed ready when port is set
  return (
    <iframe
      id="coding-preview-iframe"
      src={url}
      className="flex-1 w-full h-full border-none bg-white"
      sandbox="allow-scripts allow-same-origin allow-forms allow-popups"
      title="Dev Server Preview"
    />
  );
}
