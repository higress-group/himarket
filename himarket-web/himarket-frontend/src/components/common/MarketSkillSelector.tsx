import { Alert, Spin, Button, Tag } from 'antd';
import { RefreshCw } from 'lucide-react';
import { useState, useEffect, useCallback, useMemo } from 'react';

import { getMarketSkills, type MarketSkillInfo, type SkillEntry } from '../../lib/apis/cliProvider';
import { filterByKeyword } from '../../lib/utils/filterUtils';
import { SearchFilterInput } from '../common/SearchFilterInput';
import { SelectableCard } from '../common/SelectableCard';

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
  const [searchKeyword, setSearchKeyword] = useState('');

  const fetchSkills = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const res = await getMarketSkills();
      const data = res.data;
      setSkills(Array.isArray(data) ? data : []);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : '获取市场 Skill 列表失败');
    } finally {
      setLoading(false);
    }
  }, []);

  // 组件挂载时获取数据
  useEffect(() => {
    setSelectedIds([]);
    onChange(null);
    fetchSkills();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [fetchSkills]);

  // 根据关键词过滤 Skill 列表（按名称、描述和标签匹配）
  const filteredSkills = useMemo(
    () => filterByKeyword(skills, searchKeyword, ['name', 'description', 'skillTags']),
    [skills, searchKeyword],
  );

  // 切换卡片选中状态，仅传递 { productId, name }
  const handleToggle = useCallback(
    (productId: string) => {
      const isSelected = selectedIds.includes(productId);
      const nextIds = isSelected
        ? selectedIds.filter((id) => id !== productId)
        : [...selectedIds, productId];

      setSelectedIds(nextIds);

      if (nextIds.length === 0) {
        onChange(null);
        return;
      }

      const entries = nextIds
        .map((id): SkillEntry | null => {
          const skill = skills.find((s) => s.productId === id);
          if (!skill) return null;
          return { name: skill.name, productId: id };
        })
        .filter((e): e is SkillEntry => e !== null);
      onChange(entries.length > 0 ? entries : null);
    },
    [skills, selectedIds, onChange],
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
        <Alert className="w-full" message={error} showIcon type="error" />
        <Button icon={<RefreshCw size={14} />} onClick={fetchSkills} size="small">
          重试
        </Button>
      </div>
    );
  }

  // Skill 列表为空
  if (skills.length === 0) {
    return <Alert className="w-full" message="暂无已发布的 Skill" showIcon type="info" />;
  }

  // Skill 列表非空，展示卡片网格
  return (
    <div className="flex flex-col gap-3 w-full">
      {/* 列表超过 4 项时显示搜索框 */}
      {skills.length > SEARCH_THRESHOLD && (
        <SearchFilterInput
          onChange={setSearchKeyword}
          placeholder="搜索 Skill..."
          value={searchKeyword}
        />
      )}

      {/* 过滤后无匹配结果 */}
      {filteredSkills.length === 0 ? (
        <div className="text-center text-sm text-gray-400 py-4">无匹配结果</div>
      ) : (
        /* 卡片网格布局 */
        <div className="grid grid-cols-2 gap-2 max-h-[280px] overflow-y-auto pr-1">
          {filteredSkills.map((skill) => (
            <SelectableCard
              key={skill.productId}
              onClick={() => handleToggle(skill.productId)}
              selected={selectedIds.includes(skill.productId)}
            >
              <div className="flex flex-col gap-1">
                <div className="flex items-center gap-2">
                  <span className="text-sm font-medium text-gray-800">{skill.name}</span>
                </div>
                {skill.description && (
                  <span className="text-xs text-gray-400 line-clamp-2">{skill.description}</span>
                )}
                {/* 展示 skillTags 标签 */}
                {skill.skillTags && skill.skillTags.length > 0 && (
                  <div className="flex flex-wrap gap-1 mt-1">
                    {skill.skillTags.map((tag) => (
                      <Tag className="text-xs px-1.5 py-0 leading-5 m-0" color="blue" key={tag}>
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
