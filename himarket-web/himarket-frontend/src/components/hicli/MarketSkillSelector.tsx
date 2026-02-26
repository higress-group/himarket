import { useState, useEffect, useCallback, useMemo } from "react";
import { Alert, Spin, Button, Tag } from "antd";
import { RefreshCw } from "lucide-react";
import {
  getMarketSkills,
  downloadSkill,
  type MarketSkillInfo,
  type SkillEntry,
} from "../../lib/apis/cliProvider";
import { SelectableCard } from "../common/SelectableCard";
import { SearchFilterInput } from "../common/SearchFilterInput";
import { filterByKeyword } from "../../lib/utils/filterUtils";

// ============ 类型定义 ============

export interface MarketSkillSelectorProps {
  /** 选择 Skill 后回调，null 表示未选择 */
  onChange: (skills: SkillEntry[] | null) => void;
}

// 搜索过滤的阈值：列表超过此数量时显示搜索框
const SEARCH_THRESHOLD = 4;

// ============ 组件 ============

export function MarketSkillSelector({ onChange }: MarketSkillSelectorProps) {
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [skills, setSkills] = useState<MarketSkillInfo[]>([]);
  const [selectedIds, setSelectedIds] = useState<string[]>([]);
  const [downloadingIds, setDownloadingIds] = useState<Set<string>>(new Set());
  /** 缓存已下载的 SKILL.md 内容，避免重复下载 */
  const [downloadedContent, setDownloadedContent] = useState<
    Record<string, string>
  >({});
  const [searchKeyword, setSearchKeyword] = useState("");

  const fetchSkills = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const res = await getMarketSkills();
      const data = res.data;
      setSkills(Array.isArray(data) ? data : []);
    } catch (err: any) {
      setError(
        err instanceof Error ? err.message : "获取市场 Skill 列表失败"
      );
    } finally {
      setLoading(false);
    }
  }, []);

  // 组件挂载时获取数据
  useEffect(() => {
    setSelectedIds([]);
    setDownloadedContent({});
    onChange(null);
    fetchSkills();
  }, [fetchSkills]);

  // 根据关键词过滤 Skill 列表（按名称、描述和标签匹配）
  const filteredSkills = useMemo(
    () => filterByKeyword(skills, searchKeyword, ["name", "description", "skillTags"]),
    [skills, searchKeyword]
  );

  // 切换卡片选中状态，选中时下载 SKILL.md
  const handleToggle = useCallback(
    async (productId: string) => {
      const isSelected = selectedIds.includes(productId);
      const nextIds = isSelected
        ? selectedIds.filter((id) => id !== productId)
        : [...selectedIds, productId];

      setSelectedIds(nextIds);

      if (nextIds.length === 0) {
        onChange(null);
        return;
      }

      // 找出需要新下载的 Skill
      const toDownload = nextIds.filter((id) => !downloadedContent[id]);
      if (toDownload.length > 0) {
        setDownloadingIds((prev) => {
          const next = new Set(prev);
          toDownload.forEach((id) => next.add(id));
          return next;
        });

        const newContent: Record<string, string> = {};
        await Promise.all(
          toDownload.map(async (id) => {
            try {
              const res = await downloadSkill(id);
              const content = typeof res === "string" ? res : (res as any)?.data ?? "";
              newContent[id] = typeof content === "string" ? content : "";
            } catch {
              // 下载失败时跳过该 Skill
              newContent[id] = "";
            }
          })
        );

        setDownloadingIds((prev) => {
          const next = new Set(prev);
          toDownload.forEach((id) => next.delete(id));
          return next;
        });

        setDownloadedContent((prev) => {
          const merged = { ...prev, ...newContent };
          // 组装 SkillEntry 列表
          const entries: SkillEntry[] = nextIds
            .map((id) => {
              const skill = skills.find((s) => s.productId === id);
              if (!skill) return null;
              const md = merged[id] ?? prev[id];
              if (!md) return null;
              return { name: skill.name, skillMdContent: md };
            })
            .filter((e): e is SkillEntry => e !== null);
          onChange(entries.length > 0 ? entries : null);
          return merged;
        });
      } else {
        // 所有内容已缓存，直接组装
        const entries: SkillEntry[] = nextIds
          .map((id) => {
            const skill = skills.find((s) => s.productId === id);
            if (!skill) return null;
            return {
              name: skill.name,
              skillMdContent: downloadedContent[id] ?? "",
            };
          })
          .filter((e): e is SkillEntry => e !== null);
        onChange(entries.length > 0 ? entries : null);
      }
    },
    [skills, selectedIds, downloadedContent, onChange]
  );

  // 加载中
  if (loading) {
    return (
      <div className="flex justify-center py-4">
        <Spin size="small" />
      </div>
    );
  }

  // 错误状态
  if (error) {
    return (
      <div className="flex flex-col items-center gap-2 w-full">
        <Alert message={error} type="error" showIcon className="w-full" />
        <Button
          size="small"
          icon={<RefreshCw size={14} />}
          onClick={fetchSkills}
        >
          重试
        </Button>
      </div>
    );
  }

  // Skill 列表为空
  if (skills.length === 0) {
    return (
      <Alert
        message="暂无已发布的 Skill"
        type="info"
        showIcon
        className="w-full"
      />
    );
  }

  // Skill 列表非空，展示卡片网格
  return (
    <div className="flex flex-col gap-3 w-full">
      {/* 列表超过 4 项时显示搜索框 */}
      {skills.length > SEARCH_THRESHOLD && (
        <SearchFilterInput
          value={searchKeyword}
          onChange={setSearchKeyword}
          placeholder="搜索 Skill..."
        />
      )}

      {/* 过滤后无匹配结果 */}
      {filteredSkills.length === 0 ? (
        <div className="text-center text-sm text-gray-400 py-4">
          无匹配结果
        </div>
      ) : (
        /* 卡片网格布局 */
        <div className="grid grid-cols-2 gap-2 max-h-[280px] overflow-y-auto pr-1">
          {filteredSkills.map((skill) => (
            <SelectableCard
              key={skill.productId}
              selected={selectedIds.includes(skill.productId)}
              onClick={() => handleToggle(skill.productId)}
            >
              <div className="flex flex-col gap-1">
                <div className="flex items-center gap-2">
                  <span className="text-sm font-medium text-gray-800">
                    {skill.name}
                  </span>
                  {/* 下载中显示加载指示器 */}
                  {downloadingIds.has(skill.productId) && (
                    <Spin size="small" />
                  )}
                </div>
                {skill.description && (
                  <span className="text-xs text-gray-400 line-clamp-2">
                    {skill.description}
                  </span>
                )}
                {/* 展示 skillTags 标签 */}
                {skill.skillTags && skill.skillTags.length > 0 && (
                  <div className="flex flex-wrap gap-1 mt-1">
                    {skill.skillTags.map((tag) => (
                      <Tag
                        key={tag}
                        className="text-xs px-1.5 py-0 leading-5 m-0"
                        color="blue"
                      >
                        {tag}
                      </Tag>
                    ))}
                  </div>
                )}
              </div>
            </SelectableCard>
          ))}
        </div>
      )}
    </div>
  );
}
