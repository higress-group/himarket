import type { PermissionRequest } from "../../types/acp";

interface PermissionDialogProps {
  permission: { id: number; request: PermissionRequest };
  onRespond: (requestId: number, optionId: string) => void;
}

export function PermissionDialog({
  permission,
  onRespond,
}: PermissionDialogProps) {
  const { id, request } = permission;
  const toolCall = request.toolCall;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/30 backdrop-blur-sm">
      <div className="w-full max-w-md rounded-2xl bg-white shadow-xl p-6">
        <div className="text-base font-semibold text-gray-800 mb-3">
          需要权限确认
        </div>
        <div className="space-y-2 mb-5">
          {toolCall.title && (
            <div className="text-sm font-medium text-gray-700">
              {toolCall.title}
            </div>
          )}
          {toolCall.rawInput?.command != null && (
            <div className="text-sm text-gray-500">
              命令:{" "}
              <code className="px-1.5 py-0.5 rounded bg-gray-100 text-gray-700 text-xs font-mono">
                {String(toolCall.rawInput.command)}
              </code>
            </div>
          )}
          {toolCall.rawInput?.description != null && (
            <div className="text-sm text-gray-500">
              {String(toolCall.rawInput.description)}
            </div>
          )}
        </div>
        <div className="flex items-center justify-end gap-2">
          {request.options.map(opt => {
            const isAllow = opt.kind.startsWith("allow");
            return (
              <button
                key={opt.optionId}
                className={`
                  px-4 py-2 rounded-lg text-sm font-medium transition-colors
                  ${
                    isAllow
                      ? "bg-gray-800 text-white hover:bg-gray-700"
                      : "bg-gray-100 text-gray-600 hover:bg-gray-200"
                  }
                `}
                onClick={() => onRespond(id, opt.optionId)}
              >
                {opt.name}
              </button>
            );
          })}
        </div>
      </div>
    </div>
  );
}
