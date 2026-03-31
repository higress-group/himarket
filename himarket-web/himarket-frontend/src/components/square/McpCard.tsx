import { useState } from "react";
import { ToolOutlined } from "@ant-design/icons";
import { ProductIconRenderer } from "../icon/ProductIconRenderer";

interface McpCardProps {
  icon: string;
  name: string;
  mcpName?: string;
  description: string;
  releaseDate: string;
  protocols: string[];
  toolCount: number;
  tags: string[];
  categoryName?: string;
  subscribed?: boolean;
  isLoggedIn?: boolean;
  onClick?: () => void;
  onSubscribe?: () => Promise<void>;
  onUnsubscribe?: () => Promise<void>;
}

export function McpCard({
  icon, name, mcpName, description, releaseDate,
  protocols, toolCount, tags, categoryName,
  subscribed, isLoggedIn,
  onClick, onSubscribe, onUnsubscribe,
}: McpCardProps) {
  const [actionLoading, setActionLoading] = useState(false);

  const hasActions = isLoggedIn && (onSubscribe || onUnsubscribe);

  const handleSubscribeClick = async (e: React.MouseEvent) => {
    e.stopPropagation();
    if (!onSubscribe) return;
    setActionLoading(true);
    try { await onSubscribe(); } finally { setActionLoading(false); }
  };

  const handleUnsubscribeClick = async (e: React.MouseEvent) => {
    e.stopPropagation();
    if (!onUnsubscribe) return;
    setActionLoading(true);
    try { await onUnsubscribe(); } finally { setActionLoading(false); }
  };

  return (
    <div
      onClick={onClick}
      className="
        group bg-white/70 backdrop-blur-sm rounded-2xl p-5
        border border-gray-100/80
        cursor-pointer
        transition-all duration-300 ease-out
        hover:bg-white hover:shadow-lg hover:shadow-gray-200/50 hover:-translate-y-0.5 hover:border-gray-200/60
        active:scale-[0.98] active:duration-150
        relative
        overflow-hidden
        min-h-[200px]
        flex flex-col
      "
    >
      {/* 已订阅角标 */}
      {isLoggedIn && subscribed && (
        <div className="absolute top-3 right-3 z-10">
          <span className="text-[10px] font-medium px-1.5 py-0.5 rounded-full bg-green-50 text-green-600 border border-green-100">
            已订阅
          </span>
        </div>
      )}

      {/* 头部：icon + 名称 + 协议标签 */}
      <div className="flex items-center gap-3 mb-3">
        <div className="w-12 h-12 rounded-xl bg-gradient-to-br from-colorPrimary/10 to-colorPrimary/5 flex items-center justify-center flex-shrink-0 overflow-hidden">
          <ProductIconRenderer className="w-full h-full object-cover" iconType={icon} />
        </div>
        <div className="flex-1 min-w-0">
          <h3 className="text-base font-semibold text-gray-800 truncate group-hover:text-gray-900 transition-colors">{name}</h3>
          {mcpName && (
            <div className="text-[10px] text-gray-400 font-mono truncate mt-0.5">{mcpName}</div>
          )}
          <div className="flex items-center gap-1.5 mt-1 flex-wrap">
            {protocols.map(p => (
              <span key={p} className="text-[10px] font-medium px-1.5 py-0.5 rounded bg-colorPrimary/10 text-colorPrimary">
                {p}
              </span>
            ))}
            {toolCount > 0 && (
              <span className="text-[10px] font-medium px-1.5 py-0.5 rounded bg-colorPrimary/5 text-colorPrimary/80">
                <ToolOutlined className="mr-0.5" />{toolCount}
              </span>
            )}
          </div>
        </div>
      </div>

      {/* 描述 */}
      <p className="max-h-12 text-sm mb-4 line-clamp-2 leading-relaxed flex-1 text-gray-500">
        {description || "暂无描述"}
      </p>

      {/* 底部：标签 + 日期 - hover 时淡出 */}
      <div className={`h-10 flex items-center justify-between text-xs transition-opacity duration-300 ${hasActions ? 'group-hover:opacity-0' : ''}`}>
        <div className="flex items-center gap-1.5 flex-wrap flex-1 min-w-0">
          {tags.slice(0, 2).map((t) => (
            <span key={t} className="text-[10px] px-2 py-0.5 rounded-full bg-gray-50 text-gray-500 border border-gray-100 truncate max-w-[80px]">
              {t}
            </span>
          ))}
          {!tags.length && categoryName && (
            <span className="text-[10px] px-2 py-0.5 rounded-full bg-gray-50 text-gray-500 border border-gray-100">
              {categoryName}
            </span>
          )}
        </div>
        <span className="flex-shrink-0 text-gray-400 tabular-nums tracking-tight">{releaseDate}</span>
      </div>

      {/* Hover 操作按钮 */}
      {hasActions && (
        <div className="
          absolute bottom-0 left-0 right-0
          p-5
          opacity-0 translate-y-2
          group-hover:opacity-100 group-hover:translate-y-0
          transition-all duration-300 ease-out
          pointer-events-none group-hover:pointer-events-auto
        ">
          <div className="flex gap-3">
            <button
              onClick={(e) => { e.stopPropagation(); onClick?.(); }}
              className="
                flex-1 px-4 py-2.5 rounded-xl
                border border-gray-300
                text-sm font-medium text-gray-700
                bg-white
                hover:bg-gray-50 hover:border-gray-400
                transition-all duration-200
                shadow-sm
              "
            >
              查看详情
            </button>
            {subscribed ? (
              <button
                disabled={actionLoading}
                onClick={handleUnsubscribeClick}
                className="flex-1 px-4 py-2.5 rounded-xl text-sm font-medium text-red-600 bg-white border border-red-300 hover:bg-red-50 hover:border-red-400 transition-all duration-200 shadow-sm disabled:opacity-50"
              >
                {actionLoading ? "处理中..." : "取消订阅"}
              </button>
            ) : (
              <button
                disabled={actionLoading}
                onClick={handleSubscribeClick}
                className="flex-1 px-4 py-2.5 rounded-xl text-sm font-medium text-white bg-colorPrimary hover:opacity-90 transition-all duration-200 shadow-sm disabled:opacity-50"
              >
                {actionLoading ? "处理中..." : "立即订阅"}
              </button>
            )}
          </div>
        </div>
      )}
    </div>
  );
}
