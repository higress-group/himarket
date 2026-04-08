import { Input } from 'antd';
import { Search } from 'lucide-react';

import type React from 'react';

/**
 * 搜索过滤输入框 Props
 * 用于 MCP 和 Skill 列表的轻量搜索过滤
 */
export interface SearchFilterInputProps {
  /** 当前搜索值 */
  value: string;
  /** 值变化回调 */
  onChange: (value: string) => void;
  /** 占位文本 */
  placeholder?: string;
}

/**
 * 轻量搜索过滤输入框
 *
 * 带搜索图标前缀和清除按钮，用于列表的实时过滤。
 */
export const SearchFilterInput: React.FC<SearchFilterInputProps> = ({
  onChange,
  placeholder = '搜索...',
  value,
}) => {
  return (
    <Input
      allowClear
      onChange={(e) => onChange(e.target.value)}
      placeholder={placeholder}
      prefix={<Search className="text-gray-400" size={16} />}
      size="middle"
      value={value}
    />
  );
};
