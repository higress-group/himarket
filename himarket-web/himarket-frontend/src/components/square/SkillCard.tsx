import { DownloadOutlined } from "@ant-design/icons";

interface SkillCardProps {
  name: string;
  description: string;
  releaseDate: string;
  skillTags?: string[];
  downloadCount?: number;
  onClick?: () => void;
}

export function SkillCard({
  name,
  description,
  releaseDate,
  skillTags = [],
  downloadCount,
  onClick,
}: SkillCardProps) {
  return (
    <div
      onClick={onClick}
      className="
        bg-white/60 backdrop-blur-sm rounded-2xl p-5
        border border-white/40
        cursor-pointer
        transition-all duration-300 ease-in-out
        hover:bg-white hover:shadow-md hover:scale-[1.02] hover:border-gray-300/60
        active:scale-[0.98]
        h-[200px] flex flex-col
      "
    >
      {/* 名称 */}
      <h3 className="text-base font-semibold text-gray-900 truncate mb-3">
        {name}
      </h3>

      {/* 简介 */}
      <p className="text-sm line-clamp-3 leading-relaxed text-[#a3a3a3] flex-1">
        {description}
      </p>

      {/* 底部：标签 + 下载数 + 日期 */}
      <div className="mt-2 space-y-1.5">
        {(skillTags ?? []).length > 0 && (
          <div className="flex items-center gap-1 overflow-hidden">
            {(skillTags ?? []).slice(0, 3).map(tag => (
              <span
                key={tag}
                className="px-1.5 py-0.5 rounded text-[10px] font-medium bg-gray-100 text-gray-500 whitespace-nowrap"
              >
                {tag}
              </span>
            ))}
          </div>
        )}

        <div className="flex items-center justify-end gap-2 text-[#a3a3a3] text-xs">
          {downloadCount != null && downloadCount > 0 && (
            <span className="flex items-center gap-0.5">
              <DownloadOutlined className="text-[10px]" />
              {downloadCount}
            </span>
          )}
          <span>{releaseDate}</span>
        </div>
      </div>
    </div>
  );
}
