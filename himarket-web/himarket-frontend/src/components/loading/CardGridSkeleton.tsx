/**
 * 卡片网格骨架屏组件
 * 用于 Square 页面等卡片列表加载状态
 * 网格列数与实际卡片网格保持一致，避免切换时布局跳动
 */

interface CardGridSkeletonProps {
  /** 卡片数量，默认 8 */
  count?: number;
  /** 响应式列数配置 */
  columns?: { sm?: number; md?: number; lg?: number };
}

export function CardGridSkeleton({ count = 8, columns }: CardGridSkeletonProps) {
  const gridCols = columns
    ? `grid-cols-1 sm:grid-cols-${columns.sm ?? 2} md:grid-cols-${columns.md ?? 3} lg:grid-cols-${columns.lg ?? 4}`
    : 'grid-cols-1 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-4';
  return (
    <div className={`grid ${gridCols} gap-6 max-w-[1600px] mx-auto`}>
      {Array.from({ length: count }).map((_, index) => (
        <div
          key={index}
          className="
            bg-white/70 backdrop-blur-sm rounded-2xl p-5
            border border-gray-100/80
            h-[200px] flex flex-col
            animate-pulse
          "
        >
          {/* 图标 + 名称 + 下载数 骨架 */}
          <div className="flex items-center gap-3 mb-3">
            {/* 图标骨架 */}
            <div className="w-12 h-12 rounded-xl bg-gray-200 flex-shrink-0" />
            {/* 名称骨架 */}
            <div className="flex-1 h-5 bg-gray-200 rounded-md" />
            {/* 下载数骨架 */}
            <div className="w-12 h-4 bg-gray-200 rounded-md" />
          </div>

          {/* 简介骨架 */}
          <div className="flex-1 space-y-2">
            <div className="h-4 bg-gray-200 rounded-md w-full" />
            <div className="h-4 bg-gray-200 rounded-md w-4/5" />
            <div className="h-4 bg-gray-200 rounded-md w-3/5" />
          </div>

          {/* 底部标签 + 日期骨架 */}
          <div className="mt-2 space-y-2">
            {/* 标签骨架 */}
            <div className="flex items-center gap-1">
              <div className="w-12 h-5 bg-gray-200 rounded-md" />
              <div className="w-14 h-5 bg-gray-200 rounded-md" />
            </div>
            {/* 日期骨架 */}
            <div className="flex justify-end">
              <div className="w-24 h-3 bg-gray-200 rounded-md" />
            </div>
          </div>
        </div>
      ))}
    </div>
  );
}
