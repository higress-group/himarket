import { useState } from "react";
import { DownloadOutlined, EditOutlined, DeleteOutlined, UserOutlined } from "@ant-design/icons";
import { Popconfirm, message } from "antd";
import { deleteDeveloperSkill } from "../../lib/apis/developerSkill";

interface SkillCardProps {
  name: string;
  description: string;
  releaseDate: string;
  skillTags?: string[];
  downloadCount?: number;
  onClick?: () => void;
  sourceTag?: "personal" | "official";
  developerUsername?: string;
  // personal view extras
  isOwner?: boolean;
  productId?: string;
  onEdit?: () => void;
  onDelete?: () => void;
}

export function SkillCard({
  name,
  description,
  releaseDate,
  skillTags = [],
  downloadCount,
  onClick,
  sourceTag,
  developerUsername,
  isOwner,
  productId,
  onEdit,
  onDelete,
}: SkillCardProps) {
  const [deleting, setDeleting] = useState(false);

  const handleDelete = async (e?: React.MouseEvent) => {
    e?.stopPropagation();
    if (!productId) return;
    setDeleting(true);
    try {
      await deleteDeveloperSkill(productId);
      message.success("已删除");
      onDelete?.();
    } catch {
      message.error("删除失败");
    } finally {
      setDeleting(false);
    }
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
        h-[200px] flex flex-col
      "
    >
      {/* 名称 + 标签 + 下载数 */}
      <div className="flex items-center gap-3 mb-3">
        <h3 className="text-base font-semibold text-gray-800 truncate flex-1 group-hover:text-gray-900 transition-colors">
          {name}
        </h3>
        {sourceTag && (
          <span
            className={`
              px-1.5 py-0.5 rounded text-[10px] font-medium flex-shrink-0
              ${sourceTag === "personal"
                ? "bg-blue-50 text-blue-500 border border-blue-100"
                : "bg-emerald-50 text-emerald-500 border border-emerald-100"
              }
            `}
          >
            {sourceTag === "personal" ? "个人" : "平台"}
          </span>
        )}
        {isOwner ? (
          <div className="flex items-center gap-1 flex-shrink-0" onClick={(e) => e.stopPropagation()}>
            <button
              type="button"
              onClick={(e) => { e.stopPropagation(); onEdit?.(); }}
              className="p-1 rounded text-gray-400 hover:text-blue-500 transition-colors"
              title="编辑"
            >
              <EditOutlined className="text-sm" />
            </button>
            <Popconfirm
              title="确认删除该 Skill？"
              onConfirm={handleDelete}
              okText="删除"
              cancelText="取消"
              okButtonProps={{ danger: true, loading: deleting }}
            >
              <button
                type="button"
                className="p-1 rounded text-gray-400 hover:text-red-500 transition-colors"
                title="删除"
              >
                <DeleteOutlined className="text-sm" />
              </button>
            </Popconfirm>
          </div>
        ) : (
          <span className="flex items-center gap-1.5 text-gray-400 text-sm flex-shrink-0">
            <DownloadOutlined className="text-sm text-gray-400" />
            {downloadCount ?? 0}
          </span>
        )}
      </div>

      {/* 简介 */}
      <p className="text-sm line-clamp-3 leading-relaxed text-gray-500 flex-1">
        {description}
      </p>

      {/* 底部：标签 + 日期 */}
      <div className="mt-2 space-y-1.5">
        {(skillTags ?? []).length > 0 && (
          <div className="flex items-center gap-1 overflow-hidden">
            {(skillTags ?? []).slice(0, 3).map(tag => (
              <span
                key={tag}
                className="px-2 py-0.5 rounded-md text-[11px] font-medium bg-gray-50 text-gray-500 whitespace-nowrap border border-gray-100"
              >
                {tag}
              </span>
            ))}
          </div>
        )}

        <div className="flex items-center justify-between text-gray-400 text-xs">
          {developerUsername ? (
            <span className="flex items-center gap-1">
              <UserOutlined className="text-[10px]" />
              {developerUsername}
            </span>
          ) : <span />}
          <span className="tabular-nums tracking-tight">{releaseDate}</span>
        </div>
      </div>
    </div>
  );
}

