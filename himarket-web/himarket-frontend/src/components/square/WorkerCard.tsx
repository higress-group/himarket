import { DownloadOutlined } from '@ant-design/icons';

interface WorkerCardProps {
  name: string;
  description: string;
  releaseDate: string;
  workerTags?: string[];
  downloadCount?: number;
  onClick?: () => void;
}

export function WorkerCard({
  description,
  downloadCount,
  name,
  onClick,
  releaseDate,
  workerTags = [],
}: WorkerCardProps) {
  return (
    <button
      className="
        group bg-white/70 backdrop-blur-sm rounded-2xl p-5
        border border-gray-100/80
        cursor-pointer
        transition-all duration-300 ease-out
        hover:bg-white hover:shadow-lg hover:shadow-gray-200/50 hover:-translate-y-0.5 hover:border-gray-200/60
        active:scale-[0.98] active:duration-150
        h-[200px] flex flex-col
        w-full text-left border-none
      "
      onClick={onClick}
      type="button"
    >
      {/* 名称 + 下载数 */}
      <div className="flex items-center gap-3 mb-3">
        <h3 className="text-base font-semibold text-gray-800 truncate flex-1 group-hover:text-gray-900 transition-colors">
          {name}
        </h3>
        <span className="flex items-center gap-1.5 text-gray-400 text-sm flex-shrink-0">
          <DownloadOutlined className="text-sm text-gray-400" />
          {downloadCount ?? 0}
        </span>
      </div>

      {/* 简介 */}
      <p className="text-sm line-clamp-3 leading-relaxed text-gray-500 flex-1">{description}</p>

      {/* 底部：标签 + 日期 */}
      <div className="mt-2 space-y-1.5">
        {(workerTags ?? []).length > 0 && (
          <div className="flex items-center gap-1 overflow-hidden">
            {(workerTags ?? []).slice(0, 3).map((tag) => (
              <span
                className="px-2 py-0.5 rounded-md text-[11px] font-medium bg-gray-50 text-gray-500 whitespace-nowrap border border-gray-100"
                key={tag}
              >
                {tag}
              </span>
            ))}
          </div>
        )}

        <div className="flex items-center justify-end text-gray-400 text-xs">
          <span className="tabular-nums tracking-tight">{releaseDate}</span>
        </div>
      </div>
    </button>
  );
}
