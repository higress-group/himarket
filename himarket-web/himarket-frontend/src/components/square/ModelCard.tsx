import { ProductIconRenderer } from '../icon/ProductIconRenderer';

interface ModelCardProps {
  icon: string;
  name: string;
  description: string;
  company?: string;
  releaseDate: string;
  onClick?: () => void;
}

export function ModelCard({
  company,
  description,
  icon,
  name,
  onClick,
  releaseDate,
}: ModelCardProps) {
  return (
    <button
      className="
        group bg-[linear-gradient(135deg,rgba(236,239,246,0.96)_0%,rgba(246,248,252,0.9)_50%,rgba(255,255,255,0.88)_100%)] backdrop-blur-sm rounded-[18px] p-5
        border border-[#D6DEEA]/90
        cursor-pointer
        transition-all duration-300 ease-out
        shadow-[0_14px_38px_rgba(74,85,120,0.065)]
        hover:bg-[#F7F9FD]/92 hover:shadow-lg hover:shadow-indigo-100/35 hover:-translate-y-0.5 hover:border-[#C4CEE0]
        active:scale-[0.98] active:duration-150
        relative
        overflow-hidden
        min-h-[176px]
        flex flex-col
        w-full text-left
      "
      onClick={onClick}
      type="button"
    >
      {/* 上部：图标和名称 */}
      <div className="flex items-center gap-4 mb-3">
        <div className="w-12 h-12 rounded-[14px] border border-white/60 bg-white/70 flex items-center justify-center flex-shrink-0 overflow-hidden">
          <ProductIconRenderer className="w-full h-full object-cover" iconType={icon} />
        </div>
        <h3 className="text-base font-semibold text-gray-900 truncate flex-1 transition-colors">
          {name}
        </h3>
      </div>

      {/* 中部：简介（固定两行） */}
      <p className="max-h-12 text-sm mb-3 line-clamp-2 leading-relaxed flex-1 text-gray-500">
        {description}
      </p>

      {/* 底部：公司和发布日期 */}
      <div className="h-8 flex items-center justify-between text-xs">
        {company ? <span className="truncate text-gray-500">{company}</span> : null}
        <span className="flex-shrink-0 text-gray-400 tabular-nums tracking-tight">
          {releaseDate}
        </span>
      </div>
    </button>
  );
}
