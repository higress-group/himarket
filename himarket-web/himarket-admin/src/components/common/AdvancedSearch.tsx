import { SearchOutlined } from '@ant-design/icons';
import { Select, Input, Button, Tag, Space } from 'antd';
import React, { useState, useEffect } from 'react';
// import './AdvancedSearch.css';

const { Option } = Select;

export interface SearchParam {
  label: string;
  name: string;
  placeholder: string;
  type?: 'input' | 'select';
  optionList?: Array<{ label: string; value: string }>;
}

interface AdvancedSearchProps {
  searchParamsList: SearchParam[];
  onSearch: (searchName: string, searchValue: string) => void;
  onClear?: () => void;
  className?: string;
}

export const AdvancedSearch: React.FC<AdvancedSearchProps> = ({
  className = '',
  onClear,
  onSearch,
  searchParamsList,
}) => {
  const [activeSearchName, setActiveSearchName] = useState<string>('');
  const [activeSearchValue, setActiveSearchValue] = useState<string>('');
  const [tagList, setTagList] = useState<Array<SearchParam & { value: string }>>([]);
  const [isInitialized, setIsInitialized] = useState<boolean>(false);

  useEffect(() => {
    // 防止初始化时自动触发搜索

    if (isInitialized && activeSearchName) {
      setActiveSearchValue(''); // 清空输入框
      setTagList([]); // 清空关联标签
      onSearch(activeSearchName, '');
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [activeSearchName, isInitialized]); // 移除 onSearch 避免无限循环

  useEffect(() => {
    if (searchParamsList.length > 0) {
      const firstParam = searchParamsList[0];
      if (firstParam) {
        setActiveSearchName(firstParam.name);
      }
      setIsInitialized(true); // 标记为已初始化
    }
  }, [searchParamsList]);

  const handleSearch = () => {
    if (activeSearchValue.trim()) {
      // 添加到标签列表
      const currentParam = searchParamsList.find((item) => item.name === activeSearchName);
      if (currentParam) {
        const newTag = {
          ...currentParam,
          value: activeSearchValue,
        };
        setTagList((prev) => {
          const filtered = prev.filter((tag) => tag.name !== activeSearchName);
          return [...filtered, newTag];
        });
      }

      onSearch(activeSearchName, activeSearchValue);
      setActiveSearchValue('');
    }
  };

  const handleClearOne = (tagName: string) => {
    setTagList((prev) => prev.filter((tag) => tag.name !== tagName));
    onSearch(tagName, '');
  };

  const handleClearAll = () => {
    setTagList([]);
    if (onClear) {
      onClear();
    }
  };

  const handleSelectOne = (tagName: string) => {
    const tag = tagList.find((t) => t.name === tagName);
    if (tag) {
      setActiveSearchName(tagName);
      setActiveSearchValue(tag.value);
    }
  };

  const getCurrentParam = () => {
    return searchParamsList.find((item) => item.name === activeSearchName);
  };

  const currentParam = getCurrentParam();

  return (
    <div className={`flex flex-col gap-4 ${className}`}>
      {/* 搜索控件 */}
      <div className="flex items-center">
        {/* 左侧：搜索字段选择器 */}
        <Select
          className="h-10"
          onChange={setActiveSearchName}
          size="large"
          style={{
            borderBottomRightRadius: 0,
            borderRight: 'none',
            borderTopRightRadius: 0,
            width: 120,
          }}
          value={activeSearchName}
        >
          {searchParamsList.map((item) => (
            <Option key={item.name} value={item.name}>
              {item.label}
            </Option>
          ))}
        </Select>

        {/* 中间：搜索值输入框 */}
        {currentParam?.type === 'select' ? (
          <Select
            allowClear
            className="h-10"
            onChange={(value) => {
              setActiveSearchValue(value);
              // 自动触发搜索
              if (value) {
                onSearch(activeSearchName, value);
              }
            }}
            onClear={() => {
              setActiveSearchValue('');
              onClear?.();
            }}
            placeholder={currentParam.placeholder}
            size="large"
            style={{
              borderBottomLeftRadius: 0,
              borderTopLeftRadius: 0,
              width: 400,
            }}
            value={activeSearchValue}
          >
            {currentParam.optionList?.map((item) => (
              <Option key={item.value} value={item.value}>
                {item.label}
              </Option>
            ))}
          </Select>
        ) : (
          <Input
            allowClear
            className="h-10"
            onChange={(e) => setActiveSearchValue(e.target.value)}
            onClear={() => setActiveSearchValue('')}
            onPressEnter={handleSearch}
            placeholder={currentParam?.placeholder}
            size="large"
            style={{
              borderBottomLeftRadius: 0,
              borderTopLeftRadius: 0,
              width: 400,
            }}
            suffix={
              <Button
                className="h-8 w-8 flex items-center justify-center"
                icon={<SearchOutlined />}
                onClick={handleSearch}
                size="small"
                type="text"
              />
            }
            value={activeSearchValue}
          />
        )}
      </div>

      {/* 搜索标签 */}
      {tagList.length > 0 && (
        <div className="mt-4">
          <div className="flex items-center gap-2 mb-2">
            <span className="text-sm text-gray-500">已选择的筛选条件：</span>
            <Button
              className="text-gray-400 hover:text-gray-600"
              onClick={handleClearAll}
              size="small"
              type="link"
            >
              清除全部
            </Button>
          </div>
          <Space wrap>
            {tagList.map((tag) => (
              <Tag
                className="cursor-pointer"
                closable
                color={tag.name === activeSearchName ? 'blue' : 'default'}
                key={tag.name}
                onClick={() => handleSelectOne(tag.name)}
                onClose={() => handleClearOne(tag.name)}
              >
                {tag.label}：{tag.value}
              </Tag>
            ))}
          </Space>
        </div>
      )}
    </div>
  );
};
