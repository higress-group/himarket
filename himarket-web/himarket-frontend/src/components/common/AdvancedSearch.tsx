import { SearchOutlined } from '@ant-design/icons';
import { Select, Input, Button } from 'antd';
import React, { useState, useEffect } from 'react';

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
  // onClear,
  className = '',
  onSearch,
  searchParamsList,
}) => {
  const [activeSearchName, setActiveSearchName] = useState<string>(() => {
    // Initialize with first search param if available
    const first = searchParamsList[0];
    return first ? first.name : '';
  });
  const [activeSearchValue, setActiveSearchValue] = useState<string>('');
  // const [tagList, setTagList] = useState<Array<SearchParam & { value: string; displayValue?: string }>>([]);

  useEffect(() => {
    // Update active search name if it's empty and params are available
    if (searchParamsList.length > 0 && !activeSearchName) {
      const first = searchParamsList[0];
      if (first) {
        setActiveSearchName(first.name);
      }
    }
  }, [searchParamsList, activeSearchName]);

  const handleSearch = () => {
    if (activeSearchValue.trim()) {
      const currentParam = searchParamsList.find((item) => item.name === activeSearchName);
      if (currentParam) {
        // 获取显示值（对于select类型，显示label而不是value）
        // let displayValue = activeSearchValue;
        // if (currentParam.type === 'select') {
        // const option = currentParam.optionList?.find(opt => opt.value === activeSearchValue);
        // displayValue = option?.label || activeSearchValue;
        // }
        // const newTag = {
        //   ...currentParam,
        //   value: activeSearchValue,
        //   displayValue: displayValue
        // };
        // setTagList(prev => {
        //   const filtered = prev.filter(tag => tag.name !== activeSearchName);
        //   return [...filtered, newTag];
        // });
      }

      onSearch(activeSearchName, activeSearchValue);
      setActiveSearchValue('');
    }
  };

  const handleClearOne = (tagName: string) => {
    // setTagList(prev => prev.filter(tag => tag.name !== tagName));
    onSearch(tagName, '');
  };

  // const handleClearAll = () => {
  //   setTagList([]);
  //   if (onClear) {
  //     onClear();
  //   }
  // };

  // const handleSelectOne = (tagName: string) => {
  //   const tag = tagList.find(t => t.name === tagName);
  //   if (tag) {
  //     setActiveSearchName(tagName);
  //     setActiveSearchValue(tag.value);
  //   }
  // };

  const getCurrentParam = () => {
    return searchParamsList.find((item) => item.name === activeSearchName);
  };

  const currentParam = getCurrentParam();

  return (
    <div className={`flex flex-col gap-4 ${className}`}>
      {/* 搜索控件 */}
      <div className="flex max-w-sm border border-gray-300 rounded-md overflow-hidden hover:border-colorPrimary focus-within:border-colorPrimary focus-within:shadow-sm">
        {/* 左侧：搜索字段选择器 */}
        <Select
          className="h-8 border-0 rounded-none"
          onChange={setActiveSearchName}
          size="middle"
          style={{
            width: 100,
          }}
          value={activeSearchName}
          variant="borderless"
        >
          {searchParamsList.map((item) => (
            <Option key={item.name} value={item.name}>
              {item.label}
            </Option>
          ))}
        </Select>

        {/* 分隔线 */}
        <div className="w-px bg-gray-300 self-stretch"></div>

        {/* 中间：搜索值输入框 */}
        {currentParam?.type === 'select' ? (
          <>
            <Select
              allowClear
              className="h-8 border-0 rounded-none"
              onChange={(value) => {
                // 自动触发搜索
                if (value) {
                  const currentParam = searchParamsList.find(
                    (item) => item.name === activeSearchName,
                  );
                  if (currentParam) {
                    // const option = currentParam.optionList?.find(opt => opt.value === value);
                    // const displayValue = option?.label || value;

                    // const newTag = {
                    //   ...currentParam,
                    //   value: value,
                    //   displayValue: displayValue
                    // };

                    // setTagList(prev => {
                    //   const filtered = prev.filter(tag => tag.name !== activeSearchName);
                    //   return [...filtered, newTag];
                    // });

                    onSearch(activeSearchName, value);
                    // 清空选择框的值，避免显示原始值
                    setActiveSearchValue(value);
                  }
                }
              }}
              onClear={() => {
                setActiveSearchValue('');
                handleClearOne(activeSearchName);
              }}
              placeholder={currentParam.placeholder}
              size="middle"
              style={{ width: 200 }}
              value={activeSearchValue}
              variant="borderless"
            >
              {currentParam.optionList?.map((item) => (
                <Option key={item.value} value={item.value}>
                  {item.label}
                </Option>
              ))}
            </Select>

            {/* 分隔线 */}
            <div className="w-px bg-gray-300 self-stretch"></div>

            {/* 搜索按钮 */}
            <div
              className="h-8 flex items-center justify-center text-gray-500"
              style={{
                width: 48,
              }}
            >
              <SearchOutlined style={{ fontSize: '14px' }} />
            </div>
          </>
        ) : (
          <>
            <Input
              allowClear
              className="h-8 border-0 rounded-none"
              onChange={(e) => setActiveSearchValue(e.target.value)}
              onClear={() => setActiveSearchValue('')}
              onPressEnter={handleSearch}
              placeholder={currentParam?.placeholder}
              size="middle"
              style={{
                width: 200,
              }}
              value={activeSearchValue}
              variant="borderless"
            />

            {/* 分隔线 */}
            <div className="w-px bg-gray-300 self-stretch"></div>

            {/* 搜索按钮 */}
            <Button
              className="h-8 border-0 rounded-none text-gray-500"
              icon={<SearchOutlined style={{ color: '#6B7280', fontSize: '14px' }} />}
              onClick={handleSearch}
              size="middle"
              style={{
                width: 48,
              }}
              type="text"
            />
          </>
        )}
      </div>

      {/* 搜索标签 */}
      {/* {tagList.length > 0 && (
        <div className="flex items-center gap-2">
          <span className="text-sm text-gray-500">筛选条件：</span>
          <Space wrap>
            {tagList.map(tag => (
              <Tag
                key={tag.name}
                closable
                onClose={() => handleClearOne(tag.name)}
                onClick={() => handleSelectOne(tag.name)}
                className="cursor-pointer"
                style={{
                  backgroundColor: '#f5f5f5',
                  border: '1px solid #d9d9d9',
                  borderRadius: '16px',
                  color: '#666',
                  fontSize: '12px',
                  padding: '4px 12px',
                }}
              >
                {tag.label}：{tag.displayValue || tag.value}
              </Tag>
            ))}
          </Space>
          <Button
            type="link"
            size="small"
            onClick={handleClearAll}
            className="text-blue-500 hover:text-blue-600 text-sm"
          >
            清除筛选条件
          </Button>
        </div>
      )} */}
    </div>
  );
};
