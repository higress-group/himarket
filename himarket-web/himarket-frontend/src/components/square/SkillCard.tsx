import { DownloadOutlined } from "@ant-design/icons";
import { ProductIconRenderer } from "../icon/ProductIconRenderer";

interface SkillCardProps {
  name: string;
  description: string;
  releaseDate: string;
  skillTags?: string[];
  downloadCount?: number;
  icon?: string;
  onClick?: () => void;
}

export function SkillCard({
  name,
  description,
  releaseDate,
  skillTags = [],
  downloadCount,
  icon,
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
      {/* 图标 + 名称 + 下载数 */}
      <div className="flex items-center gap-3 mb-3">
        <div className="w-12 h-12 rounded-xl bg-gradient-to-br from-colorPrimary/10 to-colorPrimary/5 flex items-center justify-center flex-shrink-0 overflow-hidden">
          <ProductIconRenderer className="w-full h-full object-cover" iconType={icon} />
        </div>
        <h3 className="text-base font-semibold text-gray-900 truncate flex-1">
          {name}
        </h3>
        <span className="flex items-center gap-0.5 text-[#a3a3a3] text-xs">
          <DownloadOutlined className="text-[10px]" />
          {downloadCount ?? 0}
        </span>
      </div>

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
          <span>{releaseDate}</span>
        </div>
      </div>
    </div>
  );
}
