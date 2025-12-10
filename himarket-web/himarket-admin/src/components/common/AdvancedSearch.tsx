import React, { useState, useEffect } from 'react';
import { Select, Input, Button, Tag, Space } from 'antd';
import { SearchOutlined, CloseOutlined } from '@ant-design/icons';
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
  searchParamsList,
  onSearch,
  onClear,
  className = ''
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
  }, [activeSearchName, isInitialized]); // 移除 onSearch 避免无限循环

  useEffect(() => {
    if (searchParamsList.length > 0) {
      setActiveSearchName(searchParamsList[0].name);
      setIsInitialized(true); // 标记为已初始化
    }
  }, [searchParamsList]);

  const handleSearch = () => {
    if (activeSearchValue.trim()) {
      // 添加到标签列表
      const currentParam = searchParamsList.find(item => item.name === activeSearchName);
      if (currentParam) {
        const newTag = {
          ...currentParam,
          value: activeSearchValue
        };
        setTagList(prev => {
          const filtered = prev.filter(tag => tag.name !== activeSearchName);
          return [...filtered, newTag];
        });
      }
      
      onSearch(activeSearchName, activeSearchValue);
      setActiveSearchValue('');
    }
  };

  const handleClearOne = (tagName: string) => {
    setTagList(prev => prev.filter(tag => tag.name !== tagName));
    onSearch(tagName, '');
  };

  const handleClearAll = () => {
    setTagList([]);
    if (onClear) {
      onClear();
    }
  };

  const handleSelectOne = (tagName: string) => {
    const tag = tagList.find(t => t.name === tagName);
    if (tag) {
      setActiveSearchName(tagName);
      setActiveSearchValue(tag.value);
    }
  };

  const getCurrentParam = () => {
    return searchParamsList.find(item => item.name === activeSearchName);
  };

  const currentParam = getCurrentParam();

  return (
    <div className={`flex flex-col gap-4 ${className}`}>
      {/* 搜索控件 */}
      <div className="flex items-center">
        {/* 左侧：搜索字段选择器 */}
        <Select
          value={activeSearchName}
          onChange={setActiveSearchName}
          style={{ 
            width: 120,
            borderTopRightRadius: 0,
            borderBottomRightRadius: 0,
            borderRight: 'none'
          }}
          className="h-10"
          size="large"
        >
          {searchParamsList.map(item => (
            <Option key={item.name} value={item.name}>
              {item.label}
            </Option>
          ))}
        </Select>

        {/* 中间：搜索值输入框 */}
        {currentParam?.type === 'select' ? (
          <Select
            placeholder={currentParam.placeholder}
            value={activeSearchValue}
            onChange={(value) => {
              setActiveSearchValue(value);
              // 自动触发搜索
              if (value) {
                onSearch(activeSearchName, value);
              }
            }}
            style={{ 
              width: 400,
              borderTopLeftRadius: 0,
              borderBottomLeftRadius: 0
            }}
            allowClear
            onClear={() => {
              setActiveSearchValue('');
              onClear?.();
            }}
            className="h-10"
            size="large"
          >
            {currentParam.optionList?.map(item => (
              <Option key={item.value} value={item.value}>
                {item.label}
              </Option>
            ))}
          </Select>
        ) : (
          <Input
            placeholder={currentParam?.placeholder}
            value={activeSearchValue}
            onChange={(e) => setActiveSearchValue(e.target.value)}
            style={{ 
              width: 400,
              borderTopLeftRadius: 0,
              borderBottomLeftRadius: 0
            }}
            onPressEnter={handleSearch}
            allowClear
            onClear={() => setActiveSearchValue('')}
            size="large"
            className="h-10"
            suffix={
              <Button
                type="text"
                icon={<SearchOutlined />}
                onClick={handleSearch}
                size="small"
                className="h-8 w-8 flex items-center justify-center"
              />
            }
          />
        )}
      </div>

      {/* 搜索标签 */}
      {tagList.length > 0 && (
        <div className="mt-4">
          <div className="flex items-center gap-2 mb-2">
            <span className="text-sm text-gray-500">已选择的筛选条件：</span>
            <Button
              type="link"
              size="small"
              onClick={handleClearAll}
              className="text-gray-400 hover:text-gray-600"
            >
              清除全部
            </Button>
          </div>
          <Space wrap>
            {tagList.map(tag => (
              <Tag
                key={tag.name}
                closable
                onClose={() => handleClearOne(tag.name)}
                onClick={() => handleSelectOne(tag.name)}
                className="cursor-pointer"
                color={tag.name === activeSearchName ? 'blue' : 'default'}
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
