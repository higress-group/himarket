/**
 * Skill / Worker 详情页专用骨架屏
 * 精确匹配 SkillDetail / WorkerDetail 的实际页面结构：
 *   - 返回按钮
 *   - 图标 + 标题 + 日期
 *   - 描述文字
 *   - 标签
 *   - 左右两栏：左侧 Tab 内容区 + 右侧下载/操作侧栏
 */

export function SkillWorkerDetailSkeleton() {
  return (
    <div className="animate-pulse py-8 flex flex-col gap-4">
      {/* ── 返回按钮 ── */}
      <div className="mb-4">
        <div className="h-9 w-20 bg-gray-200/60 rounded-xl" />
      </div>

      {/* ── 图标 + 标题 + 日期 ── */}
      <div className="flex items-center gap-4 mb-3">
        <div className="w-16 h-16 rounded-xl bg-gray-200/70 flex-shrink-0" />
        <div className="flex-1 min-w-0">
          <div className="h-6 bg-gray-200/70 rounded-md w-48 mb-2" />
          <div className="h-4 bg-gray-200/50 rounded-md w-32" />
        </div>
      </div>

      {/* ── 描述 ── */}
      <div className="space-y-1.5 mb-3">
        <div className="h-4 bg-gray-200/50 rounded-md w-full" />
        <div className="h-4 bg-gray-200/50 rounded-md w-3/4" />
      </div>

      {/* ── 标签 ── */}
      <div className="flex gap-2 mb-1">
        <div className="h-6 w-16 bg-gray-200/50 rounded" />
        <div className="h-6 w-20 bg-gray-200/50 rounded" />
        <div className="h-6 w-14 bg-gray-200/50 rounded" />
      </div>

      {/* ── 两栏主内容 ── */}
      <div className="flex flex-col lg:flex-row gap-4">
        {/* 左侧：带 Tab 的内容卡片 */}
        <div className="flex-1 min-w-0">
          <div
            className="bg-white rounded-lg overflow-hidden flex flex-col"
            style={{ border: '1px solid #f0f0f0', height: 'calc(100vh - 280px)', minHeight: 500 }}
          >
            {/* Tab 栏 */}
            <div
              className="flex gap-6 px-4 pt-3 pb-0"
              style={{ borderBottom: '1px solid #f0f0f0' }}
            >
              <div className="pb-2 border-b-2 border-gray-300">
                <div className="h-4 w-16 bg-gray-200/70 rounded" />
              </div>
              <div className="pb-2">
                <div className="h-4 w-10 bg-gray-200/40 rounded" />
              </div>
            </div>

            {/* Overview 内容占位 */}
            <div className="flex-1 p-6 space-y-4">
              {/* 表格骨架（frontmatter） */}
              <div className="border border-gray-200/60 rounded overflow-hidden mb-6">
                <div className="flex bg-gray-100/50">
                  <div className="flex-1 px-3 py-2">
                    <div className="h-3.5 bg-gray-200/70 rounded w-12" />
                  </div>
                  <div className="flex-1 px-3 py-2">
                    <div className="h-3.5 bg-gray-200/70 rounded w-16" />
                  </div>
                  <div className="flex-1 px-3 py-2">
                    <div className="h-3.5 bg-gray-200/70 rounded w-20" />
                  </div>
                  <div className="flex-1 px-3 py-2">
                    <div className="h-3.5 bg-gray-200/70 rounded w-14" />
                  </div>
                </div>
                <div className="flex">
                  <div className="flex-1 px-3 py-2">
                    <div className="h-3.5 bg-gray-200/50 rounded w-20" />
                  </div>
                  <div className="flex-1 px-3 py-2">
                    <div className="h-3.5 bg-gray-200/50 rounded w-24" />
                  </div>
                  <div className="flex-1 px-3 py-2">
                    <div className="h-3.5 bg-gray-200/50 rounded w-32" />
                  </div>
                  <div className="flex-1 px-3 py-2">
                    <div className="h-3.5 bg-gray-200/50 rounded w-16" />
                  </div>
                </div>
              </div>

              {/* Markdown 正文骨架 */}
              <div className="space-y-3">
                <div className="h-5 bg-gray-200/60 rounded w-2/5" />
                <div className="h-3.5 bg-gray-200/40 rounded w-full" />
                <div className="h-3.5 bg-gray-200/40 rounded w-full" />
                <div className="h-3.5 bg-gray-200/40 rounded w-4/5" />
                <div className="h-4 mt-2" />
                <div className="h-5 bg-gray-200/60 rounded w-1/3" />
                <div className="h-3.5 bg-gray-200/40 rounded w-full" />
                <div className="h-3.5 bg-gray-200/40 rounded w-5/6" />
                <div className="h-3.5 bg-gray-200/40 rounded w-full" />
                <div className="h-3.5 bg-gray-200/40 rounded w-3/4" />
              </div>
            </div>
          </div>
        </div>

        {/* 右侧：下载卡片 */}
        <div className="w-full lg:w-[420px] flex-shrink-0 space-y-3">
          <div
            className="bg-white rounded-lg overflow-hidden"
            style={{ border: '1px solid #f0f0f0' }}
          >
            {/* 标题 + 版本选择器 */}
            <div
              className="flex items-center justify-between px-4 py-3"
              style={{ borderBottom: '1px solid #f0f0f0' }}
            >
              <div className="h-4 w-10 bg-gray-200/70 rounded" />
              <div className="h-8 w-[180px] bg-gray-200/50 rounded-md" />
            </div>

            {/* 下载按钮 */}
            <div className="px-4 py-3">
              <div className="h-9 bg-gray-200/60 rounded-md w-full" />
            </div>

            {/* HTTP 下载区块 */}
            <div className="px-4 py-3">
              <div className="flex items-center justify-between mb-2">
                <div className="h-3 w-16 bg-gray-200/50 rounded" />
                <div className="h-3 w-4 bg-gray-200/40 rounded" />
              </div>
              <div className="h-9 bg-gray-100 rounded-md border border-gray-200/60" />
            </div>

            {/* NPX / CLI 区块 */}
            <div className="px-4 py-3">
              <div className="flex items-center justify-between mb-3">
                <div className="h-3 w-16 bg-gray-200/50 rounded" />
                <div className="h-3 w-4 bg-gray-200/40 rounded" />
              </div>

              {/* IDE 选择器骨架 */}
              <div className="mb-3">
                <div className="h-3 w-14 bg-gray-200/40 rounded mb-2" />
                <div className="flex flex-wrap gap-2">
                  {[56, 52, 52, 52, 44, 56].map((w, i) => (
                    <div className="h-8 bg-gray-200/40 rounded-md" key={i} style={{ width: w }} />
                  ))}
                </div>
              </div>

              {/* 输出目录 */}
              <div className="mb-3">
                <div className="h-3 w-14 bg-gray-200/40 rounded mb-1.5" />
                <div className="h-8 bg-gray-200/30 rounded-md border border-gray-200/60" />
              </div>

              {/* 命令代码块 */}
              <div className="h-9 bg-gray-100 rounded-md border border-gray-200/60" />
            </div>
          </div>

          {/* 相关推荐卡片骨架 */}
          <div
            className="bg-white rounded-lg overflow-hidden p-4"
            style={{ border: '1px solid #f0f0f0' }}
          >
            <div className="h-4 w-20 bg-gray-200/60 rounded mb-3" />
            <div className="space-y-3">
              {[1, 2, 3].map((i) => (
                <div className="flex items-center gap-3" key={i}>
                  <div className="w-10 h-10 rounded-lg bg-gray-200/50 flex-shrink-0" />
                  <div className="flex-1 min-w-0">
                    <div className="h-3.5 bg-gray-200/50 rounded w-24 mb-1.5" />
                    <div className="h-3 bg-gray-200/30 rounded w-full" />
                  </div>
                </div>
              ))}
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
