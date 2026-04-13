/**
 * 详情页骨架屏组件（通用版本）
 * 用于 ModelDetail、McpDetail、ApiDetail、AgentDetail 等使用 ProductDetailLayout 的页面
 */

export function DetailSkeleton() {
  return (
    <div className="animate-pulse">
      {/* 头部区域 */}
      <div className="bg-white/70 backdrop-blur-sm rounded-2xl border border-gray-100/80 p-6 mb-6">
        <div className="flex items-start gap-5">
          {/* 图标骨架 */}
          <div className="w-16 h-16 rounded-2xl bg-gray-200 flex-shrink-0" />

          {/* 标题和描述 */}
          <div className="flex-1 min-w-0">
            {/* 标题骨架 */}
            <div className="h-7 bg-gray-200 rounded-md w-1/3 mb-3" />
            {/* 描述骨架 */}
            <div className="space-y-2">
              <div className="h-4 bg-gray-200 rounded-md w-full" />
              <div className="h-4 bg-gray-200 rounded-md w-4/5" />
            </div>
          </div>

          {/* 操作按钮骨架 */}
          <div className="flex gap-3">
            <div className="w-24 h-10 bg-gray-200 rounded-xl" />
            <div className="w-24 h-10 bg-gray-200 rounded-xl" />
          </div>
        </div>

        {/* 元信息骨架 */}
        <div className="flex items-center gap-6 mt-5 pt-5 border-t border-gray-100">
          <div className="w-20 h-4 bg-gray-200 rounded-md" />
          <div className="w-24 h-4 bg-gray-200 rounded-md" />
          <div className="w-16 h-4 bg-gray-200 rounded-md" />
        </div>
      </div>

      {/* 内容区域 - 两栏布局 */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* 左侧主内容 */}
        <div className="lg:col-span-2 space-y-6">
          {/* 文档区域 */}
          <div className="bg-white/70 backdrop-blur-sm rounded-2xl border border-gray-100/80 p-6">
            <div className="h-6 bg-gray-200 rounded-md w-24 mb-4" />
            <div className="space-y-3">
              <div className="h-4 bg-gray-200 rounded-md w-full" />
              <div className="h-4 bg-gray-200 rounded-md w-full" />
              <div className="h-4 bg-gray-200 rounded-md w-3/4" />
              <div className="h-4 bg-gray-200 rounded-md w-full" />
              <div className="h-4 bg-gray-200 rounded-md w-5/6" />
            </div>
          </div>

          {/* 配置信息区域 */}
          <div className="bg-white/70 backdrop-blur-sm rounded-2xl border border-gray-100/80 p-6">
            <div className="h-6 bg-gray-200 rounded-md w-24 mb-4" />
            <div className="space-y-4">
              <div className="flex items-center gap-4">
                <div className="w-20 h-4 bg-gray-200 rounded-md" />
                <div className="flex-1 h-4 bg-gray-200 rounded-md" />
              </div>
              <div className="flex items-center gap-4">
                <div className="w-20 h-4 bg-gray-200 rounded-md" />
                <div className="flex-1 h-4 bg-gray-200 rounded-md" />
              </div>
              <div className="flex items-center gap-4">
                <div className="w-20 h-4 bg-gray-200 rounded-md" />
                <div className="flex-1 h-4 bg-gray-200 rounded-md" />
              </div>
            </div>
          </div>
        </div>

        {/* 右侧边栏 */}
        <div className="space-y-6">
          {/* 信息卡片 */}
          <div className="bg-white/70 backdrop-blur-sm rounded-2xl border border-gray-100/80 p-5">
            <div className="h-5 bg-gray-200 rounded-md w-20 mb-4" />
            <div className="space-y-3">
              <div className="flex justify-between">
                <div className="w-16 h-4 bg-gray-200 rounded-md" />
                <div className="w-20 h-4 bg-gray-200 rounded-md" />
              </div>
              <div className="flex justify-between">
                <div className="w-16 h-4 bg-gray-200 rounded-md" />
                <div className="w-24 h-4 bg-gray-200 rounded-md" />
              </div>
              <div className="flex justify-between">
                <div className="w-16 h-4 bg-gray-200 rounded-md" />
                <div className="w-12 h-4 bg-gray-200 rounded-md" />
              </div>
            </div>
          </div>

          {/* 相关推荐 */}
          <div className="bg-white/70 backdrop-blur-sm rounded-2xl border border-gray-100/80 p-5">
            <div className="h-5 bg-gray-200 rounded-md w-20 mb-4" />
            <div className="space-y-3">
              <div className="flex items-center gap-3">
                <div className="w-10 h-10 rounded-lg bg-gray-200" />
                <div className="flex-1 h-4 bg-gray-200 rounded-md" />
              </div>
              <div className="flex items-center gap-3">
                <div className="w-10 h-10 rounded-lg bg-gray-200" />
                <div className="flex-1 h-4 bg-gray-200 rounded-md" />
              </div>
              <div className="flex items-center gap-3">
                <div className="w-10 h-10 rounded-lg bg-gray-200" />
                <div className="flex-1 h-4 bg-gray-200 rounded-md" />
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
