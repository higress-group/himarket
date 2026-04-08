import { ProductIconRenderer } from '../icon/ProductIconRenderer';

interface ModelCardProps {
  icon: string;
  name: string;
  description: string;
  company?: string;
  releaseDate: string;
  onClick?: () => void;
  onTryNow?: () => void;
}

export function ModelCard({
  company,
  description,
  icon,
  name,
  onClick,
  onTryNow,
  releaseDate,
}: ModelCardProps) {
  return (
    <button
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
        w-full text-left border-none
      "
      onClick={onClick}
      type="button"
    >
      {/* 上部：图标和名称 */}
      <div className="flex items-center gap-3 mb-3">
        <div className="w-12 h-12 rounded-xl bg-gradient-to-br from-colorPrimary/10 to-colorPrimary/5 flex items-center justify-center flex-shrink-0 overflow-hidden">
          <ProductIconRenderer className="w-full h-full object-cover" iconType={icon} />
        </div>
        <h3 className="text-base font-semibold text-gray-800 truncate flex-1 group-hover:text-gray-900 transition-colors">
          {name}
        </h3>
      </div>

      {/* 中部：简介（固定两行） */}
      <p className="max-h-12 text-sm mb-4 line-clamp-2 leading-relaxed flex-1 text-gray-500">
        {description}
      </p>

      {/* 底部：公司和发布日期 - 只有在有按钮时才在 hover 时淡出 */}
      <div
        className={`h-10 flex items-center justify-between text-xs transition-opacity duration-300 ${onTryNow ? 'group-hover:opacity-0' : ''}`}
      >
        {company ? <span className="truncate text-gray-500">{company}</span> : null}
        <span className="flex-shrink-0 text-gray-400 tabular-nums tracking-tight">
          {releaseDate}
        </span>
      </div>

      {/* 底部按钮组 - hover 时淡入 + 轻微上移 */}
      {onTryNow && (
        <div
          className="
            absolute bottom-0 left-0 right-0
            p-5
            opacity-0 translate-y-2
            group-hover:opacity-100 group-hover:translate-y-0
            transition-all duration-300 ease-out
            pointer-events-none group-hover:pointer-events-auto
          "
        >
          <div className="flex gap-3">
            <button
              className="
                flex-1 px-4 py-2.5 rounded-xl
                border border-gray-300
                text-sm font-medium text-gray-700
                bg-white
                hover:bg-gray-50 hover:border-gray-400
                transition-all duration-200
                shadow-sm
              "
              onClick={(e) => {
                e.stopPropagation();
                onClick?.();
              }}
            >
              查看详情
            </button>
            <button
              className="
                flex-1 px-4 py-2.5 rounded-xl
                text-sm font-medium text-white
                bg-colorPrimary
                hover:opacity-90
                transition-all duration-200
                shadow-sm
              "
              onClick={(e) => {
                e.stopPropagation();
                onTryNow();
              }}
            >
              立即体验
            </button>
          </div>
        </div>
      )}
    </button>
  );
}
